package gg.grounds.proxy.velocity.welcome

import net.kyori.adventure.key.Key

internal object WelcomeGlyphs {
    val FONT = Key.key("grounds:welcome")
    const val DISCORD = '\uE400'
    const val TIKTOK = '\uE401'
    const val SHOP = '\uE402'

    /** HUD readiness is sufficient only if every approved HUD pack contains these glyphs. */
    fun supportedBy(hudPacks: Set<String>, welcomePacks: Set<String>): Boolean =
        hudPacks.isNotEmpty() && hudPacks.all { it in welcomePacks }

    fun enabled(environment: Map<String, String>): Boolean {
        fun hashes(name: String) =
            environment[name]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        return supportedBy(hashes("HUD_FONT_PACK_SHA1S"), hashes("WELCOME_FONT_PACK_SHA1S"))
    }
}
