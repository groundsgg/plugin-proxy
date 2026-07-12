package gg.grounds.proxy.velocity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionCacheTest {

    @Test
    fun `serves a repeated prefix from cache instead of asking again`() {
        // The point of the cache: tab-complete fires per keystroke, so the same prefix is asked for
        // many times in a row while the player is still typing.
        var loads = 0
        var now = 0L
        val cache = SuggestionCache(ttlMillis = 2_000, clock = { now })

        repeat(5) {
            cache.get("dah") {
                loads++
                listOf("dahendriik")
            }
        }

        assertEquals(1, loads)
        assertEquals(listOf("dahendriik"), cache.get("dah") { emptyList() })
    }

    @Test
    fun `is case-insensitive on the prefix`() {
        var loads = 0
        val cache = SuggestionCache(clock = { 0 })

        cache.get("Dah") {
            loads++
            listOf("dahendriik")
        }
        cache.get("dAH") {
            loads++
            listOf("dahendriik")
        }

        assertEquals(1, loads)
    }

    @Test
    fun `asks again once the entry is stale`() {
        var loads = 0
        var now = 0L
        val cache = SuggestionCache(ttlMillis = 2_000, clock = { now })

        cache.get("dah") {
            loads++
            listOf("a")
        }
        now = 1_999
        cache.get("dah") {
            loads++
            listOf("a")
        }
        now = 2_000
        cache.get("dah") {
            loads++
            listOf("a")
        }

        assertEquals(2, loads)
    }

    @Test
    fun `evicts stale entries instead of growing forever`() {
        // Every distinct prefix a player types is a key.
        var now = 0L
        val cache = SuggestionCache(ttlMillis = 100, maxEntries = 4, clock = { now })

        repeat(5) { i -> cache.get("p$i") { listOf("x") } }
        assertTrue(cache.size() > 0)

        now = 1_000
        repeat(5) { i -> cache.get("q$i") { listOf("x") } }

        // The expired p* entries were dropped once the cache went over its bound.
        assertTrue(cache.size() <= 6, "cache grew unbounded: ${cache.size()}")
    }

    @Test
    fun `merge puts local players first and caps the result`() {
        val local = listOf("alice", "alex")
        val remote = listOf("alex", "albert", "alfred", "alvin")

        val merged = SuggestionCache.merge(local, remote, limit = 4)

        // alex is on this proxy AND in the remote answer — it must appear once.
        assertEquals(listOf("alice", "alex", "albert", "alfred"), merged)
    }
}
