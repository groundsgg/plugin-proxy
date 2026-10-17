package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.ProxySystemMessage

/** Operational identifiers are intentionally absent from the normal tab footer. */
class ProxyDebugCommand(private val messages: Translations) : SimpleCommand {
    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean =
        invocation.source().hasPermission(PERMISSION)

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission(PERMISSION)) {
            messages.send(source, ProxySystemMessage.DEBUG_DENIED)
            return
        }
        val player = source as? Player
        if (player == null) {
            messages.send(source, ProxySystemMessage.DEBUG_PLAYER_ONLY)
            return
        }
        messages.send(
            player,
            ProxySystemMessage.DEBUG_SERVER,
            "server" to (player.currentServer.orElse(null)?.serverInfo?.name ?: "—"),
        )
        messages.send(
            player,
            ProxySystemMessage.DEBUG_PROXY,
            "proxy" to System.getenv("PROXY_ID").orEmpty().ifBlank { "—" },
        )
    }

    companion object {
        const val PERMISSION = "grounds.proxy.debug"
    }
}
