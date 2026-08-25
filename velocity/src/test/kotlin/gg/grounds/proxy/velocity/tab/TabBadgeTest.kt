package gg.grounds.proxy.velocity.tab

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabBadgeTest {
    @Test
    fun `a chip contains the overlay label`() {
        val plain =
            PlainTextComponentSerializer.plainText()
                .serialize(TabBadge.chip("DE", NamedTextColor.WHITE))
        assertTrue(plain.contains("DE"), plain)
    }

    @Test
    fun `slice glyphs abut by cancelling the bitmap advance`() {
        val plain =
            PlainTextComponentSerializer.plainText()
                .serialize(TabBadge.chip("DE", NamedTextColor.WHITE))
        val gap = TabSpaces.of(-1)
        val left = TabGlyphs.BADGE_LEFT
        val middle = TabGlyphs.BADGE_MIDDLE
        val right = TabGlyphs.BADGE_RIGHT
        assertFalse(plain.contains("$left$middle"), plain)
        assertFalse(plain.contains("$middle$middle"), plain)
        assertFalse(plain.contains("$middle$right"), plain)
        assertTrue(plain.contains("$left$gap$middle"), plain)
        assertTrue(plain.contains("$middle$gap$middle"), plain)
        assertTrue(plain.contains("$middle$gap$right$gap"), plain)
        val badgeWidth = 16
        assertTrue(plain.contains(TabSpaces.of(-badgeWidth)), plain)
        assertFalse(plain.contains(TabSpaces.of(-(badgeWidth + 12))), plain)
    }
}
