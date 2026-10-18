package gg.grounds.proxy.velocity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyHopTest {
    private val self = "velocity-7d9f8c6b5-x7k2q"

    @Test
    fun `a ticket survives the cookie`() {
        val ticket = ProxyHop.Ticket("velocity-7d9f8c6b5-abcde", 3)
        assertEquals(ticket, ProxyHop.decode(ProxyHop.encode(ticket)))
    }

    @Test
    fun `no cookie, a cleared one or garbage is an ordinary join`() {
        assertEquals(ProxyHop.Decision.None, ProxyHop.decide(null, self))
        assertEquals(ProxyHop.Decision.None, ProxyHop.decide(byteArrayOf(), self))
        assertNull(ProxyHop.decode("velocity-a".toByteArray()))
        assertEquals(ProxyHop.Decision.None, ProxyHop.decide("\n3".toByteArray(), self))
    }

    @Test
    fun `the whole pod name or its suffix names a proxy, a fragment does not`() {
        assertTrue(ProxyHop.matches(self, self))
        assertTrue(ProxyHop.matches(self, "X7K2Q"))
        assertFalse(ProxyHop.matches(self, "k2q"))
        assertFalse(ProxyHop.matches(self, "velocity-other"))
    }

    @Test
    fun `only pods of the same deployment are reachable through its address`() {
        assertTrue(ProxyHop.sameDeployment(self, "velocity-7d9f8c6b5-abcde"))
        assertTrue(ProxyHop.sameDeployment(self, "velocity-5c4b3a291-zzzzz"))
        assertFalse(ProxyHop.sameDeployment(self, "velocity-bedrock-6f5e4d3c2-qqqqq"))
        assertTrue(ProxyHop.sameDeployment("", "velocity-bedrock-6f5e4d3c2-qqqqq"))
    }

    @Test
    fun `landing on the target keeps the player, even with no hops left`() {
        val payload = ProxyHop.encode(ProxyHop.Ticket("x7k2q", 0))
        assertEquals(ProxyHop.Decision.Arrived("x7k2q"), ProxyHop.decide(payload, self))
    }

    @Test
    fun `a miss spends one hop, and the last miss gives up`() {
        val again = ProxyHop.decide(ProxyHop.encode(ProxyHop.Ticket("abcde", 2)), self)
        assertEquals(ProxyHop.Decision.HopAgain(ProxyHop.Ticket("abcde", 1)), again)

        val spent = ProxyHop.decide(ProxyHop.encode(ProxyHop.Ticket("abcde", 0)), self)
        assertEquals(ProxyHop.Decision.GiveUp("abcde"), spent)
    }
}
