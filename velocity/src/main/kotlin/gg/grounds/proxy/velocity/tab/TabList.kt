package gg.grounds.proxy.velocity.tab

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import gg.grounds.i18n.Palette
import gg.grounds.i18n.Translations
import gg.grounds.proxy.api.PlayerLocaleQuery
import gg.grounds.proxy.api.PlayerRoleQuery
import gg.grounds.proxy.api.ServerDisplayQuery
import java.time.Year
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * The header and footer above and below the player list, and the chips and colour of the names in
 * it.
 *
 * The tab list is the only screen a player can open from anywhere, which makes it the right place
 * for the two facts that are true everywhere: which network this is, and which backend you are on.
 * It belongs to the proxy because both answers do — a backend server knows its own name and nothing
 * about how the network should label it.
 *
 * ```
 *              [GROUNDS wordmark]
 *
 *  [DE] [ADMIN]  Steve
 *  [EN] [USER]   Alex
 *
 *      Lobby s9fwt              Ping 24 ms
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
    private val roleQuery: () -> PlayerRoleQuery?,
    private val localeQuery: () -> PlayerLocaleQuery?,
    private val serverQuery: () -> ServerDisplayQuery?,
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
            "server" to serverLabel(viewer),
            "ping" to ping(viewer.ping),
            "year" to Year.now().value.toString(),
        )

    private fun serverLabel(viewer: Player): Component {
        val raw =
            viewer.currentServer.map { it.serverInfo.name }.orElse(null)
                ?: return Component.text(UNKNOWN, Palette.TEXT_FAINT)
        val queried = serverQuery()?.displayOf(raw)
        val id = queried?.id ?: ServerDisplayIds.idOf(raw)
        val kind = queried?.kind
        val kindLabel = kindLabel(kind, viewer)
        val text = if (kindLabel == null) id else "$kindLabel $id"
        return Component.text(text, Palette.TEXT)
    }

    private fun kindLabel(kind: String?, viewer: Player): String? {
        val key =
            when (kind) {
                "lobby" -> ProxyMessage.TAB_SERVER_LOBBY
                "game" -> ProxyMessage.TAB_SERVER_GAME
                "match" -> ProxyMessage.TAB_SERVER_MATCH
                else -> return kind
            }
        return plain(messages.render(key, viewer))
    }

    /**
     * Paints each name with language and rank chips, then the name in the role's colour.
     *
     * Locale comes from [PlayerLocaleQuery] when registered, otherwise the locale the client
     * announced. Rank comes from [PlayerRoleQuery]. A missing query or a missing value omits that
     * chip rather than drawing a placeholder. Display names are always set so a player with no rank
     * still shows a language chip.
     */
    private fun colourNames(viewer: Player) {
        val query = roleQuery()
        val localeQ = localeQuery()
        viewer.tabList.entries.forEach { entry ->
            val role = query?.highestRoleOf(entry.profile.id)
            val locale =
                localeQ?.localeOf(entry.profile.id)
                    ?: proxy.getPlayer(entry.profile.id).map { it.effectiveLocale }.orElse(null)
            entry.setDisplayName(TabName.format(entry.profile.name, locale, role))
        }
    }

    private fun plain(component: Component): String =
        PlainTextComponentSerializer.plainText().serialize(component)

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
