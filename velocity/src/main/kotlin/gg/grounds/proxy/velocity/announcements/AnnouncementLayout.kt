package gg.grounds.proxy.velocity.announcements

import gg.grounds.hud.HudMetrics
import gg.grounds.hud.HudPill
import gg.grounds.hud.HudPills
import gg.grounds.hud.HudRun
import java.util.Locale

object AnnouncementLayout {
    fun pill(entry: Announcement, locale: Locale): HudPill =
        HudPill(
            buildList {
                add(HudRun(entry.caption(locale), 0xF3F0EA))
                entry.address?.let { add(HudRun(it, 0xE7B84B, true)) }
            }
        )

    fun groups(
        settings: AnnouncementSettings,
        serverType: String,
        locale: Locale,
    ): List<List<HudPill>> {
        val groups = mutableListOf<List<HudPill>>()
        var current = mutableListOf<HudPill>()
        var width = 0
        for (entry in
            settings.announcements.filter { it.enabled && serverType in it.serverTypes }) {
            val pill = pill(entry, locale)
            val next = HudPills.width(pill)
            if (
                current.isNotEmpty() &&
                    (current.size >= 2 || width + 8 + next > HudMetrics.layout("maxWidth"))
            ) {
                groups += current
                current = mutableListOf()
                width = 0
            }
            width += (if (current.isEmpty()) 0 else 8) + next
            current += pill
        }
        if (current.isNotEmpty()) groups += current
        return groups
    }

    fun <T> groupAt(groups: List<List<T>>, epochMillis: Long): List<T>? =
        if (groups.isEmpty()) null
        else groups[Math.floorMod(Math.floorDiv(epochMillis, 10000), groups.size.toLong()).toInt()]
}
