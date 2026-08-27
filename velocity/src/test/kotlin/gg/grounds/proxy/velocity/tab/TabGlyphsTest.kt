package gg.grounds.proxy.velocity.tab

import kotlin.math.roundToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabGlyphsTest {
    @Test
    fun `logo advance is the rounded scaled width plus the bitmap gap`() {
        val scaled =
            (TabGlyphs.LOGO_TEXTURE_WIDTH.toDouble() * TabGlyphs.LOGO_HEIGHT /
                    TabGlyphs.LOGO_TEXTURE_HEIGHT)
                .roundToInt()
        assertEquals(scaled + 1, TabGlyphs.LOGO_ADVANCE)
    }
}
