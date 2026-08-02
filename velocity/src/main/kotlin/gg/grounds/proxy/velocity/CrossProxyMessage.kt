package gg.grounds.proxy.velocity

import java.util.UUID

/**
 * The subjects one proxy uses to reach a player held by another, and the envelope that says who.
 *
 * These used to be keyed by player — `proxy.system.<playerId>` — which meant every proxy held one
 * subscription per online player per feature. That is bounded (a logout unsubscribes) but the
 * interest still crosses the leafnode to the hub on every single join and leave, and a proxy
 * restart re-subscribes its whole roster at once. Keying by *proxy* instead makes the interest
 * constant: three subscriptions, taken at startup, whatever the player count does.
 *
 * The trade is that the sender now has to know which proxy holds the target, and gets it wrong if
 * the player moves between the lookup and the publish. That race is real but small, and none of
 * these three messages was ever delivery-guaranteed: they are a chat line, a server switch and a
 * transfer packet, each of which a player can already miss by quitting a moment earlier.
 *
 * [PROXY_ID] is the pod name, the same value plugin-player records on the session — which is what
 * makes `PlayerPresence.proxyId` a usable address rather than a label.
 */
internal object CrossProxyMessage {

    /** A chat or system line for a player on another proxy. */
    const val SYSTEM = "proxy.system"

    /** "Move this player to that backend server." */
    const val TRANSFER = "proxy.transfer"

    /**
     * "Move this player to that proxy," sent as a Minecraft transfer packet by whoever holds them.
     */
    const val HOST_TRANSFER = "proxy.host-transfer"

    /** The subject a proxy publishes to in order to reach [proxyId]. */
    fun subjectFor(prefix: String, proxyId: String): String = "$prefix.$proxyId"

    /**
     * Who the message is for, in front of the payload it used to be on its own.
     *
     * A newline rather than JSON: the payloads are a serialized Component, a server name and a
     * `host:port`, none of which contains one, and a parser here would be a dependency bought for a
     * single field.
     */
    fun encode(targetId: UUID, payload: String): String = "$targetId\n$payload"

    /** The [encode] pair, or null if this is not something this version wrote. */
    fun decode(raw: String): Pair<UUID, String>? {
        val split = raw.indexOf('\n')
        if (split <= 0) return null
        val targetId =
            try {
                UUID.fromString(raw.substring(0, split))
            } catch (_: IllegalArgumentException) {
                return null
            }
        return targetId to raw.substring(split + 1)
    }
}
