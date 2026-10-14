package gg.grounds.proxy.api

import java.util.UUID

/**
 * Per-recipient chat rendering capabilities for an online player.
 *
 * Providers must fail closed: an offline player, unknown client platform, or unverified resource
 * pack returns the default text-only appearance.
 */
interface PlayerChatAppearanceQuery {
    fun appearanceOf(playerId: UUID): PlayerChatAppearance
}

data class PlayerChatAppearance(
    val javaHeadsSupported: Boolean = false,
    val chatFontReady: Boolean = false,
)
