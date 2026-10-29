package gg.grounds.proxy.velocity.command

import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.ProxySystemMessage
import java.util.Locale
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class InteractiveCommandMessageTest {

    private val messages =
        Translations.forBundle("gg.grounds.proxy.messages", javaClass.classLoader)

    @Test
    fun `region entries run their exact region command in every supported locale`() {
        for (locale in locales) {
            val action =
                interactiveCommand(
                    messages.render(ProxySystemMessage.REGION_ENTRY, locale, "region" to "eu"),
                    CommandAction.run("/region eu"),
                    "/region eu",
                )

            assertEquals(
                ClickEvent.runCommand("/region eu"),
                action.clickEvent(),
                locale.toLanguageTag(),
            )
            assertEquals("/region eu", hoverText(action), locale.toLanguageTag())
            assertFalse(plain(action).contains("/region"), locale.toLanguageTag())
        }
    }

    @Test
    fun `proxy entries run their exact network-wide proxy command`() {
        val action =
            interactiveCommand(
                messages.render(
                    ProxySystemMessage.PROXY_ENTRY,
                    Locale.ENGLISH,
                    "proxy" to "eu-2",
                    "region" to "eu",
                    "count" to 12,
                ),
                CommandAction.run("/proxy eu-2"),
                "/proxy eu-2",
            )

        assertEquals(ClickEvent.runCommand("/proxy eu-2"), action.clickEvent())
        assertEquals("/proxy eu-2", hoverText(action))
    }

    @Test
    fun `motd help runs complete read-only commands and suggests edits`() {
        val show = motdHelpAction("/motd")
        val preview = motdHelpAction("/motd preview [MiniMessage]")
        val set = motdHelpAction("/motd set <MiniMessage>")
        val reset = motdHelpAction("/motd reset")

        assertEquals(CommandAction.run("/motd"), show)
        assertEquals(CommandAction.run("/motd preview"), preview)
        assertEquals(CommandAction.suggest("/motd set "), set)
        assertEquals(CommandAction.suggest("/motd reset"), reset)
    }

    private fun hoverText(component: net.kyori.adventure.text.Component): String =
        PlainTextComponentSerializer.plainText()
            .serialize(component.hoverEvent()!!.value() as net.kyori.adventure.text.Component)

    private fun plain(component: net.kyori.adventure.text.Component): String =
        PlainTextComponentSerializer.plainText().serialize(component)

    private companion object {
        val locales =
            listOf(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.forLanguageTag("es"))
    }
}
