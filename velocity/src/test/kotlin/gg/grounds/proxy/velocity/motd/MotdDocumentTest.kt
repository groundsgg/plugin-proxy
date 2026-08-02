package gg.grounds.proxy.velocity.motd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MotdDocumentTest {

    @Test
    fun `survives a round trip`() {
        val document =
            MotdDocument(
                text = "<gold>Grounds</gold><newline><gray>{{region}}</gray>",
                maxPlayers = 500,
                source = "motd.gg/AbC123",
                updatedBy = "Hendrik",
                updatedAt = "2026-08-02T10:00:00Z",
            )
        assertEquals(document, MotdDocument.fromJson(document.toJson()))
    }

    /** An unset cap is absent, not null — the dashboard reads this document too. */
    @Test
    fun `omits the fields that are not set`() {
        assertEquals("""{"text":"hi"}""", MotdDocument(text = "hi").toJson())
    }

    @Test
    fun `reads a document that only has text`() {
        val document = MotdDocument.fromJson("""{"text":"hi"}""")
        assertEquals("hi", document.text)
        assertNull(document.maxPlayers)
        assertNull(document.updatedBy)
    }

    /** Whatever the dashboard adds later must not stop this version from reading the MOTD. */
    @Test
    fun `ignores fields it does not know`() {
        assertEquals("hi", MotdDocument.fromJson("""{"text":"hi","favicon":"x"}""").text)
    }

    @Test
    fun `refuses what is not a MOTD`() {
        assertThrows<MotdFormatException> { MotdDocument.fromJson("not json") }
        assertThrows<MotdFormatException> { MotdDocument.fromJson("[]") }
        assertThrows<MotdFormatException> { MotdDocument.fromJson("""{"maxPlayers":10}""") }
    }
}
