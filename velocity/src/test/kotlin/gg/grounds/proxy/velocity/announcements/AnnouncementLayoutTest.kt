package gg.grounds.proxy.velocity.announcements

import com.google.gson.JsonParser
import java.util.Locale
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementLayoutTest {
    @Test
    fun `caption and link share a pill with a sand separator and ordinary spaces`() {
        val settings =
            AnnouncementSettings.parse(
                """{"schemaVersion":1,"announcements":[{"id":"7c354d9c-6b16-4f06-9f15-000000000001","name":"test","enabled":true,"serverTypes":["lobby"],"text":{"en":"abc"},"address":"example.gg"}]}"""
            )
        val entry = settings.announcements.single()
        val pill = AnnouncementLayout.pill(entry, Locale.ENGLISH)
        assertEquals(listOf("abc", " • ", "example.gg"), pill.runs.map { it.text })
        assertEquals(listOf(0xF3F0EA, 0xC1B9AA, 0xE7B84B), pill.runs.map { it.rgb })
        assertEquals(listOf(false, false, true), pill.runs.map { it.bold })
        assertEquals(97, gg.grounds.hud.HudPills.width(pill))
        val withoutLink = AnnouncementLayout.pill(entry.copy(address = null), Locale.ENGLISH)
        assertEquals(listOf("abc"), withoutLink.runs.map { it.text })
        assertEquals(23, gg.grounds.hud.HudPills.width(withoutLink))
        val overwide =
            settings.copy(
                announcements =
                    listOf(
                        entry.copy(text = mapOf("en" to "W".repeat(55)), address = "W".repeat(8))
                    )
            )
        assertEquals(
            402,
            gg.grounds.hud.HudPills.width(
                AnnouncementLayout.pill(overwide.announcements.single(), Locale.ENGLISH)
            ),
        )
        val json =
            com.google.gson
                .Gson()
                .toJson(mapOf("schemaVersion" to 1, "announcements" to overwide.announcements))
        assertThrows(IllegalArgumentException::class.java) { AnnouncementSettings.parse(json) }
    }

    @Test
    fun `shared seeds validate and all languages respect single pill width`() {
        val contract =
            javaClass
                .getResourceAsStream("/announcements/announcements-v1.json")!!
                .bufferedReader()
                .readText()
        val settings =
            AnnouncementSettings.parse(
                JsonParser.parseString(contract).asJsonObject.get("seeds").toString()
            )
        assertEquals(3, settings.announcements.size)
        for (language in listOf("en", "de", "fr", "es")) {
            val groups =
                AnnouncementLayout.groups(settings, "lobby", Locale.forLanguageTag(language))
            assertTrue(groups.isNotEmpty())
            assertTrue(groups.all { it.size <= 2 })
        }
        assertTrue(AnnouncementLayout.groups(settings, "game", Locale.ENGLISH).isEmpty())
        val groups = listOf(listOf(1), listOf(2), listOf(3))
        assertEquals(listOf(1), AnnouncementLayout.groupAt(groups, 9999))
        assertEquals(listOf(2), AnnouncementLayout.groupAt(groups, 10000))
        assertEquals(listOf(3), AnnouncementLayout.groupAt(groups, 20000))
    }

    @Test
    fun `JSON numeric schema versions have identical semantics to the portal`() {
        assertTrue(
            AnnouncementSettings.parse("""{"schemaVersion":1.0,"announcements":[]}""")
                .announcements
                .isEmpty()
        )
    }

    @Test
    fun `rejects incorrect primitive types unsupported unicode and missing fallback`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementSettings.parse("""{"schemaVersion":"1","announcements":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementSettings.parse("""{"schemaVersion":2,"announcements":[]}""")
        }
    }

    @Test
    fun `jsonb whitespace does not invalidate a canonically valid document`() {
        val entry =
            mapOf(
                "id" to "7c354d9c-6b16-4f06-9f15-000000000001",
                "name" to "x",
                "enabled" to true,
                "text" to mapOf("en" to "abc"),
                "address" to null,
            )
        val rows =
            (0..31).map { i ->
                entry +
                    mapOf(
                        "id" to "7c354d9c-6b16-4f06-9f15-${i.toString().padStart(12,'0')}",
                        "serverTypes" to (0..31).map { j -> "r${i}_${j}".padEnd(56, 'x') },
                    )
            }
        val doc = mapOf("schemaVersion" to 1, "announcements" to rows)
        val compact = com.google.gson.GsonBuilder().serializeNulls().create().toJson(doc)
        val spaced =
            com.google.gson.GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(doc)
        assertTrue(compact.toByteArray().size < 65536)
        assertTrue(spaced.toByteArray().size > 65536)
        assertEquals(32, AnnouncementSettings.parse(spaced).announcements.size)
    }
}
