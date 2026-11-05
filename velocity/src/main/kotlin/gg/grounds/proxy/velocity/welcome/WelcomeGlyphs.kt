package gg.grounds.proxy.velocity.welcome

import net.kyori.adventure.key.Key

internal object WelcomeGlyphs {
    val FONT = Key.key("grounds:welcome")
    const val DISCORD = '\uE400'
    const val TIKTOK = '\uE401'
    const val SHOP = '\uE402'
    const val LOGO = '\uE403'
    const val LOGO_RIGHT = '\uE404'
    const val LOGO_JOIN = '\uE405'
    const val LOGO_TEXT = "\uE403\uE405\uE404"
    const val LOGO_SLICE_ADVANCE = 46.0
    // Each 256x241 cell renders 42*256/241 pixels wide; remove native rounded glyph spacing.
    const val LOGO_JOIN_ADVANCE = -1.38589211618257
    const val LOGO_ADVANCE = 90.61410788381743

    /** HUD readiness is sufficient only if every approved HUD pack contains these glyphs. */
    fun supportedBy(hudPacks: Set<String>, welcomePacks: Set<String>): Boolean =
        hudPacks.isNotEmpty() && hudPacks.all { it in welcomePacks }

    private fun hashes(environment: Map<String, String>, name: String): Set<String> =
        environment[name].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    fun enabled(environment: Map<String, String>): Boolean =
        supportedBy(
            hashes(environment, "HUD_FONT_PACK_SHA1S"),
            hashes(environment, "WELCOME_FONT_PACK_SHA1S"),
        )

    fun decoratedLogoEnabled(environment: Map<String, String>): Boolean =
        supportedBy(
            hashes(environment, "HUD_FONT_PACK_SHA1S"),
            hashes(environment, "WELCOME_LOGO_PACK_SHA1S"),
        )
}
