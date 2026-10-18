package gg.grounds.proxy.velocity

/**
 * The cookie `/proxy <id>` travels on, and what a proxy does when a player arrives carrying it.
 *
 * No address reaches one particular proxy: every proxy in a region sits behind the same name, and
 * which one answers a connect is up to the load balancer. So the player is sent back through that
 * name with the wanted proxy written into a cookie, and whichever proxy they land on either keeps
 * them (it is the one) or sends them round again, until [MAX_HOPS] is spent.
 *
 * Top level and free of the Velocity API so a test can reach it; the API is compile-only.
 */
object ProxyHop {
    const val KEY = "grounds:proxy_hop"
    const val MAX_HOPS = 8

    data class Ticket(val target: String, val hopsLeft: Int)

    sealed interface Decision {
        /** No ticket: an ordinary join. */
        data object None : Decision

        data class Arrived(val target: String) : Decision

        data class HopAgain(val ticket: Ticket) : Decision

        data class GiveUp(val target: String) : Decision
    }

    fun encode(ticket: Ticket): ByteArray = "${ticket.target}\n${ticket.hopsLeft}".toByteArray()

    fun decode(payload: ByteArray?): Ticket? {
        val text = payload?.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8) ?: return null
        val target = text.substringBefore('\n').trim().takeIf(String::isNotEmpty) ?: return null
        val hopsLeft = text.substringAfter('\n', "").trim().toIntOrNull() ?: return null
        return Ticket(target, hopsLeft)
    }

    /**
     * Whether [proxyId] is the proxy [target] names. Pod names end in a random suffix, so the
     * suffix alone (`/proxy x7k2q`) is accepted as well as the whole name.
     */
    fun matches(proxyId: String, target: String): Boolean =
        proxyId.equals(target, true) || proxyId.endsWith("-$target", true)

    /**
     * Whether [a] and [b] are pods of the same Deployment (`<deployment>-<hash>-<suffix>`). Only
     * those sit behind the address a hop goes through; a Bedrock proxy, say, never answers it.
     */
    fun sameDeployment(a: String, b: String): Boolean {
        val first = deploymentOf(a) ?: return true
        val second = deploymentOf(b) ?: return true
        return first.equals(second, true)
    }

    private fun deploymentOf(proxyId: String): String? =
        proxyId.split('-').takeIf { it.size > 2 }?.dropLast(2)?.joinToString("-")

    fun decide(payload: ByteArray?, proxyId: String): Decision {
        val ticket = decode(payload) ?: return Decision.None
        return when {
            matches(proxyId, ticket.target) -> Decision.Arrived(ticket.target)
            ticket.hopsLeft > 0 -> Decision.HopAgain(ticket.copy(hopsLeft = ticket.hopsLeft - 1))
            else -> Decision.GiveUp(ticket.target)
        }
    }
}
