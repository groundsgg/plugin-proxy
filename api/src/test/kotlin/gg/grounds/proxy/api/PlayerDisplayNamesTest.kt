package gg.grounds.proxy.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerDisplayNamesTest {
    @Test
    fun `only verified bedrock loses one leading technical dot`() {
        assertEquals("Alex", PlayerDisplayNames.displayName(".Alex", true))
        assertEquals(".Alex", PlayerDisplayNames.displayName(".Alex", false))
        assertEquals(".Alex", PlayerDisplayNames.displayName(".Alex", null))
        assertEquals(".Alex", PlayerDisplayNames.displayName("..Alex", true))
    }
}
