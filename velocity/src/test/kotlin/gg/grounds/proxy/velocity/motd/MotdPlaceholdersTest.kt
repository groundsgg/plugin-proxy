package gg.grounds.proxy.velocity.motd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MotdPlaceholdersTest {

    private val context =
        MotdPlaceholders.Context(
            region = "nl-ams1",
            continent = "eu",
            players = 42,
            maxPlayers = 500,
        )

    @Test
    fun `resolves the proxy's own location`() {
        assertEquals(
            "nl-ams1 / eu / eu",
            MotdPlaceholders.render("{{region}} / {{localzone}} / {{continent}}", context),
        )
    }

    @Test
    fun `resolves the counts this ping reports`() {
        assertEquals("42 of 500", MotdPlaceholders.render("{{players}} of {{max}}", context))
    }

    @Test
    fun `is case and whitespace insensitive`() {
        assertEquals("nl-ams1", MotdPlaceholders.render("{{ REGION }}", context))
    }

    /** A typo in a MOTD has to be visible, not silently swallowed into a gap. */
    @Test
    fun `leaves an unknown token standing`() {
        assertEquals("{{regoin}}", MotdPlaceholders.render("{{regoin}}", context))
    }

    /** A proxy with no REGION should show a gap, never the word "null". */
    @Test
    fun `renders a known but unset value as nothing`() {
        val unset = context.copy(region = null, continent = null)
        assertEquals("[]", MotdPlaceholders.render("[{{region}}{{localzone}}]", unset))
    }

    /**
     * Substitution happens before the MiniMessage parse, so a value that contained a tag would be
     * parsed as one. Today none can; the escape is what keeps that true.
     */
    @Test
    fun `escapes tags in the substituted value`() {
        val hostile = context.copy(region = "<red>oops</red>")

        assertEquals("""\<red>oops\</red>""", MotdPlaceholders.render("{{region}}", hostile))
    }

    @Test
    fun `only the counts make a template dynamic`() {
        assertTrue(MotdPlaceholders.isDynamic("hi {{players}}"))
        assertTrue(MotdPlaceholders.isDynamic("hi {{max}}"))
        assertFalse(MotdPlaceholders.isDynamic("hi {{region}} {{localzone}}"))
        assertFalse(MotdPlaceholders.isDynamic("no placeholders here"))
        assertFalse(MotdPlaceholders.isDynamic("{{unknown}}"))
    }
}
