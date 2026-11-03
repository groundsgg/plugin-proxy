package gg.grounds.proxy.velocity.announcements

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementSynchronizerTest {
    @Test
    fun `missed NATS heals on next poll and timeout preserves last good`() {
        val empty = AnnouncementSettings(emptyList())
        var fail = false
        var rev: Long? = null
        val applied = mutableListOf<AnnouncementSettings?>()
        val sync =
            AnnouncementSynchronizer(
                { known ->
                    rev = known
                    if (fail) error("unavailable")
                    AnnouncementSnapshot.Changed(8, empty)
                },
                { applied += it },
                {},
            )
        sync.refresh()
        assertEquals(listOf(empty), applied)
        assertNull(rev)
        fail = true
        sync.refresh()
        assertEquals(1, applied.size)
        fail = false
        sync.refresh()
        assertEquals(8L, rev)
        sync.close()
        sync.refresh()
        assertEquals(2, applied.size)
    }

    @Test
    fun `missing clears while unchanged leaves current snapshot alone`() {
        var response: AnnouncementSnapshot = AnnouncementSnapshot.Changed(1, null)
        var calls = 0
        val sync = AnnouncementSynchronizer({ response }, { calls++ }, {})
        sync.refresh()
        assertEquals(1, calls)
        response = AnnouncementSnapshot.Unchanged
        sync.refresh()
        assertEquals(1, calls)
    }
}
