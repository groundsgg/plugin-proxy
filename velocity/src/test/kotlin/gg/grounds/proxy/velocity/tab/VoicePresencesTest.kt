package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoicePresencesTest {
    private var now = 1_000L
    private val presences = VoicePresences { now }
    private val alice = UUID(0, 1)
    private val bob = UUID(0, 2)

    private fun snapshot(server: String, vararg players: Pair<UUID, String>) =
        """{"schemaVersion":1,"server":"$server","players":{${
            players.joinToString(",") { "\"${it.first}\":\"${it.second}\"" }
        }}}"""

    @Test
    fun `a snapshot tells voice on from voice off and leaves others without an icon`() {
        assertTrue(presences.receive(snapshot("10.0.0.1:24460", alice to "on", bob to "off")))
        assertEquals(true, presences.voiceOf(alice))
        assertEquals(false, presences.voiceOf(bob))
        assertNull(presences.voiceOf(UUID(0, 3)))
        assertFalse(presences.receive(snapshot("10.0.0.1:24460", alice to "on", bob to "off")))
    }

    @Test
    fun `the freshest server wins after a player switched servers`() {
        presences.receive(snapshot("10.0.0.1:24460", alice to "on"))
        now += 1_000
        presences.receive(snapshot("10.0.0.2:24460", alice to "off"))
        assertEquals(false, presences.voiceOf(alice))
    }

    @Test
    fun `a server that stopped sending is forgotten`() {
        presences.receive(snapshot("10.0.0.1:24460", alice to "on"))
        now += VoicePresences.EXPIRY_MILLIS
        assertNull(presences.voiceOf(alice))
    }

    @Test
    fun `malformed snapshots are ignored`() {
        assertFalse(presences.receive("nope"))
        assertFalse(presences.receive("""{"schemaVersion":2,"server":"x","players":{}}"""))
        assertFalse(presences.receive(snapshot("x", alice to "loud")))
        assertNull(presences.voiceOf(alice))
    }

    @Test
    fun `the subject covers every zone of the environment`() {
        assertEquals("voice.players.stage.>", VoicePresences.subject("stage"))
        assertNull(VoicePresences.subject("stage.*"))
    }
}
