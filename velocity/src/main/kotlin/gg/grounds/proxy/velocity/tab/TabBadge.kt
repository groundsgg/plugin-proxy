package gg.grounds.proxy.velocity.tab

import kotlin.math.max
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

object TabBadge {
    fun chip(label: String, fill: TextColor): Component {
        val textWidth = VanillaAdvances.width(label)
        val pad = 4
        val inner =
            max(textWidth + pad, TabGlyphs.LEFT_PX + TabGlyphs.RIGHT_PX + TabGlyphs.MIDDLE_PX)
        val middles = inner - TabGlyphs.LEFT_PX - TabGlyphs.RIGHT_PX
        val badgeWidth = TabGlyphs.LEFT_PX + middles * TabGlyphs.MIDDLE_PX + TabGlyphs.RIGHT_PX
        val padLeft = (badgeWidth - textWidth) / 2
        val padRight = badgeWidth - textWidth - padLeft
        val gap = TabSpaces.of(-1)
        val slices = buildString {
            append(TabGlyphs.BADGE_LEFT)
            append(gap)
            repeat(middles) {
                append(TabGlyphs.BADGE_MIDDLE)
                append(gap)
            }
            append(TabGlyphs.BADGE_RIGHT)
            append(gap)
        }
        return Component.text()
            .append(Component.text(slices, fill).font(TabGlyphs.FONT))
            .append(Component.text(TabSpaces.of(-badgeWidth)).font(TabGlyphs.FONT))
            .append(Component.text(TabSpaces.of(padLeft)).font(TabGlyphs.FONT))
            .append(Component.text(label, NamedTextColor.WHITE))
            .append(Component.text(TabSpaces.of(padRight)).font(TabGlyphs.FONT))
            .build()
    }
}
