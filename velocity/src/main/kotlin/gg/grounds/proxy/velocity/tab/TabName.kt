package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.Palette
import gg.grounds.proxy.api.PlayerRole
import java.util.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

object TabName {
    fun format(name: String, locale: Locale?, role: PlayerRole?): Component {
        val colour = role?.colour?.let(TextColor::fromHexString) ?: Palette.TEXT
        val row = Component.text()
        locale
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?.let { language ->
                row.append(TabBadge.chip(language.uppercase(Locale.ROOT), Palette.TEXT_FAINT))
                row.append(Component.text(TabSpaces.of(2)).font(TabGlyphs.FONT))
            }
        role
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { rank ->
                row.append(TabBadge.chip(rank.uppercase(Locale.ROOT), colour))
                row.append(Component.text(TabSpaces.of(2)).font(TabGlyphs.FONT))
            }
        row.append(Component.text(name, colour))
        return row.build()
    }
}
