package gg.grounds.proxy.velocity.command

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

internal object WhereAmIGlyphs {
    val FONT = Key.key("grounds:connection")
    const val REGION = '\uE410'
    const val PROXY = '\uE411'
    const val SERVER = '\uE412'

    fun render(glyph: Char, ready: Boolean): Component =
        if (ready)
            Component.empty()
                .append(Component.text(glyph.toString(), NamedTextColor.WHITE).font(FONT))
                .append(Component.space())
        else Component.empty()

    /** Readiness for a HUD pack is proof only when every allowed pack has these new glyphs. */
    fun enabled(environment: Map<String, String>): Boolean {
        fun hashes(name: String) =
            environment[name]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        val hud = hashes("HUD_FONT_PACK_SHA1S")
        val connections = hashes("WHEREAMI_FONT_PACK_SHA1S")
        return hud.isNotEmpty() && hud.all { it in connections }
    }
}
