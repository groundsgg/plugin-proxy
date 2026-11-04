package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.Translations
import gg.grounds.proxy.velocity.ProxySystemMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor

/** The invoker's live connection, rendered as one support-friendly system message. */
class WhereAmICommand
@JvmOverloads
constructor(
    private val currentRegion: () -> String?,
    private val currentProxy: () -> String?,
    private val messages: Translations,
    private val iconsReady: (Player) -> Boolean = { false },
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
        val showIcons = iconsReady(player)
        val region = currentRegion()
        val proxyId = currentProxy()
        val server = player.currentServer.orElse(null)?.serverInfo?.name
        val block =
            Component.empty()
                .append(messages.render(ProxySystemMessage.WHEREAMI_HEADER, player))
                .append(Component.newline())
                .append(
                    messages.render(ProxySystemMessage.WHEREAMI_SUPPORT, player).color(SECONDARY)
                )
                .append(Component.newline())
                .append(Component.newline())
                .append(
                    messages
                        .render(
                            ProxySystemMessage.WHEREAMI_REGION,
                            player,
                            "icon" to WhereAmIGlyphs.render(WhereAmIGlyphs.REGION, showIcons),
                            "region" to value(player, region),
                        )
                        .color(SECONDARY)
                )
                .append(Component.newline())
                .append(
                    messages
                        .render(
                            ProxySystemMessage.WHEREAMI_PROXY,
                            player,
                            "icon" to WhereAmIGlyphs.render(WhereAmIGlyphs.PROXY, showIcons),
                            "proxy" to value(player, proxyId, copyable = true),
                        )
                        .color(SECONDARY)
                )
                .append(Component.newline())
                .append(
                    messages
                        .render(
                            ProxySystemMessage.WHEREAMI_SERVER,
                            player,
                            "icon" to WhereAmIGlyphs.render(WhereAmIGlyphs.SERVER, showIcons),
                            "server" to value(player, server, copyable = true),
                        )
                        .color(SECONDARY)
                )

        messages.sendRendered(player, ProxySystemMessage.WHEREAMI_HEADER, block)
    }

    private fun value(player: Player, raw: String?, copyable: Boolean = false): Component {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return Component.text("—", SECONDARY)
        val text = Component.text(value, if (copyable) INTERACTIVE else PRIMARY)
        if (!copyable) return text
        return text
            .clickEvent(ClickEvent.copyToClipboard(value))
            .hoverEvent(
                HoverEvent.showText(
                    messages.render(ProxySystemMessage.WHEREAMI_COPY_HINT, player, "value" to value)
                )
            )
    }

    private companion object {
        // Minecraft Mini-CD tokens (docs/design/minecraft-mini-cd-and-chat.md).
        // The general web palette's muted/mint tokens have different values.
        val PRIMARY = TextColor.color(0xF3F0EA)
        val SECONDARY = TextColor.color(0xC1B9AA)
        val INTERACTIVE = TextColor.color(0x67D9EC)
    }
}
