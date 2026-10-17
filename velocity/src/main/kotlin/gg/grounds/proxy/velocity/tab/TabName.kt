package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.Palette
import gg.grounds.proxy.api.PlayerDisplayNames
import gg.grounds.proxy.api.PlayerRole
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

object TabName {
    private const val GAP = 2

    fun format(
        name: String,
        role: PlayerRole?,
        bedrock: Boolean = false,
        bedrockViewer: Boolean = false,
    ): Component {
        val displayName = PlayerDisplayNames.displayName(name, bedrock)
        val colour = role?.colour?.let(TextColor::fromHexString) ?: Palette.TEXT
        val rank = role?.let(TabRankLabels::resolve)
        val row = Component.text()
        if (bedrockViewer) {
            rank?.let { row.append(BedrockRankBadges.chip(it, colour)).append(Component.space()) }
            if (bedrock)
                row.append(Component.text('\uE500', NamedTextColor.WHITE)).append(Component.space())
            return row.append(Component.text(displayName, colour)).build()
        }
        rank?.let { label ->
            row.append(TabBadge.chip(label, colour))
            row.append(Component.text(TabSpaces.of(GAP)).font(TabGlyphs.FONT))
        }
        if (bedrock) {
            row.append(
                Component.text(TabGlyphs.BEDROCK_ICON, NamedTextColor.WHITE).font(TabGlyphs.FONT)
            )
            row.append(Component.text(TabSpaces.of(GAP)).font(TabGlyphs.FONT))
        }
        row.append(Component.text(displayName, colour))
        val pad = TabGlyphs.HEADER_WIDTH - rowWidth(displayName, rank, bedrock)
        if (pad > 0) {
            row.append(Component.text(TabSpaces.of(pad)).font(TabGlyphs.FONT))
        }
        return row.build()
    }

    private fun rowWidth(name: String, rank: String?, bedrock: Boolean): Int {
        var width = VanillaAdvances.width(name)
        rank?.let { width += TabBadge.width(it) + GAP }
        if (bedrock) width += TabGlyphs.BEDROCK_ADVANCE + GAP
        return width
    }
}
