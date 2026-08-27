package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.Palette
import gg.grounds.proxy.api.PlayerRole
import java.util.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

object TabName {
    private const val GAP = 2

    fun format(name: String, locale: Locale?, role: PlayerRole?): Component {
        val colour = role?.colour?.let(TextColor::fromHexString) ?: Palette.TEXT
        val row = Component.text()
        locale
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?.let { language ->
                row.append(TabBadge.chip(language.uppercase(Locale.ROOT), Palette.TEXT_FAINT))
                row.append(Component.text(TabSpaces.of(GAP)).font(TabGlyphs.FONT))
            }
        role
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { rank ->
                row.append(TabBadge.chip(rank.uppercase(Locale.ROOT), colour))
                row.append(Component.text(TabSpaces.of(GAP)).font(TabGlyphs.FONT))
            }
        row.append(Component.text(name, colour))
        val pad = TabGlyphs.HEADER_WIDTH - rowWidth(name, locale, role)
        if (pad > 0) {
            row.append(Component.text(TabSpaces.of(pad)).font(TabGlyphs.FONT))
        }
        return row.build()
    }

    private fun rowWidth(name: String, locale: Locale?, role: PlayerRole?): Int {
        var width = VanillaAdvances.width(name)
        locale
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?.let { width += TabBadge.width(it.uppercase(Locale.ROOT)) + GAP }
        role
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { width += TabBadge.width(it.uppercase(Locale.ROOT)) + GAP }
        return width
    }
}
