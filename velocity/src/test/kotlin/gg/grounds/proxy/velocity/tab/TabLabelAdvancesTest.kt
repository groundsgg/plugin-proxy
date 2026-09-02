package gg.grounds.proxy.velocity.tab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabLabelAdvancesTest {
    @Test
    fun `label glyphs use their tab font advances`() {
        assertEquals(28, TabLabelAdvances.width("ADMIN"))
        assertEquals(44, TabLabelAdvances.width("I I!:-+_?/"))
    }
}
