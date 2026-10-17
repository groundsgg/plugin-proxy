package gg.grounds.proxy.velocity.tab

import gg.grounds.proxy.api.PlayerRole
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabNameTest {
    private fun plain(role: PlayerRole?) =
        PlainTextComponentSerializer.plainText().serialize(TabName.format("Steve", role))

    @Test
    fun `bedrock viewers receive native badge and edition glyphs without Java spacing`() {
        val text =
            PlainTextComponentSerializer.plainText()
                .serialize(
                    TabName.format(
                        "Steve",
                        PlayerRole("administrator", "Administrator", colour = "#C9826B"),
                        bedrock = true,
                        bedrockViewer = true,
                    )
                )
        assertEquals("\uE600 \uE500 Steve", text)
    }

    @Test
    fun `rank sits in front of the name and no language chip is drawn`() {
        val text =
            plain(
                PlayerRole("admin", "Admin", prefix = "[Admin] ", colour = "#f9a49a", sortOrder = 0)
            )
        assertTrue(text.indexOf("ADMIN") in 0 until text.indexOf("Steve"), text)
        assertFalse(text.contains("[Admin]"), text)
        assertFalse(text.contains("DE") || text.contains("EN"), text)
    }

    @Test
    fun `stable rank keys render their short labels and pad using that label`() {
        val text =
            PlainTextComponentSerializer.plainText()
                .serialize(TabName.format("Steve", PlayerRole("developer", "Platform Engineer")))
        val pad =
            TabGlyphs.HEADER_WIDTH - VanillaAdvances.width("Steve") - TabBadge.width("DEV") - 2

        assertTrue(text.contains("DEV"), text)
        assertFalse(text.contains("PLATFORM ENGINEER"), text)
        assertTrue(text.endsWith("Steve${TabSpaces.of(pad)}"), text)
    }

    @Test
    fun `bedrock icon sits directly before the player name`() {
        val text =
            PlainTextComponentSerializer.plainText()
                .serialize(TabName.format("Steve", null, bedrock = true))

        assertTrue(text.contains("${TabGlyphs.BEDROCK_ICON}${TabSpaces.of(2)}Steve"), text)
    }

    @Test
    fun `bedrock icon is omitted for java players`() {
        val text = PlainTextComponentSerializer.plainText().serialize(TabName.format("Steve", null))

        assertFalse(text.contains(TabGlyphs.BEDROCK_ICON), text)
    }

    @Test
    fun `badge labels use the label font without inherited emphasis`() {
        val component = TabName.format("Steve", PlayerRole("admin", "Owner"))
        val label = findText(component, "ADMIN")

        assertEquals(TabGlyphs.LABEL_FONT, label.style().font())
        assertEquals(TextDecoration.State.FALSE, label.style().decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.FALSE, label.style().decoration(TextDecoration.ITALIC))
        assertEquals(ShadowColor.none(), label.style().shadowColor())
    }

    @Test
    fun `a short name is padded to the wordmark width`() {
        val name = "A"
        val text = PlainTextComponentSerializer.plainText().serialize(TabName.format(name, null))
        val pad = TabGlyphs.HEADER_WIDTH - VanillaAdvances.width(name)
        assertTrue(pad > 0)
        assertTrue(text.contains(TabSpaces.of(pad)), text)
    }

    @Test
    fun `a name that is already wider than the wordmark is not padded`() {
        val name = "A".repeat(40)
        val text = PlainTextComponentSerializer.plainText().serialize(TabName.format(name, null))
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
