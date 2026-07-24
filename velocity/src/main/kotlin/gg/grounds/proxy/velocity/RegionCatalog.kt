package gg.grounds.proxy.velocity

import gg.grounds.proxy.api.ProxyService

/**
 * The regions a player can be sent to, and where each one answers.
 *
 * Read from `REGIONS`, a comma-separated list of `code=host[:port]`:
 * ```
 * REGIONS=nl-ams1=nl-ams1.stage.grnds.io,us-nyc1=nyc1.stage.grnds.io:25565
 * ```
 *
 * A list rather than discovery from the session table. The table only knows regions that currently
 * hold a player, so an empty region would vanish from `/region` exactly when someone wants to be
 * the first one there. It is also the only place the *address* of a region can come from — sessions
 * record where players are, not how to reach that place.
 *
 * Unparseable entries are dropped rather than failing startup: a typo in one region should not take
 * the proxy down, and the remaining regions still work. [problems] carries what was dropped so the
 * plugin can log it instead of swallowing it.
 */
class RegionCatalog(val regions: List<Region>, val problems: List<String>) {

    data class Region(val code: String, val host: String, val port: Int)

    operator fun get(code: String): Region? = regions.firstOrNull { it.code.equals(code, true) }

    val codes: List<String>
        get() = regions.map { it.code }

    companion object {
        fun fromEnvironment(
            raw: String? = System.getenv("REGIONS"),
            defaultPort: Int = ProxyService.DEFAULT_MINECRAFT_PORT,
        ): RegionCatalog {
            val regions = mutableListOf<Region>()
            val problems = mutableListOf<String>()

            raw?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.forEach { entry ->
                    val code = entry.substringBefore('=', "").trim()
                    val target = entry.substringAfter('=', "").trim()
                    if (code.isEmpty() || target.isEmpty()) {
                        problems += "'$entry' is not code=host[:port]"
                        return@forEach
                    }
                    // rsplit, because a host can contain no colon but a port always follows the
                    // last one.
                    val host = target.substringBeforeLast(':', target).trim()
                    val portText = target.substringAfterLast(':', "").trim()
                    val port =
                        when {
                            portText.isEmpty() -> defaultPort
                            else ->
                                portText.toIntOrNull()?.takeIf { it in 1..65535 }
                                    ?: run {
                                        problems += "'$entry' has a bad port"
                                        return@forEach
                                    }
                        }
                    if (host.isEmpty()) {
                        problems += "'$entry' has no host"
                        return@forEach
                    }
                    if (regions.any { it.code.equals(code, true) }) {
                        problems += "'$code' appears more than once; keeping the first"
                        return@forEach
                    }
                    regions += Region(code, host, port)
                }

            return RegionCatalog(regions, problems)
        }
    }
}
