package gg.grounds.proxy.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import gg.grounds.i18n.Translations
import gg.grounds.proxy.api.ProxyService
import gg.grounds.proxy.velocity.ProxySystemMessage
import gg.grounds.proxy.velocity.RegionCatalog

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
 *
 * A bare `/region` from a player on a lobby is not answered here: the lobby draws a world map for
 * picking the default region, and Velocity hands a command the proxy declines in [hasPermission] to
 * the player's current server. Everywhere else the bare command still lists the regions.
 */
class RegionCommand(
    private val catalog: RegionCatalog,
    private val currentRegion: () -> String?,
    private val proxyService: () -> ProxyService?,
    private val messages: Translations,
    private val isOnLobby: (Player) -> Boolean = { false },
) : SimpleCommand {

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        val player = invocation.source() as? Player
        return regionAnswersHere(
            isPlayer = player != null,
            hasArguments = invocation.arguments().isNotEmpty(),
            onLobby = player != null && isOnLobby(player),
        )
    }

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val player = source as? Player
        if (player == null) {
            messages.send(source, ProxySystemMessage.REGION_PLAYER_ONLY)
            return
        }

        if (catalog.regions.isEmpty()) {
            messages.send(source, ProxySystemMessage.REGION_NONE)
            return
        }

        val requested = invocation.arguments().firstOrNull()
        if (requested == null) {
            sendList(player)
            return
        }

        val target = catalog[requested]
        if (target == null) {
            messages.send(
                player,
                ProxySystemMessage.REGION_UNKNOWN,
                "region" to requested,
                "available" to catalog.codes.joinToString(", "),
            )
            return
        }

        val here = currentRegion()
        if (here != null && target.code.equals(here, true)) {
            messages.send(player, ProxySystemMessage.REGION_ALREADY_HERE, "region" to target.code)
            return
        }

        val service = proxyService()
        if (service == null) {
            messages.send(player, ProxySystemMessage.REGION_UNAVAILABLE)
            return
        }

        messages.send(player, ProxySystemMessage.REGION_MOVING, "region" to target.code)
        service.transferToHost(player.uniqueId, target.host, target.port)
    }

    private fun sendList(player: Player) {
        val here = currentRegion()
        messages.send(player, ProxySystemMessage.REGION_HEADER)
        catalog.regions.forEach { region ->
            val isHere = here != null && region.code.equals(here, true)
            if (isHere) {
                messages.send(
                    player,
                    ProxySystemMessage.REGION_ENTRY_CURRENT,
                    "region" to region.code,
                )
            } else {
                messages.sendInteractive(
                    player,
                    ProxySystemMessage.REGION_ENTRY,
                    CommandAction.run("/region ${region.code}"),
                    "/region ${region.code}",
                    "region" to region.code,
                )
            }
        }
        messages.send(player, ProxySystemMessage.REGION_USAGE)
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val typed = invocation.arguments().firstOrNull()?.lowercase() ?: ""
        return catalog.codes.filter { it.lowercase().startsWith(typed) }
    }
}

/**
 * Whether the proxy handles a `/region`. Only a player's bare command on a lobby is declined, which
 * makes Velocity forward it to the lobby's world map. Top level so a test can reach it without the
 * Velocity API, which is compile-only.
 */
internal fun regionAnswersHere(
    isPlayer: Boolean,
    hasArguments: Boolean,
    onLobby: Boolean,
): Boolean = !isPlayer || hasArguments || !onLobby
