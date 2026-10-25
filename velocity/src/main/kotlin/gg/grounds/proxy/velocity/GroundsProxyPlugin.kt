package gg.grounds.proxy.velocity

import com.google.gson.JsonParser
import com.google.inject.Inject
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.scheduler.ScheduledTask
import gg.grounds.BuildInfo
import gg.grounds.i18n.Translations
import gg.grounds.presentation.VelocitySystemMessages
import gg.grounds.proxy.api.NetworkPlayerCounts
import gg.grounds.proxy.api.PlayerDisplayQuery
import gg.grounds.proxy.api.PlayerMessageRenderers
import gg.grounds.proxy.api.PlayerRoleQuery
import gg.grounds.proxy.api.PlayerSessionTransfer
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import gg.grounds.proxy.api.ServerDisplayQuery
import gg.grounds.proxy.velocity.command.MotdCommand
import gg.grounds.proxy.velocity.command.OnlineCommand
import gg.grounds.proxy.velocity.command.ProxyCommand
import gg.grounds.proxy.velocity.command.ProxyDebugCommand
import gg.grounds.proxy.velocity.command.RegionCommand
import gg.grounds.proxy.velocity.handler.NatsHandler
import gg.grounds.proxy.velocity.listener.LobbyCountsListener
import gg.grounds.proxy.velocity.listener.ProxyHopListener
import gg.grounds.proxy.velocity.maintenance.MaintenanceAccess
import gg.grounds.proxy.velocity.maintenance.MaintenanceConfigStore
import gg.grounds.proxy.velocity.maintenance.MaintenanceEngine
import gg.grounds.proxy.velocity.metrics.MetricsConfig
import gg.grounds.proxy.velocity.metrics.ProxyMetrics
import gg.grounds.proxy.velocity.metrics.ProxySnapshot
import gg.grounds.proxy.velocity.motd.MotdConfigStore
import gg.grounds.proxy.velocity.motd.MotdGgClient
import gg.grounds.proxy.velocity.motd.MotdManager
import gg.grounds.proxy.velocity.tab.BedrockPlayers
import gg.grounds.proxy.velocity.tab.BedrockRoster
import gg.grounds.proxy.velocity.tab.PlayerDisplayQueryImpl
import gg.grounds.proxy.velocity.tab.TabList
import gg.grounds.proxy.velocity.tab.TabParties
import io.nats.client.Subscription
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.slf4j.Logger

/**
 * Where service-player publishes the network-wide player count. Not scoped per environment or
 * region: every proxy on this NATS wants the same number, and the leaf topology already decides who
 * can hear it.
 */
private const val PLAYER_COUNTS_SUBJECT = "proxy.player-counts"

/**
 * How often the tab list is redrawn.
 *
 * Slow enough that a few hundred players cost nothing, fast enough that a ping which has just gone
 * bad shows up while the player is still wondering why.
 */
private const val TAB_REFRESH_SECONDS = 5L

/** service-social answers from one query; past this the tab simply stays without the party. */
private val PARTY_LOOKUP_TIMEOUT: Duration = Duration.ofSeconds(2)

/**
 * Which service-config application the MOTD document belongs to.
 *
 * Every proxy in every region reads and writes the same one, which is the point: the MOTD is a
 * property of the network. It is deliberately not the release name — `velocity` and `velocity-2`
 * are two deployments of one thing, and they must not end up with a MOTD each.
 */
private const val DEFAULT_CONFIG_APP = "velocity"

/**
 * Reads `{"total":N}`.
 *
 * One integer out of one flat object does not justify a JSON dependency in a plugin shaded into
 * every proxy, and a reader that returns null on anything it does not recognise is easier to reason
 * about here than a permissive parser. Null means "keep the previous value", which is the same
 * thing a missed broadcast means.
 */
internal fun parsePlayerCount(payload: String): Int? {
    val marker = "\"total\""
    val at = payload.indexOf(marker)
    if (at < 0) return null
    val colon = payload.indexOf(':', at + marker.length)
    if (colon < 0) return null
    val digits = payload.drop(colon + 1).trimStart().takeWhile { it.isDigit() }
    return digits.toIntOrNull()?.takeIf { it >= 0 }
}

