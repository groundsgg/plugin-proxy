package gg.grounds.proxy.velocity.motd

import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory

class MotdManagerTest {

    private class FakeStore : MotdStore {
        var stored: MotdDocument? = null
        var failure: Exception? = null
        var writes = 0
        var lastWriter: String? = null

        override fun read(): MotdDocument? {
            failure?.let { throw it }
            return stored
        }

        override fun write(document: MotdDocument, updatedBy: String) {
            failure?.let { throw it }
            writes++
            lastWriter = updatedBy
            stored = document
        }

        override fun clear(deletedBy: String): Boolean {
            failure?.let { throw it }
            val had = stored != null
            stored = null
            return had
        }
    }

    private val store = FakeStore()
    private val manager =
        MotdManager(
            store = store,
            region = "nl-ams1",
            continent = "eu",
            logger = LoggerFactory.getLogger(MotdManagerTest::class.java),
        )

    /**
     * The rendered MOTD as a component, compared against a component rather than a string:
     * MiniMessage drops closing tags it does not need when serialising, and asserting on that would
     * test the serializer instead of the manager.
     */
    private fun rendered(players: Int = 0, maxPlayers: Int = 0) =
        manager.render(players, maxPlayers)

    private fun motd(miniMessage: String) = MiniMessage.miniMessage().deserialize(miniMessage)

    @Test
    fun `serves nothing until it has read something`() {
        assertNull(rendered())
        assertNull(manager.current())
        assertFalse(manager.loaded())
    }

    @Test
    fun `adopts what it reads`() {
        store.stored = MotdDocument(text = "<gold>Grounds</gold>", maxPlayers = 500)
        manager.refresh()

        assertTrue(manager.loaded())
        assertEquals(motd("<gold>Grounds</gold>"), rendered())
        assertEquals(500, manager.maxPlayers())
    }

    @Test
    fun `resolves the proxy's location into the MOTD`() {
        store.stored = MotdDocument(text = "region {{region}} zone {{localzone}}")
        manager.refresh()

        assertEquals(motd("region nl-ams1 zone eu"), rendered())
    }

    /** A MOTD that cannot change between pings is parsed once, not per server-list refresh. */
    @Test
    fun `reuses the parse of a static MOTD`() {
        store.stored = MotdDocument(text = "<gold>Grounds</gold> {{region}}")
        manager.refresh()

        assertSame(manager.render(1, 2), manager.render(3, 4))
    }

    @Test
    fun `rebuilds a MOTD that names the counts`() {
        store.stored = MotdDocument(text = "{{players}}/{{max}}")
        manager.refresh()

        assertEquals(motd("7/500"), rendered(players = 7, maxPlayers = 500))
        assertEquals(motd("8/500"), rendered(players = 8, maxPlayers = 500))
    }

    /** service-config being briefly unreachable must not empty every region's server list entry. */
    @Test
    fun `keeps the previous MOTD when the store fails`() {
        store.stored = MotdDocument(text = "<gold>Grounds</gold>")
        manager.refresh()

        store.failure = IllegalStateException("UNAVAILABLE")
        manager.refresh()

        assertEquals(motd("<gold>Grounds</gold>"), rendered())
    }

    @Test
    fun `keeps the previous MOTD when the stored one is unreadable`() {
        store.stored = MotdDocument(text = "<gold>Grounds</gold>")
        manager.refresh()

        store.failure = MotdFormatException("stored MOTD has no 'text'")
        manager.refresh()

        assertEquals(motd("<gold>Grounds</gold>"), rendered())
    }

    /** A removal elsewhere has to reach this proxy — that is not the same as a failed read. */
    @Test
    fun `drops the MOTD when the store no longer has one`() {
        store.stored = MotdDocument(text = "<gold>Grounds</gold>")
        manager.refresh()

        store.stored = null
        manager.refresh()

        assertNull(rendered())
    }

    @Test
    fun `applies a write here without waiting for the next poll`() {
        manager.set(MotdDocument(text = "<red>new</red>"), "Hendrik")

        assertEquals(motd("<red>new</red>"), rendered())
        assertEquals(1, store.writes)
        assertEquals("Hendrik", store.lastWriter)
        assertEquals("Hendrik", manager.current()?.updatedBy)
        assertTrue(manager.current()?.updatedAt?.isNotEmpty() == true)
    }

    /** A refused write must reach whoever ran the command, not be swallowed into a local change. */
    @Test
    fun `does not apply a write that was refused`() {
        store.failure = IllegalStateException("PERMISSION_DENIED")

        assertThrows<IllegalStateException> { manager.set(MotdDocument(text = "x"), "Hendrik") }
        assertNull(rendered())
    }

    @Test
    fun `clears here and in the store`() {
        manager.set(MotdDocument(text = "<red>new</red>"), "Hendrik")

        assertTrue(manager.clear("Hendrik"))
        assertNull(rendered())
        assertNull(store.stored)
        assertFalse(manager.clear("Hendrik"))
    }

    @Test
    fun `previews text that is not stored`() {
        val preview = manager.preview("{{region}} {{players}}", players = 3, maxPlayers = 9)

        assertEquals(motd("nl-ams1 3"), preview)
    }
}
