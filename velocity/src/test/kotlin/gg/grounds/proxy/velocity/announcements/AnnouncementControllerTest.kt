package gg.grounds.proxy.velocity.announcements

import java.util.Locale
import java.util.UUID
import net.kyori.adventure.bossbar.BossBar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementControllerTest {
    private class Viewer : AnnouncementViewer {
        override val playerId = UUID.randomUUID()
        override val session = Any()
        override var serverType: String? = "lobby"
        override var locale = Locale.GERMAN
        override var hudReady = true
        override var connected = true
        val visible = mutableSetOf<BossBar>()
        var shown = 0

        override fun show(bar: BossBar) {
            visible += bar
            shown++
        }

        override fun hide(bar: BossBar) {
            visible -= bar
        }
    }

    private fun settings(): AnnouncementSettings =
        AnnouncementSettings.parse(
            """{"schemaVersion":1,"announcements":[{"id":"7c354d9c-6b16-4f06-9f15-000000000001","name":"test","enabled":true,"serverTypes":["lobby"],"text":{"en":"abc"},"address":null}]}"""
        )

    @Test
    fun `lobby game lobby switch filters immediately and keeps only one bar`() {
        val viewer = Viewer()
        val controller = AnnouncementController({ listOf(viewer) }, { settings() })
        controller.refreshAll()
        assertEquals(1, viewer.visible.size)
        controller.refreshAll()
        assertEquals(1, viewer.shown)
        viewer.serverType = "game"
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
        viewer.serverType = "lobby"
        controller.refreshAll()
        assertEquals(1, viewer.visible.size)
        viewer.hudReady = false
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
        viewer.hudReady = true
        controller.close()
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
    }

    @Test
    fun `two proxies only touch local players and disconnected sessions never revive`() {
        val a = Viewer()
        val b = Viewer()
        val ca = AnnouncementController({ listOf(a) }, { settings() })
        val cb = AnnouncementController({ listOf(b) }, { settings() })
        ca.refreshAll()
        cb.refreshAll()
        assertEquals(1, a.visible.size)
        assertEquals(1, b.visible.size)
        a.connected = false
        ca.refreshAll()
        assertTrue(a.visible.isEmpty())
        assertEquals(1, b.visible.size)
        ca.close()
        cb.close()
    }

    @Test
    fun `empty and missing snapshots remove bars and unknown backend stays empty`() {
        val a = Viewer()
        var config: AnnouncementSettings? = settings()
        val controller = AnnouncementController({ listOf(a) }, { config })
        controller.refreshAll()
        config = AnnouncementSettings.parse("""{"schemaVersion":1,"announcements":[]}""")
        controller.refreshAll()
        assertTrue(a.visible.isEmpty())
        config = settings()
        a.serverType = null
        controller.refreshAll()
        assertTrue(a.visible.isEmpty())
        config = null
        controller.refreshAll()
        assertTrue(a.visible.isEmpty())
        controller.close()
    }

    @Test
    fun `refresh stays suspended until the matching backend transition finishes`() {
        val viewer = Viewer()
        val source = Any()
        val attempt = Any()
        val controller = AnnouncementController({ listOf(viewer) }, { settings() })
        controller.refreshAll()
        assertEquals(1, viewer.visible.size)
        controller.beginSwitch(viewer.playerId, viewer.session, attempt, source) { "game" }
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
        controller.completedSwitch(viewer.playerId, viewer.session, "wrong-target", Any())
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
        viewer.serverType = "game"
        controller.completedSwitch(viewer.playerId, viewer.session, "game", Any())
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
        viewer.serverType = "lobby"
        controller.refreshAll()
        assertEquals(1, viewer.visible.size)
        controller.beginSwitch(viewer.playerId, viewer.session, Any(), source) { "game" }
        controller.failedSwitch(viewer.playerId, viewer.session, "game", Any(), true)
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
        controller.failedSwitch(viewer.playerId, viewer.session, "game", source, true)
        controller.refreshAll()
        assertEquals(1, viewer.visible.size)
        controller.close()
    }

    @Test
    fun `later denial does not leave a lobby session suspended`() {
        val viewer = Viewer()
        val controller = AnnouncementController({ listOf(viewer) }, { settings() })
        var target: String? = "game"
        controller.beginSwitch(viewer.playerId, viewer.session, Any(), Any()) { target }
        target = null
        controller.refreshAll()
        assertEquals(1, viewer.visible.size)
        controller.close()
    }

    @Test
    fun `denying a later attempt cannot release an earlier pending switch`() {
        val viewer = Viewer()
        val controller = AnnouncementController({ listOf(viewer) }, { settings() })
        controller.beginSwitch(viewer.playerId, viewer.session, Any(), Any()) { "game" }
        var second: String? = "match"
        controller.beginSwitch(viewer.playerId, viewer.session, Any(), Any()) { second }
        second = null
        controller.refreshAll()
        assertTrue(viewer.visible.isEmpty())
        controller.close()
    }
}
