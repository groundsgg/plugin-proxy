package gg.grounds.proxy.velocity.motd

import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * `{{token}}` substitution, applied to the stored MiniMessage before it is parsed.
 *
 * The point of these is that one stored MOTD renders differently depending on who answered the
 * ping: the same document says `nl-ams1` in Amsterdam and `us-nyc1` in New York. That is why they
 * are resolved here, per ping, rather than baked in when the MOTD is written.
 *
 * Substitution happens *before* the MiniMessage parse, so a value could otherwise smuggle in tags.
 * None of today's values can — they come from environment variables and player counts — but the
 * escape is what keeps that true once a value comes from somewhere else.
 */
object MotdPlaceholders {

    /**
     * What the placeholders resolve to on this proxy, for this ping.
     *
     * [region] and [continent] come from the environment and are the same for every ping this proxy
     * answers; [players] and [maxPlayers] change between them.
     */
    data class Context(
        val region: String?,
        val continent: String?,
        val players: Int,
        val maxPlayers: Int,
    )

    /**
     * The tokens that resolve, in the form an operator types them. `localzone` and `continent` are
     * the same value under two names — `localzone` is what the MOTD reads like from inside the
     * game, `continent` is what the deployment calls it.
     */
    val TOKENS: List<String> = listOf("region", "localzone", "continent", "players", "max")

    private val TOKEN_PATTERN = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}")

    /**
     * Replaces every known token in [template].
     *
     * A known token whose value is unset renders as nothing — a proxy with no `REGION` should show
     * a gap, not the word "null". An *unknown* token is left standing as written: that is a typo in
     * the MOTD, and seeing `{{regoin}}` in the server list is how it gets found.
     */
    fun render(template: String, context: Context): String =
        TOKEN_PATTERN.replace(template) { match ->
            val token = match.groupValues[1].lowercase()
            val value = resolve(token, context) ?: return@replace match.value
            MiniMessage.miniMessage().escapeTags(value)
        }

    /** True when [template] contains a token whose value can change between two pings. */
    fun isDynamic(template: String): Boolean =
        TOKEN_PATTERN.findAll(template).any { it.groupValues[1].lowercase() in DYNAMIC_TOKENS }

    private fun resolve(token: String, context: Context): String? =
        when (token) {
            "region" -> context.region.orEmpty()
            "localzone",
            "continent" -> context.continent.orEmpty()
            "players" -> context.players.toString()
            "max" -> context.maxPlayers.toString()
            else -> null
        }

    private val DYNAMIC_TOKENS = setOf("players", "max")
}
