package gg.grounds.proxy.api

import java.util.UUID

/** Performs a session-token handoff before sending a player to another proxy host. */
fun interface PlayerSessionTransfer {
    fun transferToHost(playerId: UUID, host: String, port: Int): Boolean
}
