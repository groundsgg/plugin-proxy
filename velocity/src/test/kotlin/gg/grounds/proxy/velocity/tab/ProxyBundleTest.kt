package gg.grounds.proxy.velocity.tab

import gg.grounds.i18n.BundleCheck
import gg.grounds.i18n.SystemCategory
import gg.grounds.i18n.SystemLevel
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
    fun `proxy feedback keeps general context and uses semantic levels`() {
        assertEquals(SystemCategory.GENERAL, ProxySystemMessage.REGION_UNKNOWN.category)
        assertEquals(SystemLevel.ERROR, ProxySystemMessage.REGION_UNKNOWN.level)
        assertEquals(SystemLevel.WARNING, ProxySystemMessage.REGION_ALREADY_HERE.level)
        assertEquals(SystemLevel.SUCCESS, ProxySystemMessage.REGION_MOVING.level)
        assertEquals(SystemLevel.ERROR, ProxySystemMessage.MOTD_SAVE_FAILED.level)
        assertEquals(SystemLevel.WARNING, ProxySystemMessage.MOTD_EXTRA_LINES.level)
        assertEquals(SystemLevel.SUCCESS, ProxySystemMessage.MOTD_UPDATED.level)
        assertEquals(SystemCategory.PARTY, ProxySystemMessage.PARTY_TRANSFER_FOLLOWING.category)
        assertEquals(SystemLevel.INFO, ProxySystemMessage.PARTY_TRANSFER_FOLLOWING.level)
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
