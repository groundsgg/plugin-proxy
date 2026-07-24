package gg.grounds.proxy.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.BuildInfo
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import gg.grounds.proxy.velocity.command.OnlineCommand
import gg.grounds.proxy.velocity.command.RegionCommand
import gg.grounds.proxy.velocity.handler.NatsHandler
import io.nats.client.Subscription
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.slf4j.Logger

@Plugin(id = "plugin-proxy", name = "GroundsProxyPlugin", version = BuildInfo.VERSION)
class GroundsProxyPlugin
@Inject
constructor(private val proxy: ProxyServer, private val logger: Logger) {
    private lateinit var natsHandler: NatsHandler
    private val systemSubscriptions = ConcurrentHashMap<UUID, Subscription>()
    private val transferSubscriptions = ConcurrentHashMap<UUID, Subscription>()
    private val hostTransferSubscriptions = ConcurrentHashMap<UUID, Subscription>()

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
        proxy.commandManager.register("online", OnlineCommand(service))
        proxy.commandManager.register(
            "region",
            RegionCommand(
                catalog,
                { System.getenv("REGION")?.trim()?.takeIf(String::isNotEmpty) },
                service,
            ),
        )

        // Subscribe existing players for system/transfer messages
        proxy.allPlayers.forEach { subscribeForPlayer(it.uniqueId) }

        logger.info("plugin-proxy enabled (nats={})", natsUrl)
    }

    @Subscribe
    fun onLogin(event: PostLoginEvent) {
        subscribeForPlayer(event.player.uniqueId)
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        cleanupPlayer(event.player.uniqueId)
    }

    @Subscribe
    fun onShutdown(event: ProxyShutdownEvent) {
        ProxyServiceRegistry.unregister(ProxyService::class.java)
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
