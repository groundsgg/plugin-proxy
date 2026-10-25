package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.Palette
import gg.grounds.proxy.api.PlayerDisplayNames
import gg.grounds.proxy.api.PlayerRole
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/** What the tab list shows about a player's voice; Java viewers only. */
enum class TabVoice {
    ON,
    /** Switched off in the mod. */
    OFF,
    /** No voice at all, shown only to the player's own party: voice cannot reach them. */
    MISSING,
}

object TabName {
    private const val GAP = 2

    fun format(
        name: String,
        role: PlayerRole?,
        bedrock: Boolean = false,
        bedrockViewer: Boolean = false,
        partyMember: Boolean = false,
        /** Null draws no voice icon. */
        voice: TabVoice? = null,
    ): Component {
        val displayName = PlayerDisplayNames.displayName(name, bedrock)
        val colour = role?.colour?.let(TextColor::fromHexString) ?: Palette.TEXT
        val rank = role?.let(TabRankLabels::resolve)
        val nameText =
            Component.text(displayName, colour).decoration(TextDecoration.BOLD, partyMember)
        val row = Component.text()
        if (bedrockViewer) {
            rank?.let { row.append(BedrockRankBadges.chip(it, colour)).append(Component.space()) }
            if (bedrock)
                row.append(Component.text('\uE500', NamedTextColor.WHITE)).append(Component.space())
            return row.append(nameText).build()
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
        voice?.let {
            val icon =
                when (it) {
                    TabVoice.ON -> Component.text(TabGlyphs.VOICE_ON, Palette.SUCCESS)
                    TabVoice.OFF -> Component.text(TabGlyphs.VOICE_OFF, Palette.TEXT_FAINT)
                    TabVoice.MISSING -> Component.text(TabGlyphs.VOICE_OFF, Palette.DANGER)
                }
            row.append(icon.font(TabGlyphs.FONT))
            row.append(Component.text(TabSpaces.of(GAP)).font(TabGlyphs.FONT))
        }
        row.append(nameText)
        val pad =
            TabGlyphs.HEADER_WIDTH -
                rowWidth(displayName, rank, bedrock, partyMember, voice != null)
        if (pad > 0) {
            row.append(Component.text(TabSpaces.of(pad)).font(TabGlyphs.FONT))
        }
        return row.build()
    }

    private fun rowWidth(
        name: String,
        rank: String?,
        bedrock: Boolean,
        bold: Boolean,
        voice: Boolean,
    ): Int {
        // Bold draws every glyph one pixel wider.
        var width = VanillaAdvances.width(name) + if (bold) name.length else 0
        rank?.let { width += TabBadge.width(it) + GAP }
        if (bedrock) width += TabGlyphs.BEDROCK_ADVANCE + GAP
        if (voice) width += TabGlyphs.ICON_ADVANCE + GAP
        return width
    }
}
