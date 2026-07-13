package gg.grounds.proxy.api

/**
 * Where a player currently is: which proxy holds their connection ([proxyId], e.g. `velocity-2`)
 * and which backend server they are playing on ([server], e.g. `minestom-lobby-2-kvlkz-5h977`).
 *
 * Either can be empty. A player who has connected to a proxy but not yet reached a backend is on no
 * server, and a session written by an older plugin-player carries no proxy id.
 *
 * [joinedAt] is epoch millis, and is 0 for a player on *this* proxy — Velocity does not record it,
 * and asking the presence service for something already in memory is not worth a round trip.
 */
data class PlayerPresence(val proxyId: String, val server: String, val joinedAt: Long)
