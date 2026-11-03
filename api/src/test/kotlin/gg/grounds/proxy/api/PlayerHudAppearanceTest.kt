package gg.grounds.proxy.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse

class PlayerHudAppearanceTest {
    @Test fun `unverified appearance is disabled by default`() { assertFalse(PlayerHudAppearance().ready) }
}
