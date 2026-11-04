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
    fun `links below the brand have independent click targets`() {
        val message = renderer.render("Alex", Locale.ENGLISH, false, true)
        val clickable = nodes(message).filter { url(it) != null }
        assertEquals(listOf("🎮 Discord", "🎵 TikTok", "🛒 Shop"), clickable.map(::plain))
        assertTrue(plain(message).contains("🎮 Discord\n🎵 TikTok\n🛒 Shop"))
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
            val full = renderer.render("Alex", locale, true, false)
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
