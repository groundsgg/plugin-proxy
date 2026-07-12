package gg.grounds.proxy.api

import java.util.UUID

/**
 * Cross-proxy player lookup. A proxy knows only the players connected to itself; this answers for
 * the whole network.
 *
 * Registered into the [ProxyServiceRegistry] by whichever plugin owns presence — today
 * plugin-player, backed by service-player. With nothing registered, [ProxyService] degrades to
 * local-only answers.
 */
interface PlayerSessionQuery {
    fun getSession(playerId: UUID): PlayerSessionInfo?

    fun resolveByName(name: String): PlayerSessionInfo?

    /**
     * Online names starting with [prefix], at most [limit] of them.
     *
     * A prefix search, not a roster dump: this feeds tab-complete, which fires on every keystroke,
     * so "all online players" would ship the whole network's names — thousands of times a second at
     * 10k players online. Implementations clamp [limit] themselves.
     */
    fun suggestNames(prefix: String, limit: Int): List<String>
}

data class PlayerSessionInfo(
    val playerId: UUID,
    val name: String,
    val proxyId: String?,
    val server: String?,
    val connectedAt: Long,
)
