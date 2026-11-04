package gg.grounds.proxy.velocity.welcome

import gg.grounds.proxy.velocity.tab.TabGlyphs
import gg.grounds.proxy.velocity.tab.TabSpaces
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextDecoration

/**
 * Align within the default 320px chat column; the server cannot read client chat-width settings.
 */
internal object WelcomeLayout {
    private val defaultFont = Key.key("minecraft:default")

    fun center(line: Component, packReady: Boolean): Component {
        val inset = ((320 - width(line)) / 2).coerceAtLeast(0)
        val padding =
            if (packReady) Component.text(TabSpaces.of(inset)).font(TabGlyphs.FONT)
            else Component.text(" ".repeat(inset / 4)).font(defaultFont)
        // Padding is a sibling so it cannot inherit a link target or tint the bitmap.
        return Component.empty().append(padding).append(line)
    }

    private fun width(
        component: Component,
        inheritedFont: Key = defaultFont,
        inheritedBold: Boolean = false,
    ): Int {
        val font = component.font() ?: inheritedFont
        val bold =
            when (component.decoration(TextDecoration.BOLD)) {
                TextDecoration.State.TRUE -> true
                TextDecoration.State.FALSE -> false
                TextDecoration.State.NOT_SET -> inheritedBold
            }
        val own =
            (component as? TextComponent)?.content()?.sumOf { ch ->
                when {
                    font == TabGlyphs.FONT && ch == TabGlyphs.LOGO -> TabGlyphs.LOGO_ADVANCE
                    font == WelcomeGlyphs.FONT && ch == WelcomeGlyphs.SHOP -> 13
                    font == WelcomeGlyphs.FONT && ch in '\uE400'..'\uE401' -> 14
                    else -> advance(ch) + if (bold && ch != ' ') 1 else 0
                }
            } ?: 0
        return own + component.children().sumOf { width(it, font, bold) }
    }

    /** Native Java bitmap advances, including the Latin accents used by the welcome locales. */
    private fun advance(ch: Char): Int =
        when (ch) {
            ' ' -> 4
            '!',
            '\'',
            ',',
            '.',
            ':',
            ';',
            'i',
            '|',
            '¡' -> 2
            '`',
            'l',
            'í' -> 3
            '"',
            '(',
            ')',
            '*',
            'I',
            '[',
            ']',
            't',
            '{',
            '}' -> 4
            '<',
            '>',
            'f',
            'k' -> 5
            '@',
            '~' -> 7
            '→' -> 8
            else -> 6
        }
}
