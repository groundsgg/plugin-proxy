package gg.grounds.proxy.velocity

import gg.grounds.proxy.velocity.command.regionAnswersHere
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegionCommandTest {

    /** Declining is what makes Velocity forward the command to the lobby's world map. */
    @Test
    fun `a bare region on a lobby goes to the lobby`() {
        assertFalse(regionAnswersHere(isPlayer = true, hasArguments = false, onLobby = true))
    }

    @Test
    fun `a region code, another server or the console stays on the proxy`() {
        assertTrue(regionAnswersHere(isPlayer = true, hasArguments = true, onLobby = true))
        assertTrue(regionAnswersHere(isPlayer = true, hasArguments = false, onLobby = false))
        assertTrue(regionAnswersHere(isPlayer = false, hasArguments = false, onLobby = false))
    }
}
