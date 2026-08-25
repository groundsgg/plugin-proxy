package gg.grounds.proxy.velocity.tab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VanillaAdvancesTest {
    @Test
    fun `DE is two six-wide letters plus the gap already in each advance`() {
        assertEquals(12, VanillaAdvances.width("DE"))
    }

    @Test
    fun `ADMIN uses the narrow I`() {
        assertEquals(6 + 6 + 6 + 4 + 6, VanillaAdvances.width("ADMIN"))
    }
}
