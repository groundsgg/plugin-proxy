package gg.grounds.proxy.velocity

import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.proxy.api.NetworkPlayerCounts
import gg.grounds.proxy.api.NetworkProxyCounts
import gg.grounds.proxy.api.PlayerPresence
import gg.grounds.proxy.api.PlayerSessionQuery
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import gg.grounds.proxy.velocity.handler.NatsHandler
import java.net.InetSocketAddress
import java.util.UUID
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer

class ProxyServiceImpl(
    private val natsHandler: NatsHandler,
    private val proxy: ProxyServer,
    private val suggestionCache: SuggestionCache = SuggestionCache(),
) : ProxyService {

    private fun sessionQuery(): PlayerSessionQuery? =
        ProxyServiceRegistry.get(PlayerSessionQuery::class.java)

    /**
     * Local players are free — they are already in memory. The network-wide half is the expensive
     * one, so it is only asked once the player has typed enough to narrow it down, and the answer
     * is cached briefly: Velocity fires tab-complete on every keystroke.
     */
    override fun suggestPlayerNames(prefix: String, limit: Int): Collection<String> {
        val local =
            proxy.allPlayers
                .map { it.username }
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .sorted()
        if (local.size >= limit) return local.take(limit)

        val remote =
            if (prefix.length >= MIN_REMOTE_PREFIX) {
                suggestionCache.get(prefix) {
                    sessionQuery()?.suggestNames(prefix, limit) ?: emptyList()
                }
            } else {
                emptyList()
            }
        return SuggestionCache.merge(local, remote, limit)
    }

    override fun resolvePlayerId(name: String): UUID? {
        return proxy.getPlayer(name).orElse(null)?.uniqueId
            ?: sessionQuery()?.resolveByName(name)?.playerId
    }

    override fun resolvePlayerName(playerId: UUID): String? {
        return proxy.getPlayer(playerId).orElse(null)?.username
            ?: sessionQuery()?.getSession(playerId)?.name
    }

    override fun isOnline(playerId: UUID): Boolean {
        return proxy.getPlayer(playerId).isPresent || sessionQuery()?.getSession(playerId) != null
    }

    override fun getPresence(playerId: UUID): PlayerPresence? {
        val local = proxy.getPlayer(playerId).orElse(null)
        if (local != null) {
            val serverName = local.currentServer.orElse(null)?.serverInfo?.name ?: ""
            return PlayerPresence(
                proxyId = System.getenv("PROXY_ID") ?: "",
                server = serverName,
                joinedAt = 0,
            )
        }
        val session = sessionQuery()?.getSession(playerId) ?: return null
        return PlayerPresence(
            proxyId = session.proxyId ?: "",
            server = session.server ?: "",
            joinedAt = session.connectedAt,
        )
    }

    override fun getNetworkPlayerCounts(): NetworkPlayerCounts? =
        sessionQuery()?.countPlayersByServer()

    override fun sendToPlayer(targetId: UUID, message: Component) {
        val local = proxy.getPlayer(targetId).orElse(null)
        if (local != null) {
            local.sendMessage(message)
        } else {
            val json = GsonComponentSerializer.gson().serialize(message)
            publishToHolder(CrossProxyMessage.SYSTEM, targetId, json)
        }
    }

    /**
     * Publishes to the proxy that holds [targetId], or drops the message if nobody does.
     *
     * Dropping is the honest outcome: the old per-player subject let a proxy publish into the void
     * and call it sent. Here the absence of a session is the same answer, reached before the write
     * instead of after it.
     */
    private fun publishToHolder(prefix: String, targetId: UUID, payload: String): Boolean {
        val holder = getPresence(targetId)?.proxyId?.takeIf { it.isNotBlank() } ?: return false
        natsHandler.publish(
            CrossProxyMessage.subjectFor(prefix, holder),
            CrossProxyMessage.encode(targetId, payload),
        )
        return true
    }

    override fun getNetworkProxyCounts(): NetworkProxyCounts? =
        sessionQuery()?.countPlayersByProxy()

    override fun transferToHost(playerId: UUID, host: String, port: Int): Boolean {
        val local = proxy.getPlayer(playerId).orElse(null)
        if (local != null) {
            // The client reconnects to the new address on its own and keeps its session. Nothing
            // to await: the connection this call is running on is the one about to go away.
            local.transferToHost(InetSocketAddress.createUnresolved(host, port))
            return true
        }
        // Not here — but possibly on another proxy. Publishing is not proof of delivery; false is
        // reserved for "nobody anywhere has them", which the session lookup can actually answer.
        return publishToHolder(CrossProxyMessage.HOST_TRANSFER, playerId, "$host:$port")
    }

    override fun drainToHost(host: String, port: Int): Int {
        val address = InetSocketAddress.createUnresolved(host, port)
        // Snapshot first: transferring mutates allPlayers while we walk it.
        val players = proxy.allPlayers.toList()
        players.forEach { it.transferToHost(address) }
        return players.size
    }

    override fun transferPlayer(playerId: UUID, serverName: String) {
        val local = proxy.getPlayer(playerId).orElse(null)
        if (local != null) {
            val server = proxy.getServer(serverName).orElse(null) ?: return
            local.createConnectionRequest(server).fireAndForget()
        } else {
            publishToHolder(CrossProxyMessage.TRANSFER, playerId, serverName)
        }
    }

    companion object {
        /** One letter matches most of the network; make the player narrow it down first. */
        const val MIN_REMOTE_PREFIX = 2
    }
}
