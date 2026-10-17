package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RankWarmupTest {
    @Test
    fun `capacity cleanup cannot restart an expired badge still observed by another viewer`() {
        var now = 0L
        val warmup = RankWarmup { now }
        val id = UUID.randomUUID()
        assertTrue(warmup.keep(id, true, false))
        repeat(511) { assertTrue(warmup.keep(UUID.randomUUID(), true, false)) }
        now = 300_000_000_001L
        assertFalse(warmup.keep(id, true, false))
        assertTrue(warmup.keep(UUID.randomUUID(), true, false))
        assertFalse(warmup.keep(id, true, false))
    }

    @Test
    fun `initial remote badge survives a cold lookup but a missing rank never persists indefinitely`() {
        var now = 0L
        val warmup = RankWarmup { now }
        val id = UUID.randomUUID()
        assertTrue(warmup.keep(id, true, false))
        assertFalse(warmup.keep(id, false, false))
        // A cold remote queue can outlive the next five-second tab refresh.
        now = 5_000_000_001L
        assertTrue(warmup.keep(id, true, false))
        now = 299_000_000_000L
        assertTrue(warmup.keep(id, true, false))
        now = 300_000_000_001L
        assertFalse(warmup.keep(id, true, false))
        warmup.clear(id)
        assertFalse(warmup.keep(id, true, true))
        assertFalse(warmup.keep(id, false, false))
    }
}
