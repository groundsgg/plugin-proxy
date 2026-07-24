package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import gg.grounds.proxy.api.ProxyService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * `/online` — who is online across the whole network, by region and by proxy.
 *
 * Velocity can only count the players connected to the proxy running the command, so with two
 * proxies in front of one network each of them would report half of it. The numbers come from the
 * session table instead.
 *
 * When that is unreachable the command says so rather than printing this proxy's own count: a local
 * number and a network number look identical once rendered, and quietly showing the wrong one is
 * the failure this exists to avoid.
 */
class OnlineCommand(private val proxyService: () -> ProxyService?) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val counts = proxyService()?.getNetworkProxyCounts()

        if (counts == null) {
            source.sendMessage(
                Component.text(
                    "Network player counts are unavailable — the presence service cannot be reached.",
                    NamedTextColor.RED,
                )
            )
            return
        }

        source.sendMessage(
            Component.text("--- ${counts.total} players online ---", NamedTextColor.GOLD)
        )

        if (counts.proxies.isEmpty()) {
            source.sendMessage(Component.text("  nobody is connected", NamedTextColor.GRAY))
            return
        }

        // Grouped by region, proxies nested underneath: "how is the network spread" is a question
        // about places first and processes second. Regions are sorted, with the unknown bucket last
        // so it reads as a footnote rather than a region called "unknown".
        val byRegion = counts.proxies.groupBy { it.region }
        val ordered =
            byRegion.keys.filterNotNull().sorted() +
                if (byRegion.containsKey(null)) listOf(null) else emptyList()

        ordered.forEach { region ->
            val proxies = byRegion.getValue(region).sortedByDescending { it.players }
            val regionTotal = proxies.sumOf { it.players }
            val label = region ?: "unknown region"
            source.sendMessage(Component.text("$label — $regionTotal", NamedTextColor.AQUA))
            proxies.forEach { proxy ->
                source.sendMessage(
                    Component.text("  ${proxy.proxyId}: ${proxy.players}", NamedTextColor.GRAY)
                )
            }
        }
    }
}
