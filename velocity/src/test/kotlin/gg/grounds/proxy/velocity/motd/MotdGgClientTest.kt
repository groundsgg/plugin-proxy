package gg.grounds.proxy.velocity.motd

import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MotdGgClientTest {

    private val client = MotdGgClient(userAgent = "test")

    @Test
    fun `accepts every form of a motd gg link`() {
        assertEquals("AbC123", client.parseId("AbC123"))
        assertEquals("AbC123", client.parseId("  AbC123  "))
        assertEquals("AbC123", client.parseId("motd.gg/AbC123"))
        assertEquals("AbC123", client.parseId("https://motd.gg/AbC123"))
        assertEquals("AbC123", client.parseId("http://motd.gg/AbC123"))
        assertEquals("AbC123", client.parseId("https://motd.gg/AbC123.json"))
    }

    @Test
    fun `rejects what is not an id`() {
        assertNull(client.parseId(""))
        assertNull(client.parseId("https://example.com/AbC123"))
        assertNull(client.parseId("example.com/AbC123"))
        assertNull(client.parseId("has spaces"))
    }

    /**
     * MiniMessage omits a closing tag it does not need, and keeps the line break as the newline
     * character it already was rather than re-encoding it as `<newline>`. Both forms mean the same
     * thing to the parser; this is what actually gets stored.
     */
    @Test
    fun `converts the section codes motd gg stores into MiniMessage`() {
        val imported = client.parse("AbC123", """{"id":"AbC123","text":"§cRed\n§aGreen"}""")

        assertEquals("<red>Red\n</red><green>Green", imported.text)
        assertEquals("AbC123", imported.id)
    }

    /**
     * Compared as components: whether the serializer writes the hex digits upper or lower case is
     * its business and has changed between adventure versions, and MiniMessage reads either.
     */
    @Test
    fun `keeps hex colours`() {
        val hex = "§x§f§f§0§0§a§aWarm"

        val imported = client.parse("AbC123", """{"text":"$hex"}""")

        assertEquals(
            MiniMessage.miniMessage().deserialize("<#ff00aa>Warm"),
            MiniMessage.miniMessage().deserialize(imported.text),
        )
    }

    @Test
    fun `reports a server icon it did not import`() {
        assertTrue(
            client.parse("x", """{"text":"hi","favicon":"data:image/png;base64,AA"}""").hasFavicon
        )
        assertFalse(client.parse("x", """{"text":"hi"}""").hasFavicon)
    }

    @Test
    fun `carries the design's name through`() {
        assertEquals("Summer", client.parse("x", """{"text":"hi","name":"Summer"}""").name)
    }

    /**
     * motd.gg answers 200 with its editor page for an id it does not know, so an almost-right id
     * arrives here as HTML. Saying "unknown id" beats a JSON parse error nobody can act on.
     */
    @Test
    fun `treats the editor page as an unknown id`() {
        val ex =
            assertThrows<MotdFormatException> { client.parse("AbC124", "<!DOCTYPE html>\n<html>") }
        assertTrue(ex.message!!.contains("AbC124"), ex.message)
    }

    @Test
    fun `refuses a response that is not a MOTD`() {
        assertThrows<MotdFormatException> { client.parse("x", "{ not json") }
        assertThrows<MotdFormatException> { client.parse("x", "[]") }
        assertThrows<MotdFormatException> { client.parse("x", """{"name":"no text"}""") }
    }
}
