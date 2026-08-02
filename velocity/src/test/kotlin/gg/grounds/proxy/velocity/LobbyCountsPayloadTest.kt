package gg.grounds.proxy.velocity

import gg.grounds.proxy.velocity.listener.LobbyCountsPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LobbyCountsPayloadTest {

    @Test
    fun `a request is recognised`() {
        assertTrue(LobbyCountsPayload.isCountsRequest(LobbyCountsPayload.request()))
    }

    @Test
    fun `anything else on the channel is left alone`() {
        assertFalse(LobbyCountsPayload.isCountsRequest(ByteArray(0)))
        assertFalse(LobbyCountsPayload.isCountsRequest(byteArrayOf(0x00, 0x20)))
        assertNull(LobbyCountsPayload.decode(byteArrayOf(0x00, 0x20)))
    }

    @Test
    fun `counts survive a round trip`() {
        val response = LobbyCountsPayload.response(mapOf("lobby-a" to 12, "lobby-b" to 0))

        assertEquals(listOf("Counts", "lobby-a=12,lobby-b=0"), LobbyCountsPayload.decode(response))
    }

    @Test
    fun `an unknown network answers empty, not zero`() {
        // Zeros would be indistinguishable from an empty network, and a number that is silently
        // wrong is exactly what this path exists to avoid.
        assertEquals(
            listOf("Counts", ""),
            LobbyCountsPayload.decode(LobbyCountsPayload.response(null)),
        )
    }
}
