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

    /**
     * How many players are on each backend server, across every proxy.
     *
     * Null when the network cannot be asked (no presence backend reachable). Callers must not
     * quietly substitute their own proxy's numbers for this — a proxy-local count *looks* like a
     * network count and is the very thing this exists to replace.
     */
    fun countPlayersByServer(): NetworkPlayerCounts?
}

/**
 * A snapshot of who is online network-wide.
 *
 * [byServer] holds one entry per *occupied* backend server; a server nobody is on is absent rather
 * than zero. [total] counts everyone online, including players who have not reached a backend
 * server yet, so it can exceed the sum of [byServer].
 */
data class NetworkPlayerCounts(val byServer: Map<String, Int>, val total: Int) {
    fun on(serverName: String): Int = byServer[serverName] ?: 0
}

data class PlayerSessionInfo(
    val playerId: UUID,
    val name: String,
    val proxyId: String?,
    val server: String?,
    val connectedAt: Long,
)
