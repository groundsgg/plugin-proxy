package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.velocity.RegionCatalog
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * `/region` — see which regions exist and move to one.
 *
 * The move is the Minecraft transfer packet: the client reconnects to the other region's address
 * itself and keeps its session, so the player sees a load screen rather than a disconnect. That is
 * why this belongs on top of [ProxyService.transferToHost] rather than being a message telling
 * someone to reconnect manually.
 *
 * Geo-steering already puts a player in the nearest region; this is the override — for playing with
 * someone on the other side of the world, or for testing a region deliberately.
 */
class RegionCommand(
    private val catalog: RegionCatalog,
    private val currentRegion: () -> String?,
    private val proxyService: () -> ProxyService?,
) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val player = source as? Player
        if (player == null) {
            source.sendMessage(
                Component.text("Only a player can change region.", NamedTextColor.RED)
            )
            return
        }

        if (catalog.regions.isEmpty()) {
            source.sendMessage(
                Component.text("No regions are configured on this proxy.", NamedTextColor.RED)
            )
            return
        }

        val requested = invocation.arguments().firstOrNull()
        if (requested == null) {
            sendList(player)
            return
        }

        val target = catalog[requested]
        if (target == null) {
            player.sendMessage(
                Component.text(
                    "Unknown region '$requested'. Available: ${catalog.codes.joinToString(", ")}",
                    NamedTextColor.RED,
                )
            )
            return
        }

        val here = currentRegion()
        if (here != null && target.code.equals(here, true)) {
            player.sendMessage(
                Component.text("You are already in ${target.code}.", NamedTextColor.YELLOW)
            )
            return
        }

        val service = proxyService()
        if (service == null) {
            player.sendMessage(
                Component.text("Region switching is unavailable right now.", NamedTextColor.RED)
            )
            return
        }

        player.sendMessage(Component.text("Moving you to ${target.code}...", NamedTextColor.GREEN))
        service.transferToHost(player.uniqueId, target.host, target.port)
    }

    private fun sendList(player: Player) {
        val here = currentRegion()
        player.sendMessage(Component.text("--- Regions ---", NamedTextColor.GOLD))
        catalog.regions.forEach { region ->
            val isHere = here != null && region.code.equals(here, true)
            player.sendMessage(
                Component.text(
                        if (isHere) "  ${region.code} (you are here)" else "  ${region.code}"
                    )
                    .color(if (isHere) NamedTextColor.GREEN else NamedTextColor.GRAY)
            )
        }
        player.sendMessage(Component.text("Use /region <code> to move.", NamedTextColor.GRAY))
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val typed = invocation.arguments().firstOrNull()?.lowercase() ?: ""
        return catalog.codes.filter { it.lowercase().startsWith(typed) }
    }
}
