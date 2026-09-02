package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.slf4j.Logger

/** Optional Floodgate lookup; this plugin also runs on proxies without Floodgate. */
internal class BedrockPlayers(private val logger: Logger, private val lookup: () -> Class<*>?) {
    @Volatile private var api: Class<*>? = null

    fun isBedrock(id: UUID): Boolean {
        val type = api ?: lookup()?.also { api = it }
        if (type != null) {
            try {
                val instance = type.getMethod("getInstance").invoke(null)
                if (instance != null) {
                    return type
                        .getMethod("isFloodgatePlayer", UUID::class.java)
                        .invoke(instance, id) == true
                }
            } catch (failure: ReflectiveOperationException) {
                logger.debug("Could not read a player's edition from Floodgate", failure)
            }
        }
        return isUnlinkedUuid(id)
    }

    companion object {
        // Floodgate represents an unlinked XUID in the UUID's lower 64 bits.
        fun isUnlinkedUuid(id: UUID): Boolean =
            id.mostSignificantBits == 0L && id.leastSignificantBits != 0L

        fun of(logger: Logger): BedrockPlayers =
            BedrockPlayers(logger) {
                try {
                    Class.forName(
                        "org.geysermc.floodgate.api.FloodgateApi",
                        false,
                        BedrockPlayers::class.java.classLoader,
                    )
                } catch (_: ClassNotFoundException) {
                    null
                }
            }
    }
}
