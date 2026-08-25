package gg.grounds.proxy.velocity.tab

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabBadgeTest {
    @Test
    fun `a chip contains the overlay label`() {
        val plain = PlainTextComponentSerializer.plainText().serialize(
            TabBadge.chip("DE", NamedTextColor.WHITE)
        )
        assertTrue(plain.contains("DE"), plain)
    }
}
