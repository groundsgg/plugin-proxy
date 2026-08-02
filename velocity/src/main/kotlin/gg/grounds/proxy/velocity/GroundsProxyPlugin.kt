package gg.grounds.proxy.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.BuildInfo
import gg.grounds.i18n.LocaleResolver
import gg.grounds.i18n.Translations
import gg.grounds.proxy.api.PlayerLocaleQuery
import gg.grounds.proxy.api.PlayerRoleQuery
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import gg.grounds.proxy.velocity.command.MotdCommand
import gg.grounds.proxy.velocity.command.OnlineCommand
import gg.grounds.proxy.velocity.command.RegionCommand
import gg.grounds.proxy.velocity.handler.NatsHandler
import gg.grounds.proxy.velocity.motd.MotdConfigStore
import gg.grounds.proxy.velocity.motd.MotdGgClient
import gg.grounds.proxy.velocity.motd.MotdManager
import gg.grounds.proxy.velocity.tab.TabList
import io.nats.client.Subscription
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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

@Plugin(id = "plugin-proxy", name = "GroundsProxyPlugin", version = BuildInfo.VERSION)
class GroundsProxyPlugin
@Inject
constructor(private val proxy: ProxyServer, private val logger: Logger) {
    private lateinit var natsHandler: NatsHandler
    private val systemSubscriptions = ConcurrentHashMap<UUID, Subscription>()
    private val transferSubscriptions = ConcurrentHashMap<UUID, Subscription>()
    private val hostTransferSubscriptions = ConcurrentHashMap<UUID, Subscription>()

    /**
     * The last network-wide player count service-player published, or null until the first one
     * arrives.
     *
     * Null is not zero, and the ping handler treats it that way: before the first broadcast we let
     * Velocity report its own count rather than claim an empty network. Volatile because it is
     * written on a NATS dispatcher thread and read on whichever thread answers a ping.
     */
    @Volatile private var networkPlayerCount: Int? = null
    private var countSubscription: Subscription? = null
    private var tabList: TabList? = null

    /**
     * The network-wide MOTD, or null when this proxy has no service-config to read it from. Null
     * disables `/motd` entirely rather than offering a command that cannot store anything.
     */
    @Volatile private var motdManager: MotdManager? = null
    private var motdStore: MotdConfigStore? = null

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        val natsUrl = System.getenv("NATS_URL") ?: "nats://nats.infra:4222"

        natsHandler = NatsHandler(natsUrl, logger)
        natsHandler.connect()

        val proxyServiceImpl = ProxyServiceImpl(natsHandler, proxy)
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
        val region = { System.getenv("REGION")?.trim()?.takeIf(String::isNotEmpty) }
        proxy.commandManager.register("online", OnlineCommand(service))
        proxy.commandManager.register("region", RegionCommand(catalog, region, service))

        // The network-wide player count, pushed every few seconds. Subscribed once for the whole
        // proxy rather than per player: it is a property of the network, not of anybody's session.
        countSubscription =
            natsHandler.subscribe(PLAYER_COUNTS_SUBJECT) { payload ->
                val parsed = parsePlayerCount(payload)
                if (parsed == null) {
                    logger.warn("Ignoring malformed player-count broadcast: {}", payload)
                } else {
                    networkPlayerCount = parsed
                }
            }

        startMotd()

        // Subscribe existing players for system/transfer messages
        proxy.allPlayers.forEach { subscribeForPlayer(it.uniqueId) }

        val messages =
            Translations.forBundle(
                "gg.grounds.proxy.messages",
                javaClass.classLoader,
                localeResolver =
                    LocaleResolver { audience ->
                        ProxyServiceRegistry.get(PlayerLocaleQuery::class.java)?.let { query ->
                            audience.get(Identity.UUID).map(query::localeOf).orElse(null)
                        } ?: LocaleResolver.FROM_AUDIENCE.localeOf(audience)
                    },
            )
        // Looked up per call, not captured: plugin-permissions may register after this runs, and a
        // reference taken now would stay null for the life of the proxy.
        val tab =
            TabList(proxy, messages, region) {
                ProxyServiceRegistry.get(PlayerRoleQuery::class.java)
            }
        tabList = tab

        // On a timer as well as on join: the ping and the roster both change with no event to hang
        // off, and a footer that shows the ping from the moment you logged in is worse than none.
        proxy.scheduler
            .buildTask(this, Runnable { tab.refreshAll() })
            .delay(Duration.ofSeconds(TAB_REFRESH_SECONDS))
            .repeat(Duration.ofSeconds(TAB_REFRESH_SECONDS))
            .schedule()

        logger.info("plugin-proxy enabled (nats={})", natsUrl)
    }

    /**
     * Brings up the network-wide MOTD, if this deployment has a service-config to keep it in.
     *
     * Without `CONFIG_GRPC_TARGET` the whole feature stays off and Velocity's own MOTD is served —
     * the same thing that happened before there was a `/motd`. That is the right shape for a
     * per-engineer proxy or a local run, where there is no config service to talk to.
     */
    private fun startMotd() {
        val target = env("CONFIG_GRPC_TARGET")
        if (target == null) {
            logger.info("MOTD disabled (reason=CONFIG_GRPC_TARGET_unset)")
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
    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        val count = networkPlayerCount
        val motd = motdManager
        if (count == null && motd == null) return

        val builder = event.ping.asBuilder()
        count?.let { builder.onlinePlayers(it) }
        motd?.let { manager ->
            manager.maxPlayers()?.let { builder.maximumPlayers(it) }
            // The numbers this very ping is about to report, so a MOTD that mentions them cannot
            // disagree with the pair printed next to it.
            manager.render(builder.onlinePlayers, builder.maximumPlayers)?.let {
                builder.description(it)
            }
        }
        event.ping = builder.build()
    }

    @Subscribe
    fun onLogin(event: PostLoginEvent) {
        subscribeForPlayer(event.player.uniqueId)
        tabList?.refresh(event.player)
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
    fun onDisconnect(event: DisconnectEvent) {
        cleanupPlayer(event.player.uniqueId)
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        ProxyServiceRegistry.unregister(ProxyService::class.java)
        countSubscription?.let { natsHandler.unsubscribe(it) }
        motdStore?.close()
        if (this::natsHandler.isInitialized) {
            natsHandler.close()
        }
    }

    private fun subscribeForPlayer(playerId: UUID) {
        val sysSub =
            natsHandler.subscribe("proxy.system.$playerId") { payload ->
                val component = GsonComponentSerializer.gson().deserialize(payload)
                proxy.getPlayer(playerId).orElse(null)?.sendMessage(component)
            }
        systemSubscriptions[playerId] = sysSub

        val transferSub =
            natsHandler.subscribe("proxy.transfer.$playerId") { serverName ->
                val player = proxy.getPlayer(playerId).orElse(null) ?: return@subscribe
                val server = proxy.getServer(serverName).orElse(null)
                if (server != null) {
                    player.sendMessage(
                        Component.text("Folge Party-Leader zu $serverName...", NamedTextColor.GREEN)
                    )
                    player.createConnectionRequest(server).fireAndForget()
                } else {
                    logger.warn(
                        "Transfer target server '{}' not found for {}",
                        serverName,
                        playerId,
                    )
                }
            }
        transferSubscriptions[playerId] = transferSub

        // A transfer to a different *proxy*, published by whichever proxy ran the command. The
        // player is on this one, so this is where the packet has to be sent from.
        val hostSub =
            natsHandler.subscribe("proxy.host-transfer.$playerId") { target ->
                val player = proxy.getPlayer(playerId).orElse(null) ?: return@subscribe
                val host = target.substringBeforeLast(':', target)
                val port = target.substringAfterLast(':', "").toIntOrNull()
                if (host.isBlank() || port == null) {
                    logger.warn(
                        "Ignoring malformed host-transfer target '{}' for {}",
                        target,
                        playerId,
                    )
                    return@subscribe
                }
                player.transferToHost(InetSocketAddress.createUnresolved(host, port))
            }
        hostTransferSubscriptions[playerId] = hostSub
    }

    private fun cleanupPlayer(playerId: UUID) {
        systemSubscriptions.remove(playerId)?.let { natsHandler.unsubscribe(it) }
        transferSubscriptions.remove(playerId)?.let { natsHandler.unsubscribe(it) }
        hostTransferSubscriptions.remove(playerId)?.let { natsHandler.unsubscribe(it) }
    }
}
