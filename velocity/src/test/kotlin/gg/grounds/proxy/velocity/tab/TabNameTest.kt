package gg.grounds.proxy.velocity.tab

import gg.grounds.proxy.api.PlayerRole
import java.util.Locale
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `stable rank keys render their short labels and pad using that label`() {
        val text =
            PlainTextComponentSerializer.plainText()
                .serialize(
                    TabName.format(
                        "Steve",
                        Locale.GERMANY,
                        PlayerRole("developer", "Platform Engineer"),
                    )
                )

        assertTrue(text.contains("DEV"), text)
        assertFalse(text.contains("PLATFORM ENGINEER"), text)
        assertTrue(text.contains(TabSpaces.of(91)), text)
    }

    @Test
    fun `bedrock icon sits directly before the player name`() {
        val text =
            PlainTextComponentSerializer.plainText()
                .serialize(TabName.format("Steve", null, null, bedrock = true))

        assertTrue(text.contains("${TabGlyphs.BEDROCK_ICON}${TabSpaces.of(2)}Steve"), text)
    }

    @Test
    fun `bedrock icon is omitted for java players`() {
        val text =
            PlainTextComponentSerializer.plainText().serialize(TabName.format("Steve", null, null))

        assertFalse(text.contains(TabGlyphs.BEDROCK_ICON), text)
    }

    @Test
    fun `badge labels use the label font without inherited emphasis`() {
        val component = TabName.format("Steve", null, PlayerRole("admin", "Owner"))
        val label = findText(component, "ADMIN")

        assertEquals(TabGlyphs.LABEL_FONT, label.style().font())
        assertEquals(TextDecoration.State.FALSE, label.style().decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.FALSE, label.style().decoration(TextDecoration.ITALIC))
        assertEquals(ShadowColor.none(), label.style().shadowColor())
    }

    @Test
    fun `missing locale omits the language chip`() {
        val text = plain(null, null)
        assertFalse(text.contains("DE"), text)
        assertTrue(text.contains("Steve"), text)
    }

    @Test
    fun `a short name is padded to the wordmark width`() {
        val name = "A"
        val text =
            PlainTextComponentSerializer.plainText().serialize(TabName.format(name, null, null))
        val pad = TabGlyphs.HEADER_WIDTH - VanillaAdvances.width(name)
        assertTrue(pad > 0)
        assertTrue(text.contains(TabSpaces.of(pad)), text)
    }

    @Test
    fun `a name that is already wider than the wordmark is not padded`() {
        val name = "A".repeat(40)
        val text =
            PlainTextComponentSerializer.plainText().serialize(TabName.format(name, null, null))
        assertEquals(name, text)
    }

    private fun findText(
        component: net.kyori.adventure.text.Component,
        expected: String,
    ): TextComponent {
        if (component is TextComponent && component.content() == expected) return component
        return component
            .children()
            .asSequence()
            .mapNotNull { child -> runCatching { findText(child, expected) }.getOrNull() }
            .firstOrNull() ?: error("Could not find $expected")
    }
}
