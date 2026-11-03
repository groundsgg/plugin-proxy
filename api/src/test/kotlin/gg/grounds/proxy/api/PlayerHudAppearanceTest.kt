package gg.grounds.proxy.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PlayerHudAppearanceTest {
    @Test
    fun `unverified appearance is disabled by default`() {
        assertFalse(PlayerHudAppearance().ready)
    }
}