/**
 * Reads the optional `"servers":{"<server>":n,..}` of a player-count broadcast.
 *
 * Null when the broadcast has none (an older service-player, or one with per-server counts off) or
 * when it is not well formed — the caller then keeps asking the network, as before. A server whose
 * count is not a non-negative integer makes the whole object unusable rather than silently absent.
 */
internal fun parseServerCounts(payload: String): Map<String, Int>? {
    val root = runCatching { JsonParser.parseString(payload) }.getOrNull() ?: return null
    if (!root.isJsonObject) return null
    val servers = root.asJsonObject.get("servers") ?: return null
    if (!servers.isJsonObject) return null
    val counts = LinkedHashMap<String, Int>()
    for ((server, value) in servers.asJsonObject.entrySet()) {
        val count =
            value
                .takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asString
                ?.toIntOrNull()
                ?.takeIf { it >= 0 } ?: return null
        counts[server] = count
    }
    return counts
}

/** A broadcast's per-server counts while it is fresh; null when stale or when it carried none. */
internal fun broadcastServerCounts(
    total: Int,
    servers: Map<String, Int>?,
    receivedAtNanos: Long,
    nowNanos: Long,
): NetworkPlayerCounts? =
    servers
        ?.takeIf { isPlayerCountFresh(receivedAtNanos, nowNanos) }
        ?.let { NetworkPlayerCounts(it, total) }

/**
 * How long a cached player count is trusted before it is treated as absent.
 *
 * Six missed broadcasts (service-player publishes every 5s): long enough that ordinary network
 * jitter never trips it, short enough that a subscription going quiet — the NATS connection staying
 * healthy while its callback simply stops delivering, observed in production 2026-09-26 with
 * nothing logged anywhere — self-heals in well under a minute instead of showing a wrong number
 * indefinitely.
 */
private val PLAYER_COUNT_STALE_AFTER: Duration = Duration.ofSeconds(30)

/** [System.nanoTime] rather than a wall clock: monotonic, so it can't be fooled by a clock step. */
internal fun isPlayerCountFresh(receivedAtNanos: Long, nowNanos: Long): Boolean =
    nowNanos - receivedAtNanos <= PLAYER_COUNT_STALE_AFTER.toNanos()

/**
 * Resolves service-config through the platform service contract while keeping old deployments
 * working until every bundle has migrated from the legacy gRPC-specific variable.
 */
