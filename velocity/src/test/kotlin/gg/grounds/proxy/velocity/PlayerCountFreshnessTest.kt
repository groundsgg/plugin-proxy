package gg.grounds.proxy.velocity

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerCountFreshnessTest {

    @Test
    fun `is fresh right after it arrives`() {
        assertTrue(isPlayerCountFresh(receivedAtNanos = 0L, nowNanos = 0L))
    }

    @Test
    fun `is fresh just under the staleness window`() {
        val nowNanos = Duration.ofSeconds(30).minusNanos(1).toNanos()
        assertTrue(isPlayerCountFresh(receivedAtNanos = 0L, nowNanos = nowNanos))
    }

    @Test
    fun `goes stale once the window has passed`() {
        val nowNanos = Duration.ofSeconds(30).plusNanos(1).toNanos()
        assertFalse(isPlayerCountFresh(receivedAtNanos = 0L, nowNanos = nowNanos))
    }
}
