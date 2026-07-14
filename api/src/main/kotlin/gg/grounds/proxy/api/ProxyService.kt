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

    /** Moves the player to [serverName], including when they are connected to another proxy. */
    fun transferPlayer(playerId: UUID, serverName: String)

    companion object {
        const val DEFAULT_SUGGESTION_LIMIT = 20
    }
}
