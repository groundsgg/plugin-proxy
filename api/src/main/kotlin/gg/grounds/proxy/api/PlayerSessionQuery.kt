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

    /**
     * How many players each proxy holds, and which region that proxy is in.
     *
     * The same players as [countPlayersByServer], grouped along a different axis: that one answers
     * which backend servers are busy, this one answers how the network is spread across proxies and
     * regions. No caller wants both, which is why they are separate calls rather than one reply
     * carrying two groupings.
     *
     * Null when the network cannot be asked — and the same warning applies: a proxy-local number
     * looks exactly like a network number once rendered.
     *
     * Defaulted to null so an older presence plugin that predates this method still satisfies the
     * interface. Without the default it would compile and then throw AbstractMethodError at the
     * first call, which is a much worse way to find out.
     */
    fun countPlayersByProxy(): NetworkProxyCounts? = null
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

/**
 * How many players sit on each proxy right now.
 *
 * [proxies] holds one entry per *occupied* proxy; an idle proxy is absent rather than zero, since a
 * caller cannot enumerate proxies anyway. Unlike [NetworkPlayerCounts], [total] *equals* the sum of
 * the entries: every session belongs to exactly one proxy, whereas a player may be on no backend
 * server yet.
 */
data class NetworkProxyCounts(val proxies: List<ProxyPlayers>, val total: Int) {
    /**
     * Players per region, with proxies in the same region added together.
     *
     * A proxy whose region is unknown is counted under null rather than dropped — losing players
     * from a total is worse than showing a row that says "unknown".
     */
    val byRegion: Map<String?, Int>
        get() = proxies.groupBy { it.region }.mapValues { (_, group) -> group.sumOf { it.players } }
}

/**
 * One proxy's share of the players online. [region] is null when the proxy declares none, which is
 * also what every session created before a proxy declared one looks like.
 */
data class ProxyPlayers(val proxyId: String, val region: String?, val players: Int)

data class PlayerSessionInfo(
    val playerId: UUID,
    val name: String,
    val proxyId: String?,
    val server: String?,
    val connectedAt: Long,
    /**
     * Where the proxy holding this player is. Null when unknown — carried from the session rather
     * than parsed out of [proxyId], which is a pod name and says nothing about place.
     */
    val region: String? = null,
)
