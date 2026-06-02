package gg.grounds.proxy.velocity

import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.proxy.api.PlayerPresence
import gg.grounds.proxy.api.PlayerSessionQuery
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.api.ProxyServiceRegistry
import gg.grounds.proxy.velocity.handler.NatsHandler
import java.util.UUID
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer

class ProxyServiceImpl(private val natsHandler: NatsHandler, private val proxy: ProxyServer) :
    ProxyService {

    private fun sessionQuery(): PlayerSessionQuery? =
        ProxyServiceRegistry.get(PlayerSessionQuery::class.java)

    override fun getOnlinePlayerNames(): Collection<String> {
        val localNames = proxy.allPlayers.map { it.username }
        val remoteNames = sessionQuery()?.getOnlinePlayers()?.map { it.name } ?: emptyList()
        return (localNames + remoteNames).distinct()
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

    override fun sendToPlayer(targetId: UUID, message: Component) {
        val local = proxy.getPlayer(targetId).orElse(null)
        if (local != null) {
            local.sendMessage(message)
        } else {
            val json = GsonComponentSerializer.gson().serialize(message)
            natsHandler.publish("proxy.system.$targetId", json)
        }
    }

    override fun transferPlayer(playerId: UUID, serverName: String) {
        val local = proxy.getPlayer(playerId).orElse(null)
        if (local != null) {
            val server = proxy.getServer(serverName).orElse(null) ?: return
            local.createConnectionRequest(server).fireAndForget()
        } else {
            natsHandler.publish("proxy.transfer.$playerId", serverName)
        }
    }
}
