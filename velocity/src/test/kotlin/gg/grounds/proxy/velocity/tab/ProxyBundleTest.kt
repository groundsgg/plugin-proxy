package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.BundleCheck
import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.ProxySystemMessage
import java.util.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProxyBundleTest {

    private val messages = Translations.forBundle(BUNDLE, javaClass.classLoader)

    @Test
    fun `every supported language has every message with the English tags`() {
        val problems =
            BundleCheck.problems(
                BUNDLE,
                javaClass.classLoader,
                ProxyMessage.entries + ProxySystemMessage.entries,
            )
        assertEquals(emptyList<String>(), problems, problems.joinToString("\n"))
    }

    @Test
    fun `errors warnings and confirmations use their system categories`() {
        assertEquals(SystemCategory.ERROR, ProxySystemMessage.REGION_UNKNOWN.category)
        assertEquals(SystemCategory.WARNING, ProxySystemMessage.REGION_ALREADY_HERE.category)
        assertEquals(SystemCategory.SUCCESS, ProxySystemMessage.REGION_MOVING.category)
        assertEquals(SystemCategory.ERROR, ProxySystemMessage.MOTD_SAVE_FAILED.category)
        assertEquals(SystemCategory.WARNING, ProxySystemMessage.MOTD_EXTRA_LINES.category)
        assertEquals(SystemCategory.SUCCESS, ProxySystemMessage.MOTD_UPDATED.category)
    }

    @Test
    fun `command arguments are rendered as literal text`() {
        val rendered =
            messages.render(
                ProxySystemMessage.REGION_UNKNOWN,
                Locale.ENGLISH,
                "region" to "<red>boom</red>",
                "available" to "eu, <click:run_command:/op>x</click>",
            )

        assertEquals(
            "Unknown region '<red>boom</red>'. Available: eu, <click:run_command:/op>x</click>",
            PlainTextComponentSerializer.plainText().serialize(rendered),
        )
    }

    private companion object {
        const val BUNDLE = "gg.grounds.proxy.messages"
    }
}
