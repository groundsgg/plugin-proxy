package gg.grounds.proxy.velocity.tab

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.i18n.Palette
import gg.grounds.i18n.Translations
import gg.grounds.proxy.api.PlayerRole
import gg.grounds.proxy.api.PlayerRoleQuery
import java.time.Year
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

/**
 * The header and footer above and below the player list, and the colour of the names in it.
 *
 * The tab list is the only screen a player can open from anywhere, which makes it the right place
 * for the two facts that are true everywhere: which network this is, and where in it you are
 * standing. It belongs to the proxy because both answers do — a backend server knows its own name
 * and nothing about the region it sits in.
 *
 * ```
 *              Grounds Network
 *
 *      Region nl-ams1     Ping 24 ms
 *              grounds.gg 2026
 * ```
 *
 * The ping is coloured by how bad it is, using the state colours rather than a number nobody reads:
 * green under 100 ms, amber under 200, salmon above. A player who has not been measured yet gets a
 * dash instead of a fabricated zero.
 */
class TabList(
    private val proxy: ProxyServer,
    private val messages: Translations,
    private val region: () -> String?,
    private val roleQuery: () -> PlayerRoleQuery?,
) {

    /** Redraws everything [viewer] sees. */
    fun refresh(viewer: Player) {
        viewer.sendPlayerListHeaderAndFooter(header(viewer), footer(viewer))
        colourNames(viewer)
    }

    /**
     * Redraws for everyone.
     *
     * Called on a timer, because the ping and the roster change without an event to hang off. It
     * renders per player rather than once for all of them: the footer is in the player's own
     * language and carries their own ping, so there is nothing to share.
     */
    fun refreshAll() {
        proxy.allPlayers.forEach(::refresh)
    }

    private fun header(viewer: Player): Component = messages.render(ProxyMessage.TAB_HEADER, viewer)

    private fun footer(viewer: Player): Component =
        messages.render(
            ProxyMessage.TAB_FOOTER,
            viewer,
            "region" to (region() ?: UNKNOWN),
            "ping" to ping(viewer.ping),
            "year" to Year.now().value.toString(),
        )

    /**
     * Paints each name in the colour of its owner's highest role.
     *
     * A no-op until something registers a [PlayerRoleQuery] — plugin-permissions holds the snapshot
     * these colours come from. Until then every name keeps the backend's own display name, which is
     * what players see today.
     */
    private fun colourNames(viewer: Player) {
        val query = roleQuery() ?: return
        viewer.tabList.entries.forEach { entry ->
            val role = query.highestRoleOf(entry.profile.id) ?: return@forEach
            entry.setDisplayName(displayName(entry.profile.name, role))
        }
    }

    private fun displayName(name: String, role: PlayerRole): Component {
        // A colour the service stores badly should cost that one player their colour, not throw on
        // a render that runs every few seconds for everybody.
        val colour = role.colour?.let(TextColor::fromHexString) ?: Palette.TEXT
        val prefix = role.prefix.orEmpty()
        return Component.empty()
            .append(Component.text(prefix, colour))
            .append(Component.text(name, colour))
    }

    companion object {
        private const val UNKNOWN = "—"

        /** Below this a player has nothing to complain about. */
        internal const val GOOD_PING_MS = 100L

        /** Below this it is playable; above it, something is wrong worth showing. */
        internal const val FAIR_PING_MS = 200L

        /**
         * The ping, coloured by how bad it is.
         *
         * Velocity reports -1 until the first keepalive round trip has come back, which is most of
         * the first second after a join. Printing that as `-1 ms`, or rounding it to 0, would be a
         * lie in the one place a player looks to decide whether the lag is theirs.
         */
        internal fun ping(millis: Long): Component =
            when {
                millis < 0 -> Component.text(UNKNOWN, Palette.TEXT_FAINT)
                millis < GOOD_PING_MS -> Component.text("$millis ms", Palette.SUCCESS)
                millis < FAIR_PING_MS -> Component.text("$millis ms", Palette.WARNING)
                else -> Component.text("$millis ms", Palette.DANGER)
            }
    }
}
