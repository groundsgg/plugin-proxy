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
        // UUID layout is not proof: Floodgate can also use a linked Java UUID, and a Java
        // account can otherwise be mistaken for Bedrock by heuristics. Only Floodgate's API is
        // authoritative for display classification.
        return false
    }

    companion object {
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
