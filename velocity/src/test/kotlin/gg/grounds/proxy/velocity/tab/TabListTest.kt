package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.Palette
import gg.grounds.i18n.Translations
import java.util.Locale
import java.util.ResourceBundle
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val BUNDLE = "gg.grounds.proxy.messages"

class TabListTest {

    private val messages = Translations.forBundle(BUNDLE, javaClass.classLoader)

    private fun plain(component: Component) =
        PlainTextComponentSerializer.plainText().serialize(component)

    private fun colourOf(component: Component) =
        component.color() ?: component.children().firstNotNullOfOrNull { it.color() }

    private fun footer(server: String, ping: Long) =
        plain(
            messages.render(
                ProxyMessage.TAB_FOOTER,
                Locale.ENGLISH,
                "server" to server,
                "ping" to TabList.ping(ping),
                "online" to "42",
            )
        )

    @Test
    fun `every message exists in the bundle`() {
        val bundle = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH, javaClass.classLoader)
        val missing = ProxyMessage.entries.filterNot { bundle.containsKey(it.id) }
        assertEquals(emptyList<ProxyMessage>(), missing)
    }

    @Test
    fun `the header is the wordmark glyph`() {
        val header = plain(messages.render(ProxyMessage.TAB_HEADER, Locale.ENGLISH))
        assertTrue(header.contains("\uE000"), header)
        assertFalse(header.contains("Grounds Network"), header)
    }

    @Test
    fun `the header pads the wordmark on every side`() {
        val header = plain(messages.render(ProxyMessage.TAB_HEADER, Locale.ENGLISH))
        val side = TabSpaces.of(TabGlyphs.LOGO_PAD)
        assertTrue(header.contains("$side${TabGlyphs.LOGO}$side"), header)
        val lines = header.lines()
        assertTrue(lines.size >= 6, header)
        assertTrue(lines.first().isEmpty(), header)
        assertTrue(lines.last().isEmpty(), header)
    }

    @Test
    fun `the header is not prefixed - the wordmark already says whose network this is`() {
        val header = plain(messages.render(ProxyMessage.TAB_HEADER, Locale.ENGLISH))
        assertFalse(header.contains("[Grounds]"), header)
    }

    @Test
    fun `the footer contains only the server type and domain in every locale`() {
        for (locale in
            listOf(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.forLanguageTag("es"))) {
            val rendered =
                plain(messages.render(ProxyMessage.TAB_FOOTER, locale, "server" to "Lobby"))
            assertEquals(listOf("Lobby", "grounds.gg"), rendered.trim().lines())
        }
    }

    /** Hard-coding the year is how a footer ends up saying 2026 in 2029. */
    @Test
    fun `the year is not baked into the bundle`() {
        val bundle = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH, javaClass.classLoader)
        assertFalse(bundle.getString(ProxyMessage.TAB_FOOTER.id).contains(Regex("\\b20\\d\\d\\b")))
    }

    @Test
    fun `an unknown region does not print null`() {
        val rendered = footer("—", 24)
        assertFalse(rendered.contains("null"), rendered)
    }

    @Test
    fun `the ping is coloured by how bad it is`() {
        assertEquals(Palette.SUCCESS, colourOf(TabList.ping(24)))
        assertEquals(Palette.WARNING, colourOf(TabList.ping(150)))
        assertEquals(Palette.DANGER, colourOf(TabList.ping(400)))
    }

    @Test
    fun `an unmeasured ping is a dash, not a fabricated zero`() {
        val unmeasured = TabList.ping(-1)
        assertEquals("—", plain(unmeasured))
        assertEquals(Palette.TEXT_FAINT, colourOf(unmeasured))
    }

    @Test
    fun `the thresholds are boundaries, not ranges that overlap`() {
        assertEquals(Palette.SUCCESS, colourOf(TabList.ping(TabList.GOOD_PING_MS - 1)))
        assertEquals(Palette.WARNING, colourOf(TabList.ping(TabList.GOOD_PING_MS)))
        assertEquals(Palette.WARNING, colourOf(TabList.ping(TabList.FAIR_PING_MS - 1)))
        assertEquals(Palette.DANGER, colourOf(TabList.ping(TabList.FAIR_PING_MS)))
    }

    @Test
    fun `no bundle line reaches for a legacy colour`() {
        val legacy = Regex("<(dark_)?(red|green|blue|aqua|purple|yellow|gray|grey|white|black)>")
        val bundle = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH, javaClass.classLoader)
        for (key in bundle.keySet()) {
            // The wordmark is a bitmap glyph MiniMessage tints white; it is not body copy.
            if (key == ProxyMessage.TAB_HEADER.id) continue
            assertFalse(
                legacy.containsMatchIn(bundle.getString(key)),
                "$key uses a legacy colour instead of a semantic token",
            )
        }
    }
}
