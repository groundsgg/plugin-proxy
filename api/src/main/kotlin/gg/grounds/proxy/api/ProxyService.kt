package gg.grounds.proxy.api

import java.util.UUID
import net.kyori.adventure.text.Component

interface ProxyService {
    fun getOnlinePlayerNames(): Collection<String>

    fun resolvePlayerId(name: String): UUID?

    fun resolvePlayerName(playerId: UUID): String?

    fun isOnline(playerId: UUID): Boolean

    fun getPresence(playerId: UUID): PlayerPresence?

    fun sendToPlayer(targetId: UUID, message: Component)

    fun transferPlayer(playerId: UUID, serverName: String)
}
