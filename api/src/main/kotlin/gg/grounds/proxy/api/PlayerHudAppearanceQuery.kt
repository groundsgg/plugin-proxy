package gg.grounds.proxy.api

import java.util.UUID

/** Only a verified currently loaded HUD-capable pack may enable bitmap HUD glyphs. */
fun interface PlayerHudAppearanceQuery {
    fun appearanceOf(playerId: UUID): PlayerHudAppearance
}

data class PlayerHudAppearance(val ready: Boolean = false)
