package gg.grounds.proxy.velocity

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CrossProxyMessageTest {

    private val player: UUID = UUID.fromString("9a122510-849a-44e8-b022-743093a8b1f0")

    @Test
    fun `a subject addresses a proxy, not a player`() {
        assertEquals(
            "proxy.system.velocity-2-7b8876f688-7rtt2",
            CrossProxyMessage.subjectFor(CrossProxyMessage.SYSTEM, "velocity-2-7b8876f688-7rtt2"),
        )
    }

    @Test
    fun `the target survives the round trip`() {
        val payload = """{"text":"hello"}"""
        val (targetId, decoded) =
            CrossProxyMessage.decode(CrossProxyMessage.encode(player, payload))!!
        assertEquals(player, targetId)
        assertEquals(payload, decoded)
    }

    /** Component JSON is compact, but a payload is still free to contain one. */
    @Test
    fun `only the first newline separates - the payload keeps its own`() {
        val payload = "line one\nline two"
        val (_, decoded) = CrossProxyMessage.decode(CrossProxyMessage.encode(player, payload))!!
        assertEquals(payload, decoded)
    }

    @Test
    fun `an empty payload is still a valid message`() {
        val (targetId, decoded) = CrossProxyMessage.decode(CrossProxyMessage.encode(player, ""))!!
        assertEquals(player, targetId)
        assertEquals("", decoded)
    }

    /**
     * A subject now carries messages for many players, so a malformed one must not be mistaken for
     * a message to somebody. Every one of these used to be a payload on a subject that already knew
     * who it was for.
     */
    @Test
    fun `anything that is not this envelope decodes to nothing`() {
        assertNull(CrossProxyMessage.decode(""))
        assertNull(CrossProxyMessage.decode("no newline at all"))
        assertNull(CrossProxyMessage.decode("\npayload"))
        assertNull(CrossProxyMessage.decode("not-a-uuid\npayload"))
        assertNull(CrossProxyMessage.decode("""{"text":"a bare component"}"""))
    }
}
