package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RankWarmupTest {
    @Test
    fun `initial remote badge survives a cold lookup but a missing rank never persists indefinitely`() {
        var now = 0L
        val warmup = RankWarmup { now }
        val id = UUID.randomUUID()
        assertTrue(warmup.keep(id, true, false))
        assertFalse(warmup.keep(id, false, false))
        now = 2_000_000_001L
        assertFalse(warmup.keep(id, true, false))
        warmup.clear(id)
        assertFalse(warmup.keep(id, true, true))
        assertFalse(warmup.keep(id, false, false))
    }
}
