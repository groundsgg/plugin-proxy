package gg.grounds.proxy.velocity.welcome

import gg.grounds.i18n.Translations
import java.util.Locale
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WelcomeMessageTest {
    private val renderer =
        WelcomeMessage(Translations.forBundle("gg.grounds.proxy.messages", javaClass.classLoader))

    private fun nodes(component: Component): List<Component> =
        listOf(component) + component.children().flatMap(::nodes)

    private fun url(component: Component) =
        (component.clickEvent()?.payload() as? ClickEvent.Payload.Text)?.value()

    private fun plain(component: Component) =
        PlainTextComponentSerializer.plainText().serialize(component)

    @Test
    fun `each welcome line is inset and shares the same visual center`() {
        val lines = plain(renderer.render("Alex", Locale.GERMAN, true, false, true)).lines()
        // Widths measured from the native Minecraft font and the active bitmap pack:
        // wordmark 129, greeting 145, social row 169, voice 278, installation 260px.
        val expectedInsets = mapOf(2 to 95, 4 to 87, 6 to 75, 8 to 21, 9 to 30)
        for ((row, expected) in expectedInsets) {
            val prefix = lines[row].takeWhile { it in '\uE010'..'\uE025' }
            val inset = prefix.sumOf { 1 shl ((it.code - 0xE010) / 2) }
            assertEquals(expected, inset, "Row $row should be centered without touching the edge")
        }
        val fallback = renderer.render("Alex", Locale.GERMAN, false, true, false)
        assertTrue(plain(fallback).lines().filter(String::isNotBlank).all { it.startsWith(" ") })
        assertFalse(plain(fallback).any { it in '\uE000'..'\uF8FF' })
    }

    @Test
    fun `links below the brand have independent click targets`() {
        val message = renderer.render("Alex", Locale.ENGLISH, true, true, true)
        val clickable = nodes(message).filter { url(it) != null }
        assertEquals(
            listOf("\uE400 Discord", "\uE401 TikTok", "\uE402 Shop"),
            clickable.map(::plain),
        )
        assertTrue(plain(message).contains("\uE400 Discord   \uE401 TikTok   \uE402 Shop"))
        assertTrue(nodes(message).any { it.font() == Key.key("grounds:welcome") })
        assertTrue(plain(message).indexOf("Grounds") < plain(message).indexOf("Discord"))
        assertEquals(
            listOf(
                "https://grnds.link/discord",
                "https://example.com/grounds/tiktok",
                "https://example.com/grounds/shop",
            ),
            nodes(message).mapNotNull { url(it) },
        )
    }

    @Test
    fun `installation link only appears for clients without the mod in every locale`() {
        for (locale in
            listOf(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.forLanguageTag("es"))) {
            val missing = renderer.render("Alex", locale, false, false)
            val installed = renderer.render("Alex", locale, false, true)
            val full = renderer.render("Alex", locale, true, false, true)
            assertTrue(
                plain(full).lines().size <= 10,
                "The full welcome must fit the default closed chat: $locale",
            )
            assertTrue(
                nodes(missing).any {
                    url(it) == "https://modrepo.de/minecraft/voicechat/wiki/installation"
                }
            )
            assertFalse(
                nodes(installed).any {
                    url(it) == "https://modrepo.de/minecraft/voicechat/wiki/installation"
                }
            )
            assertFalse(plain(missing).contains("welcome."), plain(missing))
        }
        assertTrue(
            plain(renderer.render("Alex", Locale.GERMAN, false, false)).contains("Willkommen")
        )
    }

    @Test
    fun `logo requires verified pack and player names cannot inject markup`() {
        val fallback = renderer.render("<red>Alex", Locale.ENGLISH, false, true)
        val packed = renderer.render("Alex", Locale.ENGLISH, true, true)
        assertTrue(plain(fallback).contains("<red>Alex"))
        assertFalse(plain(fallback).contains('\uE000'))
        assertFalse(plain(packed).contains("GROUNDS"), "The wordmark replaces the text heading")
        assertTrue(
            nodes(packed).any {
                it.font() == Key.key("grounds:tab") && plain(it).contains('\uE000')
            }
        )
    }

    @Test
    fun `unverified icon packs render readable labels without private glyphs`() {
        val fallback = plain(renderer.render("Alex", Locale.ENGLISH, true, true, false))
        assertTrue(fallback.contains("Discord   TikTok   Shop"))
        assertFalse(fallback.any { it in '\uE400'..'\uE402' })
        assertFalse(WelcomeGlyphs.supportedBy(setOf("old", "new"), setOf("new")))
        assertTrue(WelcomeGlyphs.supportedBy(setOf("new"), setOf("new")))
        assertFalse(WelcomeGlyphs.supportedBy(emptySet(), setOf("new")))
    }

    @Test
    fun `configured links replace the placeholders`() {
        val configured =
            WelcomeMessage(
                    Translations.forBundle("gg.grounds.proxy.messages", javaClass.classLoader),
                    WelcomeLinks(
                        tiktok = "https://www.tiktok.com/@test",
                        shop = "https://shop.example.org",
                    ),
                )
                .render("Alex", Locale.ENGLISH, false, true)
        val urls = nodes(configured).mapNotNull { url(it) }
        assertTrue("https://www.tiktok.com/@test" in urls)
        assertTrue("https://shop.example.org" in urls)
    }
}
