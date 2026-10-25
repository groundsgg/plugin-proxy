package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabPartiesTest {
    private val party = UUID(1, 0)
    private val other = UUID(2, 0)
    private val alice = UUID(0, 1)
    private val bob = UUID(0, 2)
    private val carol = UUID(0, 3)

    private fun payload(party: UUID?, vararg members: UUID) =
        """{"party":${party?.let { "\"$it\"" } ?: "null"},"members":[${
            members.joinToString(",") { "\"$it\"" }
        }]}"""

    @Test
    fun `an event puts its members into the party and drops whoever left`() {
        val parties = TabParties()
        assertTrue(parties.receive(payload(party, alice, bob, carol)))
        assertTrue(parties.receive(payload(party, alice, bob)))
        assertEquals(party, parties.partyOf(alice))
        assertEquals(party, parties.partyOf(bob))
        assertNull(parties.partyOf(carol))
    }

    @Test
    fun `a disbanded party clears all members and a repeated event changes nothing`() {
        val parties = TabParties()
        parties.receive(payload(party, alice, bob))
        assertFalse(parties.receive(payload(party, alice, bob)))
        assertTrue(parties.receive(payload(party)))
        assertNull(parties.partyOf(alice))
        assertNull(parties.partyOf(bob))
    }

    @Test
    fun `joining another party moves the player`() {
        val parties = TabParties()
        parties.receive(payload(party, alice, bob))
        parties.receive(payload(other, bob, carol))
        assertEquals(other, parties.partyOf(bob))
        assertEquals(party, parties.partyOf(alice))
    }

    @Test
    fun `a lookup without a party clears only the player it was for`() {
        val parties = TabParties()
        parties.receive(payload(party, alice, bob))
        assertFalse(parties.receive(payload(null)))
        assertTrue(parties.receive(payload(null), player = alice))
        assertNull(parties.partyOf(alice))
        assertEquals(party, parties.partyOf(bob))
    }

    @Test
    fun `malformed payloads are ignored`() {
        val parties = TabParties()
        assertFalse(parties.receive("not json"))
        assertFalse(parties.receive("""{"party":"x","members":[]}"""))
        assertFalse(parties.receive("""{"party":"$party","members":["nope"]}"""))
        assertNull(parties.partyOf(alice))
    }

    @Test
    fun `the subject follows the environment and rejects unsafe names`() {
        assertEquals("social.parties.stage", TabParties.subject("stage"))
        assertNull(TabParties.subject("stage.>"))
        assertNull(TabParties.subject(null))
    }
}
