package gg.grounds.proxy.api

import java.util.UUID

interface PlayerSessionQuery {
    fun getOnlinePlayers(): Collection<OnlinePlayer>

    fun getSession(playerId: UUID): PlayerSessionInfo?

    fun resolveByName(name: String): PlayerSessionInfo?
}

data class OnlinePlayer(
    val playerId: UUID,
    val name: String,
    val proxyId: String?,
    val server: String?,
)

data class PlayerSessionInfo(
    val playerId: UUID,
    val name: String,
    val proxyId: String?,
    val server: String?,
    val connectedAt: Long,
)
