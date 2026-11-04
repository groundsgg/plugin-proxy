package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.Palette
import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.ProxySystemMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent

/** The invoker's live connection, rendered as one support-friendly system message. */
class WhereAmICommand(
    private val currentRegion: () -> String?,
    private val currentProxy: () -> String?,
    private val messages: Translations,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val player = source as? Player
        if (player == null) {
            messages.send(source, ProxySystemMessage.WHEREAMI_PLAYER_ONLY)
            return
        }

        // The invoker is connected here. Read their backend directly so a server switch is
        // reflected immediately, without waiting for network presence heartbeats.
        val region = currentRegion()
        val proxyId = currentProxy()
        val server = player.currentServer.orElse(null)?.serverInfo?.name
        val block =
            Component.empty()
                .append(messages.render(ProxySystemMessage.WHEREAMI_HEADER, player))
                .append(Component.newline())
                .append(messages.render(ProxySystemMessage.WHEREAMI_SUPPORT, player))
                .append(Component.newline())
                .append(Component.newline())
                .append(
                    messages.render(
                        ProxySystemMessage.WHEREAMI_REGION,
                        player,
                        "region" to value(player, region),
                    )
                )
                .append(Component.newline())
                .append(
                    messages.render(
                        ProxySystemMessage.WHEREAMI_PROXY,
                        player,
                        "proxy" to value(player, proxyId, copyable = true),
                    )
                )
                .append(Component.newline())
                .append(
                    messages.render(
                        ProxySystemMessage.WHEREAMI_SERVER,
                        player,
                        "server" to value(player, server, copyable = true),
                    )
                )

        messages.sendRendered(player, ProxySystemMessage.WHEREAMI_HEADER, block)
    }

    private fun value(player: Player, raw: String?, copyable: Boolean = false): Component {
        val value =
            raw?.trim()?.takeIf(String::isNotEmpty)
                ?: return Component.text("—", Palette.TEXT_MUTED)
        val text = Component.text(value, if (copyable) Palette.ACCENT else Palette.TEXT)
        if (!copyable) return text
        return text
            .clickEvent(ClickEvent.copyToClipboard(value))
            .hoverEvent(
                HoverEvent.showText(
                    messages.render(ProxySystemMessage.WHEREAMI_COPY_HINT, player, "value" to value)
                )
            )
    }
}
