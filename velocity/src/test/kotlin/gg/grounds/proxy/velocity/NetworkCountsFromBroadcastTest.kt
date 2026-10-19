package gg.grounds.proxy.velocity

import gg.grounds.proxy.api.NetworkPlayerCounts
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NetworkCountsFromBroadcastTest {

    private val withServers = """{"total":5,"servers":{"lobby-1":3,"game-2":1}}"""

    @Test
    fun `the old reader still finds the total in a broadcast that carries servers`() {
        assertEquals(5, parsePlayerCount(withServers))
        // A server literally named "total" comes after the top-level key and does not confuse it.
        assertEquals(2, parsePlayerCount("""{"total":2,"servers":{"total":9}}"""))
    }

    @Test
    fun `reads the per-server counts in order`() {
        assertEquals(mapOf("lobby-1" to 3, "game-2" to 1), parseServerCounts(withServers))
        assertEquals(emptyMap<String, Int>(), parseServerCounts("""{"total":0,"servers":{}}"""))
    }

    @Test
    fun `a broadcast without servers, or a broken one, has none`() {
        assertNull(parseServerCounts("""{"total":5}"""))
        assertNull(parseServerCounts("""{"total":5,"servers":[1]}"""))
        assertNull(parseServerCounts("""{"total":5,"servers":{"a":-1}}"""))
        assertNull(parseServerCounts("""{"total":5,"servers":{"a":"many"}}"""))
        assertNull(parseServerCounts("""{"total":5,"servers":{"a":1.5}}"""))
        assertNull(parseServerCounts("not json"))
    }

    @Test
    fun `a fresh broadcast with servers becomes the network counts, a stale one does not`() {
        val servers = mapOf("lobby-1" to 3)
        assertEquals(NetworkPlayerCounts(servers, 5), broadcastServerCounts(5, servers, 0L, 0L))
        val late = Duration.ofSeconds(31).toNanos()
        assertNull(broadcastServerCounts(5, servers, 0L, late))
        assertNull(broadcastServerCounts(5, null, 0L, 0L))
    }

    @Test
    fun `the flag chooses the broadcast and falls back to the network query`() {
        val broadcast = NetworkPlayerCounts(mapOf("lobby-1" to 3), 5)
        val queried = NetworkPlayerCounts(mapOf("lobby-1" to 2), 4)

        assertEquals(broadcast, networkPlayerCounts(true, { broadcast }) { queried })
        assertEquals(queried, networkPlayerCounts(true, { null }) { queried })
        assertEquals(queried, networkPlayerCounts(false, { broadcast }) { queried })
        assertNull(networkPlayerCounts(true, { null }) { null })
    }
}
