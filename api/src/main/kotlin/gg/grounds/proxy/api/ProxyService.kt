package gg.grounds.proxy.api

import java.util.UUID
import net.kyori.adventure.text.Component

/**
 * What a plugin needs to act on a player who may be on *another* proxy: find them, talk to them,
 * move them. Local players are answered from Velocity directly; anyone else via the registered
 * [PlayerSessionQuery] (lookup) and NATS (delivery).
 *
 * Obtain an instance with `ProxyServiceRegistry.get(ProxyService::class.java)`.
 */
interface ProxyService {
    /**
     * Tab-complete suggestions for names starting with [prefix] — local players first, then the
     * rest of the network, capped at [limit].
     *
     * There is intentionally no "list every online player": tab-complete runs on every keystroke,
     * and a full roster at 10k players would be a large response sent thousands of times a second.
     */
    fun suggestPlayerNames(
        prefix: String,
        limit: Int = DEFAULT_SUGGESTION_LIMIT,
    ): Collection<String>

    fun resolvePlayerId(name: String): UUID?

    fun resolvePlayerName(playerId: UUID): String?

    fun isOnline(playerId: UUID): Boolean

    fun getPresence(playerId: UUID): PlayerPresence?

    /**
     * Players per backend server across the whole network, or null when the network cannot be
     * asked.
     *
     * Velocity's own `playersConnected` only ever counts the players on *this* proxy, so with two
     * proxies in front of one lobby each sees half of it. Anything that shows a player a number
     * about the network has to come from here.
     *
     * Null rather than a proxy-local fallback on purpose: a local count is indistinguishable from a
     * network count once it is rendered, and silently showing the wrong one is the bug this
     * replaces. Callers should say the number is proxy-local instead.
     */
    fun getNetworkPlayerCounts(): NetworkPlayerCounts?

    /**
     * Delivers to the player wherever they are — locally, or over NATS to the proxy holding them.
     */
    fun sendToPlayer(targetId: UUID, message: Component)

    /**
     * Players per proxy across the network, with each proxy's region, or null when the network
     * cannot be asked. Same null contract as [getNetworkPlayerCounts]: do not substitute local
     * numbers.
     */
    fun getNetworkProxyCounts(): NetworkProxyCounts?

    /** Moves the player to [serverName], including when they are connected to another proxy. */
    fun transferPlayer(playerId: UUID, serverName: String)

    /**
     * Sends the player to a different *proxy* — a different address entirely, not a backend server
     * behind this one.
     *
     * This is the Minecraft transfer packet: the client disconnects and reconnects to [host] by
     * itself, keeping the session, so the player sees a load screen rather than "you have been
     * disconnected". It is what moves someone between regions, and what empties a proxy before it
     * is taken down.
     *
     * Works for a player on another proxy too — the request travels over NATS to whichever proxy
     * holds them, exactly like [transferPlayer].
     *
     * Returns false only when the player is not online anywhere. A transfer that reaches the client
     * and fails there cannot be observed from here; the client falls back to the server list.
     */
    fun transferToHost(playerId: UUID, host: String, port: Int = DEFAULT_MINECRAFT_PORT): Boolean

    /**
     * Transfers every player on *this* proxy to [host], and returns how many were sent.
     *
     * Deliberately local: draining means emptying the proxy you are draining, and a call that could
     * empty someone else's is a foot-gun with no use case. Run it on each proxy you want emptied.
     */
    fun drainToHost(host: String, port: Int = DEFAULT_MINECRAFT_PORT): Int

    companion object {
        const val DEFAULT_SUGGESTION_LIMIT = 20
        const val DEFAULT_MINECRAFT_PORT = 25565
    }
}
