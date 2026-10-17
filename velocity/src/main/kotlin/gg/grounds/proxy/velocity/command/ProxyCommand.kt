package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.Translations
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.velocity.ProxyHop
import gg.grounds.proxy.velocity.ProxySystemMessage
import gg.grounds.proxy.velocity.SuggestionCache
import gg.grounds.proxy.velocity.listener.ProxyHopListener

/**
 * `/proxy` — which proxy you are on and which others hold players; `/proxy <id>` moves you to one.
 *
 * For testing behaviour that spans proxies, hence behind [PERMISSION]. The move goes through
 * [ProxyHopListener]: there is no address for a single proxy, so it can take a few reconnects.
 *
 * Any id is accepted, not only listed ones: the list comes from the session table, which does not
 * know a proxy nobody is on, and an empty proxy is often the one a test wants.
 */
class ProxyCommand(
    private val proxyId: String,
    private val proxyService: () -> ProxyService?,
    private val hops: ProxyHopListener,
    private val messages: Translations,
) : SimpleCommand {
    private val suggestions = SuggestionCache()

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean =
        invocation.source().hasPermission(PERMISSION)

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val requested = invocation.arguments().firstOrNull()
        if (requested == null) {
            sendList(source)
            return
        }

        val player = source as? Player
        if (player == null) {
            messages.send(source, ProxySystemMessage.PROXY_PLAYER_ONLY)
            return
        }
        if (ProxyHop.matches(proxyId, requested)) {
            messages.send(player, ProxySystemMessage.PROXY_ALREADY_HERE, "proxy" to proxyId)
            return
        }
        if (player.protocolVersion < ProtocolVersion.MINECRAFT_1_20_5) {
            messages.send(player, ProxySystemMessage.PROXY_UNSUPPORTED_CLIENT)
            return
        }

        // One hop is the transfer about to happen, so a ticket carries one fewer than the cap.
        if (!hops.send(player, ProxyHop.Ticket(requested, ProxyHop.MAX_HOPS - 1))) {
            messages.send(player, ProxySystemMessage.PROXY_NO_ADDRESS)
            return
        }
        messages.send(player, ProxySystemMessage.PROXY_MOVING, "proxy" to requested)
    }

    private fun sendList(source: CommandSource) {
        messages.send(source, ProxySystemMessage.PROXY_CURRENT, "proxy" to proxyId.ifBlank { "—" })
        proxyService()
            ?.getNetworkProxyCounts()
            ?.proxies
            ?.sortedBy { it.proxyId }
            ?.forEach { entry ->
                messages.send(
                    source,
                    if (entry.proxyId == proxyId) ProxySystemMessage.PROXY_ENTRY_CURRENT
                    else ProxySystemMessage.PROXY_ENTRY,
                    "proxy" to entry.proxyId,
                    "region" to (entry.region ?: "—"),
                    "count" to entry.players,
                )
            }
        messages.send(source, ProxySystemMessage.PROXY_USAGE, "syntax" to "/proxy <id>")
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val typed = invocation.arguments().firstOrNull()?.lowercase() ?: ""
        val known =
            suggestions.get("") {
                proxyService()?.getNetworkProxyCounts()?.proxies?.map { it.proxyId }.orEmpty()
            }
        return known.filter { it != proxyId && it.lowercase().startsWith(typed) }
    }

    companion object {
        const val PERMISSION = "grounds.proxy.switch"
    }
}