internal fun resolveConfigServiceTarget(
    environment: (String) -> String? = System::getenv
): String? =
    sequenceOf("CONFIG_SERVICE_URL", "CONFIG_GRPC_TARGET")
        .mapNotNull { name -> environment(name)?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()

@Plugin(id = "plugin-proxy", name = "GroundsProxyPlugin", version = BuildInfo.VERSION)
class GroundsProxyPlugin
@Inject
constructor(private val proxy: ProxyServer, private val logger: Logger) {
    private val messages =
        Translations.forBundleWithSystemRenderer(
            "gg.grounds.proxy.messages",
            javaClass.classLoader,
            systemRenderer = VelocitySystemMessages.renderer,
        )
    private lateinit var natsHandler: NatsHandler
    private val crossProxySubscriptions = mutableListOf<Subscription>()

    /**
     * The last network-wide player count service-player published, and when it arrived. [servers]
     * is null when that broadcast carried no per-server counts.
     */
    private data class CachedPlayerCount(
        val total: Int,
        val receivedAtNanos: Long,
        val servers: Map<String, Int>? = null,
    )

    @Volatile private var cachedPlayerCount: CachedPlayerCount? = null

    /**
     * [cachedPlayerCount], or null when there has not been one yet or the most recent one is older
     * than [PLAYER_COUNT_STALE_AFTER].
     *
     * Null is not zero, and every reader treats it that way: before the first broadcast, or once
     * one has gone stale, we let Velocity report its own count rather than claim an empty network.
     */
    private val networkPlayerCount: Int?
        get() =
            cachedPlayerCount
                ?.takeIf { isPlayerCountFresh(it.receivedAtNanos, System.nanoTime()) }
                ?.total

    /** The per-server counts of a fresh broadcast that carried them, as [NetworkPlayerCounts]. */
    private val broadcastServerCounts: NetworkPlayerCounts?
        get() =
            cachedPlayerCount?.let {
                broadcastServerCounts(it.total, it.servers, it.receivedAtNanos, System.nanoTime())
            }

    private var countSubscription: Subscription? = null
    private var metrics: ProxyMetrics? = null
    private var tabList: TabList? = null
    private var bedrockRoster: BedrockRoster? = null
    private val tabParties = TabParties()
    private var partySubject: String? = null

    /**
     * The network-wide MOTD, or null when this proxy has no service-config to read it from. Null
     * disables `/motd` entirely rather than offering a command that cannot store anything.
     */
    @Volatile private var motdManager: MotdManager? = null
    private var motdStore: MotdConfigStore? = null
    @Volatile private var maintenanceStore: MaintenanceConfigStore? = null
    private val maintenance = MaintenanceEngine()
    private val maintenanceRefreshRunning = AtomicBoolean()
    private val maintenanceClosed = AtomicBoolean()
    private var maintenanceRefreshTask: ScheduledTask? = null
    private var maintenanceApplyTask: ScheduledTask? = null

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        val natsUrl = System.getenv("NATS_URL") ?: "nats://nats.infra:4222"

        natsHandler = NatsHandler(natsUrl, logger)
        natsHandler.connect()

        val countsFromNats = env("PROXY_COUNTS_FROM_NATS").toBoolean()
        val proxyServiceImpl =
            ProxyServiceImpl(
                natsHandler,
                proxy,
                broadcastCounts = { broadcastServerCounts },
                countsFromNats = countsFromNats,
            )
        ProxyServiceRegistry.register(ProxyService::class.java, proxyServiceImpl)

        val catalog = RegionCatalog.fromEnvironment()
        catalog.problems.forEach { logger.warn("Ignoring REGIONS entry: {}", it) }
        if (catalog.regions.isEmpty()) {
            logger.info("REGIONS is unset or empty; /region will have nothing to offer")
        } else {
            logger.info("Regions available: {}", catalog.codes.joinToString(", "))
        }

        // Looked up per invocation rather than captured: the registry is written during startup and
        // a reference taken now could be the one from before another plugin registered.
        val service = { ProxyServiceRegistry.get(ProxyService::class.java) }
        // The same prefix plugin-lobby uses to tell lobbies from other backends.
        val lobbyServerPrefix =
            System.getenv("GROUNDS_LOBBY_SERVER_PREFIX")?.trim()?.takeIf(String::isNotEmpty)
                ?: "lobby"
        val region = { RegionCatalog.currentCode() }
        proxy.commandManager.register("online", OnlineCommand(service, messages))
        proxy.commandManager.register(
            "region",
            RegionCommand(
                catalog,
                region,
                service,
                messages,
                isOnLobby = { player ->
                    player.currentServer
                        .map { it.serverInfo.name.startsWith(lobbyServerPrefix, ignoreCase = true) }
                        .orElse(false)
                },
            ),
        )

        // How many players each server holds, answered to a backend that asks. Registered here
        // rather than in the lobby's own plugin because the number only exists on this side:
        // Velocity's own count is per proxy, and ProxyService is what makes it network-wide.
        val lobbyIdentifier = MinecraftChannelIdentifier.from(LobbyCountsListener.CHANNEL_ID)
        proxy.channelRegistrar.register(lobbyIdentifier)
        proxy.eventManager.register(this, LobbyCountsListener(lobbyIdentifier, service, logger))

        // The network-wide player count, pushed every few seconds. Subscribed once for the whole
        // proxy rather than per player: it is a property of the network, not of anybody's session.
        countSubscription =
            natsHandler.subscribe(PLAYER_COUNTS_SUBJECT) { payload ->
                val parsed = parsePlayerCount(payload)
                if (parsed == null) {
                    logger.warn("Ignoring malformed player-count broadcast: {}", payload)
                } else {
                    cachedPlayerCount =
                        CachedPlayerCount(parsed, System.nanoTime(), parseServerCounts(payload))
                }
            }

        startMotd()
        startMaintenance()

        subscribeForProxy()

        val bedrockPlayers = BedrockPlayers.of(logger)
        val platformSubject = BedrockRoster.subject(System.getenv("GROUNDS_ENVIRONMENT"))
        val roster =
            BedrockRoster(
                System.getenv("PROXY_ID").orEmpty(),
                { proxy.allPlayers.map { it.uniqueId } },
                bedrockPlayers::isBedrock,
                { payload -> platformSubject?.let { natsHandler.publish(it, payload) } },
            )
        bedrockRoster = roster
        ProxyServiceRegistry.register(
            PlayerDisplayQuery::class.java,
            PlayerDisplayQueryImpl(proxy, roster),
        )
        if (platformSubject != null) {
            crossProxySubscriptions += natsHandler.subscribe(platformSubject, roster::receive)
        } else {
            logger.warn(
                "GROUNDS_ENVIRONMENT is unset or invalid; Bedrock indicators are local-only"
            )
        }
        roster.refresh()

        // Looked up per call, not captured: plugin-permissions may register after this runs, and a
        // reference taken now would stay null for the life of the proxy.
        val tab =
            TabList(
                proxy,
                messages,
                roleQuery = { ProxyServiceRegistry.get(PlayerRoleQuery::class.java) },
                serverQuery = { ProxyServiceRegistry.get(ServerDisplayQuery::class.java) },
                isBedrock = roster::isBedrock,
                partyOf = tabParties::partyOf,
            )
        tabList = tab
        partySubject = TabParties.subject(System.getenv("GROUNDS_ENVIRONMENT"))
        partySubject?.let { subject ->
            crossProxySubscriptions +=
                natsHandler.subscribe(subject) { if (tabParties.receive(it)) tab.refreshAll() }
        }
        proxy.commandManager.register("proxydebug", ProxyDebugCommand(messages))

        val proxyId = System.getenv("PROXY_ID").orEmpty()
        val hops =
            ProxyHopListener(
                this,
                proxy,
                proxyId,
                address = { region()?.let { catalog[it] } },
                messages,
                logger,
            )
        proxy.eventManager.register(this, hops)
        proxy.commandManager.register(
            "proxy",
            ProxyCommand(proxyId, service, hops, messages, roster::remoteProxies),
        )

        // Roster entries and remote rank snapshots can change between join events.
        proxy.scheduler
            .buildTask(
                this,
                Runnable {
                    roster.refresh()
                    tab.refreshAll()
                },
            )
            .delay(Duration.ofSeconds(TAB_REFRESH_SECONDS))
            .repeat(Duration.ofSeconds(TAB_REFRESH_SECONDS))
            .schedule()

        startMetrics()

        logger.info("plugin-proxy enabled (nats={}, countsFromNats={})", natsUrl, countsFromNats)
    }

    /**
     * Brings up the network-wide MOTD, if this deployment has a service-config to keep it in.
     *
     * Without a config service contract the whole feature stays off and Velocity's own MOTD is
     * served — the same thing that happened before there was a `/motd`. That is the right shape for
     * a local run, where there is no config service to talk to.
     */
    private fun startMotd() {
        val target = resolveConfigServiceTarget()
        if (target == null) {
            logger.info("MOTD disabled (reason=config_service_url_unset)")
            return
        }
        val app = env("CONFIG_APP") ?: DEFAULT_CONFIG_APP
        // The environment the permission grants already name, so a deployment that has decided
        // what it is called does not have to say it twice.
        val configEnv = env("CONFIG_ENV") ?: env("GROUNDS_PERMISSION_ENVIRONMENT")
        if (configEnv == null) {
            logger.warn(
                "MOTD disabled (reason=CONFIG_ENV_and_GROUNDS_PERMISSION_ENVIRONMENT_unset)"
            )
            return
        }

        val store = MotdConfigStore.open(app, configEnv, target)
        val manager =
            MotdManager(
                store = store,
                region = env("REGION"),
                continent = env("CONTINENT"),
                logger = logger,
            )
        motdStore = store
        motdManager = manager

        val refreshSeconds = env("MOTD_REFRESH_SECONDS")?.toLongOrNull()?.takeIf { it > 0 } ?: 15L
        proxy.scheduler
            .buildTask(this, Runnable { manager.refresh() })
            .repeat(refreshSeconds, TimeUnit.SECONDS)
            .schedule()

        proxy.commandManager.register(
            "motd",
            MotdCommand(
                manager = manager,
                motdGg = MotdGgClient("plugin-proxy/${BuildInfo.VERSION} (+https://grounds.gg)"),
                async = { task -> proxy.scheduler.buildTask(this, task).schedule() },
                messages = messages,
                counts = {
                    MotdCommand.Counts(
                        players = networkPlayerCount ?: proxy.playerCount,
                        maxPlayers = manager.maxPlayers() ?: proxy.configuration.showMaxPlayers,
                    )
                },
            ),
        )

        logger.info(
            "MOTD enabled (app={}, env={}, target={}, refreshSeconds={})",
            app,
            configEnv,
            target,
            refreshSeconds,
        )
    }

    /** Starts the shared maintenance reader. Every proxy polls and only acts on its own players. */
    private fun startMaintenance() {
        val target = resolveConfigServiceTarget() ?: return
        val app = env("CONFIG_APP") ?: DEFAULT_CONFIG_APP
        val configEnv = env("CONFIG_ENV") ?: env("GROUNDS_PERMISSION_ENVIRONMENT") ?: return
        maintenanceStore = MaintenanceConfigStore.open(app, configEnv, target)
        // Hints shorten propagation after Portal saves; polling remains the correctness fallback.
        crossProxySubscriptions +=
            natsHandler.subscribe("config.$app.$configEnv.changed") {
                if (!maintenanceClosed.get()) {
                    proxy.scheduler
                        .buildTask(this, Runnable { refreshMaintenanceConfiguration() })
                        .schedule()
                }
            }
        maintenanceRefreshTask =
            proxy.scheduler
                .buildTask(this, Runnable { refreshMaintenanceConfiguration() })
                .repeat(5, TimeUnit.SECONDS)
                .schedule()
        // The local schedule still advances while Config is unavailable: end times and countdowns
        // are decisions from the cached signed document, not from a successful request this tick.
        maintenanceApplyTask =
            proxy.scheduler
                .buildTask(this, Runnable { applyMaintenance() })
                .repeat(1, TimeUnit.SECONDS)
                .schedule()
    }

    private fun refreshMaintenanceConfiguration() {
        if (maintenanceClosed.get()) return
        val store = maintenanceStore ?: return
        if (!maintenanceRefreshRunning.compareAndSet(false, true)) return
        try {
            val document = store.read()
            if (!maintenanceClosed.get()) maintenance.adopt(document)
        } catch (failure: Exception) {
            maintenance.refreshFailed()
            logger.warn(
                "Maintenance refresh failed (reason={}); retaining last valid state",
                failure.message,
            )
        } finally {
            maintenanceRefreshRunning.set(false)
        }
    }

    private fun applyMaintenance() {
        if (maintenanceClosed.get()) return
        val now = java.time.Instant.now()
        maintenance.announcementsAt(now).forEach {
            maintenance.announcementMessage(now)?.let { message ->
                val component = MiniMessage.miniMessage().deserialize(message)
                proxy.allPlayers.forEach { player -> player.sendMessage(component) }
            }
        }
        proxy.allPlayers.forEach { player ->
            if (
                MaintenanceAccess.shouldKick(
                    maintenance,
                    player.uniqueId,
                    { player.hasPermission(MaintenanceAccess.BYPASS_PERMISSION) },
                    now,
                )
            ) {
                player.disconnect(
                    MiniMessage.miniMessage().deserialize(maintenance.denialMessage(now))
                )
            }
        }
    }

    private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The player count in the server list, which Velocity would otherwise fill with the players on
     * *this* proxy — half the network once there are two of them, and plausible-looking while
     * wrong.
     *
     * Answered from what is already in memory, never by asking anything: a ping arrives whenever
     * anyone opens their server list, so this path has to be free. The count comes from the cached
     * broadcast and the MOTD from the cached document, both refreshed elsewhere.
     *
     * The icon is left alone. On a per-project deployment plugin-grounds-platform subscribes to
     * this same event and sets the icon and its own project MOTD — Velocity hands each subscriber
     * the result of the last, so whichever runs second wins the description. The two do not meet on
     * the player-facing network, where that plugin is not installed, and where a project name would
     * be the wrong thing to show anyway.
     */
    private val serverListPingListener by lazy {
        gg.grounds.proxy.velocity.motd.ServerListPingListener(
            manager = { motdManager },
            networkPlayers = { networkPlayerCount },
            maintenance = { if (maintenanceStore != null) maintenance else null },
            region = { RegionCatalog.currentCode() },
            continent = { env("CONTINENT") },
        )
    }

    @Subscribe fun onProxyPing(event: ProxyPingEvent) = serverListPingListener.onProxyPing(event)

    @Subscribe(order = PostOrder.LAST)
    fun onLogin(event: LoginEvent) {
        if (!event.result.isAllowed) return
        if (maintenanceStore == null) return
        val player = event.player
        val admission =
            MaintenanceAccess.admission(
                maintenance,
                player.uniqueId,
                { player.hasPermission(MaintenanceAccess.BYPASS_PERMISSION) },
                java.time.Instant.now(),
            )
        if (!admission.allowed) {
            event.result =
                ResultedEvent.ComponentResult.denied(
                    MiniMessage.miniMessage().deserialize(admission.message!!)
                )
        }
    }

    @Subscribe
    fun onLogin(event: PostLoginEvent) {
        bedrockRoster?.refresh()
        tabList?.refresh(event.player)
        lookUpParty(event.player.uniqueId)
    }

    /** Party events only cover changes, so a player who just came online asks for their party. */
    private fun lookUpParty(player: UUID) {
        val subject = partySubject ?: return
        proxy.scheduler
            .buildTask(
                this,
                Runnable {
                    val reply =
                        natsHandler.request("$subject.of", player.toString(), PARTY_LOOKUP_TIMEOUT)
                    if (reply != null && tabParties.receive(reply, player)) tabList?.refreshAll()
                },
            )
            .schedule()
    }

    /**
     * A backend server sends its own header and footer on connect, which replaces ours. Redrawing
     * after every switch is what keeps the network's identity on screen instead of the lobby's.
     */
    @Subscribe
    fun onServerConnected(event: ServerPostConnectEvent) {
        tabList?.refresh(event.player)
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        bedrockRoster?.close()
        ProxyServiceRegistry.unregister(ProxyService::class.java)
        ProxyServiceRegistry.unregister(PlayerDisplayQuery::class.java)
        countSubscription?.let { natsHandler.unsubscribe(it) }
        crossProxySubscriptions.forEach { natsHandler.unsubscribe(it) }
        crossProxySubscriptions.clear()
        motdStore?.close()
        maintenanceClosed.set(true)
        maintenanceRefreshTask?.cancel()
        maintenanceApplyTask?.cancel()
        maintenanceStore?.close()
        maintenanceStore = null
        metrics?.let { runCatching { it.close() } }
        metrics = null
        if (this::natsHandler.isInitialized) {
            natsHandler.close()
        }
    }

    /**
     * Bring up the Prometheus endpoint, if this proxy was asked for one.
     *
     * A failure here does **not** stop the proxy: a proxy that cannot publish metrics is degraded,
     * one that refuses to start because a port was taken is an outage that takes every player in
     * the region with it. The absence alerts by itself, as a missing `up`.
     */
    private fun startMetrics() {
        val config =
            runCatching { MetricsConfig.fromEnvironment() }
                .onFailure { failure -> logger.error("Ignoring metrics configuration", failure) }
                .getOrNull() ?: return
        if (!config.enabled) {
            logger.info("Metrics endpoint disabled (set GROUNDS_METRICS_ENABLED=true to publish)")
            return
        }

        val snapshot =
            object : ProxySnapshot {
                override fun playersOnline(): Int = proxy.playerCount

                override fun serversRegistered(): Int = proxy.allServers.size

                override fun networkPlayers(): Int? = networkPlayerCount
            }

        metrics =
            runCatching { ProxyMetrics.start(config, snapshot, logger) }
                .onFailure { failure ->
                    logger.error(
                        "Failed to start the metrics endpoint on {}:{} — continuing without it",
                        config.host,
                        config.port,
                        failure,
                    )
                }
                .getOrNull()
    }

    /**
     * The three cross-proxy subjects, taken once for this proxy rather than once per player.
     *
     * Keying by player meant the interest for every online player crossed the leafnode to the hub
     * on each join and each leave, and a restart re-subscribed the whole roster in one burst. This
     * is three subscriptions for the life of the process, whatever the player count does.
     */
    private fun subscribeForProxy() {
        val proxyId = System.getenv("PROXY_ID")?.trim().orEmpty()
        if (proxyId.isEmpty()) {
            logger.error(
                "PROXY_ID is unset; this proxy cannot be addressed by the others and will receive no cross-proxy messages"
            )
            return
        }

        crossProxySubscriptions +=
            natsHandler.subscribe(
                CrossProxyMessage.subjectFor(CrossProxyMessage.SYSTEM, proxyId)
            ) { raw ->
                forTarget(raw, CrossProxyMessage.SYSTEM) { player, payload ->
                    val message = GsonComponentSerializer.gson().deserialize(payload)
                    player.sendMessage(
                        PlayerMessageRenderers.renderOrOriginal(player.uniqueId, message)
                    )
                }
            }

        crossProxySubscriptions +=
            natsHandler.subscribe(
                CrossProxyMessage.subjectFor(CrossProxyMessage.TRANSFER, proxyId)
            ) { raw ->
                forTarget(raw, CrossProxyMessage.TRANSFER) { player, serverName ->
                    val server = proxy.getServer(serverName).orElse(null)
                    if (server == null) {
                        logger.warn(
                            "Transfer target server '{}' not found for {}",
                            serverName,
                            player.uniqueId,
                        )
                        return@forTarget
                    }
                    messages.send(
                        player,
                        ProxySystemMessage.PARTY_TRANSFER_FOLLOWING,
                        "server" to serverName,
                    )
                    player.createConnectionRequest(server).fireAndForget()
                }
            }

        // A transfer to a different *proxy*, published by whichever proxy ran the command. The
        // player is on this one, so this is where the packet has to be sent from.
        crossProxySubscriptions +=
            natsHandler.subscribe(
                CrossProxyMessage.subjectFor(CrossProxyMessage.HOST_TRANSFER, proxyId)
            ) { raw ->
                forTarget(raw, CrossProxyMessage.HOST_TRANSFER) { player, target ->
                    val host = target.substringBeforeLast(':', target)
                    val port = target.substringAfterLast(':', "").toIntOrNull()
                    if (host.isBlank() || port == null) {
                        logger.warn(
                            "Ignoring malformed host-transfer target '{}' for {}",
                            target,
                            player.uniqueId,
                        )
                        return@forTarget
                    }
                    if (
                        ProxyServiceRegistry.get(PlayerSessionTransfer::class.java)
                            ?.transferToHost(player.uniqueId, host, port) != true
                    ) {
                        logger.warn(
                            "Host transfer could not issue a session handoff (player={})",
                            player.uniqueId,
                        )
                    }
                }
            }

        logger.info("Subscribed to cross-proxy messages (proxyId={})", proxyId)
    }

    /**
     * Runs [action] for the addressed player, if they are still here.
     *
     * A miss is normal rather than an error: the publisher resolved this proxy from the session
     * table a moment ago, and the player may have left in between. That race is the price of
     * addressing a proxy instead of a player, and quietly dropping is what the per-player subject
     * did too once the subscription was gone.
     */
    private inline fun forTarget(raw: String, subject: String, action: (Player, String) -> Unit) {
        val decoded = CrossProxyMessage.decode(raw)
        if (decoded == null) {
            logger.warn("Ignoring malformed cross-proxy message on {}", subject)
            return
        }
        val (targetId, payload) = decoded
        val player = proxy.getPlayer(targetId).orElse(null) ?: return
        action(player, payload)
    }
}
