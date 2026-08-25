package gg.grounds.proxy.velocity.tab

import gg.grounds.proxy.api.PlayerRole
import java.util.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabNameTest {
    private fun plain(locale: Locale?, role: PlayerRole?) =
        PlainTextComponentSerializer.plainText().serialize(TabName.format("Steve", locale, role))

    @Test
    fun `language and rank sit in front of the name`() {
        val text =
            plain(
                Locale.GERMANY,
                PlayerRole("admin", "Admin", prefix = "[Admin] ", colour = "#f9a49a", sortOrder = 0),
            )
        assertTrue(text.contains("DE"), text)
        assertTrue(text.contains("ADMIN"), text)
        assertTrue(text.contains("Steve"), text)
        assertFalse(text.contains("[Admin]"), text)
    }

    @Test
    fun `missing locale omits the language chip`() {
        val text = plain(null, null)
        assertFalse(text.contains("DE"), text)
        assertTrue(text.contains("Steve"), text)
    }
}
