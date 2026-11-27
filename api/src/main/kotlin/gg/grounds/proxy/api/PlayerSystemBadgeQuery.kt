package gg.grounds.proxy.api

import java.util.UUID

/** Reports readiness for the system-badge font on this recipient's current Java session. */
fun interface PlayerSystemBadgeQuery {
    fun systemBadgesReady(playerId: UUID): Boolean
}
