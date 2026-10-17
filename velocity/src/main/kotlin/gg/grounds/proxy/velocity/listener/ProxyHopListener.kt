package gg.grounds.proxy.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.CookieReceiveEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.ProxyHop
import gg.grounds.proxy.velocity.ProxySystemMessage
import gg.grounds.proxy.velocity.RegionCatalog
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import net.kyori.adventure.key.Key
import org.slf4j.Logger

/**
 * Carries a player towards the proxy `/proxy <id>` asked for — see [ProxyHop] for why it takes
 * hops.
 *
 * Sending: the ticket is stored as a cookie, read back, and only once the client echoes it is the
 * transfer sent, the same order plugin-agones' drain settled on so the cookie is known to be stored
 * before the client leaves. A client that never echoes is transferred after [ECHO_TIMEOUT_MILLIS]
 * anyway.
 *
 * Arriving: on a player's first server the cookie is asked for. The check runs in play rather than
 * at login, where a transfer is known to work, at the price of one lobby load per hop.
 */
class ProxyHopListener(
    private val plugin: Any,
    private val proxy: ProxyServer,
    private val proxyId: String,
    /** Where this proxy's region answers, which is the way back to the load balancer. */
    private val address: () -> RegionCatalog.Region?,
    private val messages: Translations,
    private val logger: Logger,
) {
    private val pending = ConcurrentHashMap<UUID, Pending>()

    /** False when there is no address to send [player] through; nothing was sent then. */
    fun send(player: Player, ticket: ProxyHop.Ticket): Boolean {
        val target = address() ?: return false
        val payload = ProxyHop.encode(ticket)
        val stage = Pending(payload, target)
        pending[player.uniqueId] = stage
        try {
            player.storeCookie(COOKIE_KEY, payload)
            player.requestCookie(COOKIE_KEY)
            proxy.scheduler
                .buildTask(plugin, Runnable { complete(player, stage) })
                .delay(ECHO_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .schedule()
        } catch (_: Exception) {
            complete(player, stage)
        }
        return true
    }

    @Subscribe
    fun onServerConnected(event: ServerPostConnectEvent) {
        if (event.previousServer != null) return
        val player = event.player
        if (player.protocolVersion < ProtocolVersion.MINECRAFT_1_20_5) return
        player.requestCookie(COOKIE_KEY)
    }

    @Subscribe
    fun onCookieReceive(event: CookieReceiveEvent) {
        if (event.originalKey != COOKIE_KEY) return
        // Unhandled, Velocity would pass the answer on to the backend, which never asked.
        event.result = CookieReceiveEvent.ForwardResult.handled()
        val player = event.player
        val payload = event.originalData

        val stage = pending[player.uniqueId]
        if (stage != null) {
            if (payload != null && stage.payload.contentEquals(payload)) complete(player, stage)
            return
        }

        when (val decision = ProxyHop.decide(payload, proxyId)) {
            ProxyHop.Decision.None -> Unit
            is ProxyHop.Decision.Arrived -> {
                clear(player)
                logger.info("Proxy hop arrived (player={}, proxy={})", player.username, proxyId)
                messages.send(player, ProxySystemMessage.PROXY_ARRIVED, "proxy" to proxyId)
            }
            is ProxyHop.Decision.HopAgain -> {
                logger.info(
                    "Proxy hop missed (player={}, wanted={}, landed={}, hopsLeft={})",
                    player.username,
                    decision.ticket.target,
                    proxyId,
                    decision.ticket.hopsLeft,
                )
                if (!send(player, decision.ticket)) giveUp(player, decision.ticket.target)
            }
            is ProxyHop.Decision.GiveUp -> giveUp(player, decision.target)
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        pending.remove(event.player.uniqueId)
    }

    private fun giveUp(player: Player, target: String) {
        clear(player)
        logger.info(
            "Proxy hop gave up (player={}, wanted={}, landed={})",
            player.username,
            target,
            proxyId,
        )
        messages.send(
            player,
            ProxySystemMessage.PROXY_NOT_REACHED,
            "proxy" to target,
            "hops" to ProxyHop.MAX_HOPS,
            "current" to proxyId,
        )
    }

    private fun clear(player: Player) {
        try {
            player.storeCookie(COOKIE_KEY, byteArrayOf())
        } catch (_: Exception) {
            // A ticket left behind is spent anyway: it either names this proxy or has no hops left.
        }
    }

    private fun complete(player: Player, stage: Pending) {
        if (!pending.remove(player.uniqueId, stage) || !player.isActive) return
        player.transferToHost(InetSocketAddress.createUnresolved(stage.target.host, stage.target.port))
    }

    private class Pending(val payload: ByteArray, val target: RegionCatalog.Region)

    private companion object {
        val COOKIE_KEY: Key = Key.key(ProxyHop.KEY)
        const val ECHO_TIMEOUT_MILLIS = 1_000L
    }
}
