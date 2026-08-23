package gg.grounds.proxy.velocity.metrics

import org.slf4j.Logger

/**
 * How many Bedrock players are on each device platform, asked of Floodgate.
 *
 * Only the Bedrock proxy runs Floodgate, and only Floodgate knows this: the device a Bedrock client
 * runs on travels in its login chain, Geyser hands that to Floodgate, and by the time Velocity sees
 * the player they are an ordinary Java-protocol connection with the platform stripped off. Nothing
 * downstream can recover it — the game servers cannot tell a Switch from a phone.
 *
 * ## Why reflection rather than a dependency
 *
 * This plugin is loaded on **every** proxy, and only one of them has Floodgate. A compile-time
 * dependency would be `compileOnly` anyway — Floodgate provides the classes at runtime — so the
 * only thing it would buy is type safety against an artifact GeyserMC publishes as a **SNAPSHOT
 * only**. A moving snapshot that changes an interface breaks the build of a plugin that has nothing
 * to do with Bedrock, on a proxy that never loads Floodgate. Three calls do not justify that.
 *
 * The reflection is shallow on purpose: one static, one collection, one getter per player, and the
 * enum is read as a name so `DeviceOs` — which lives in a different artifact again — never has to
 * be resolved at all.
 *
 * ## Why it probes on every read
 *
 * Velocity's plugin classloaders can see each other, but load *order* is not guaranteed and this
 * declares no dependency on Floodgate. Probing once at startup would mean a proxy that happened to
 * initialise this plugin first concluded "no Floodgate" and stayed wrong for the life of the pod.
 * Probing per read costs a cached `Class.forName` and heals itself.
 */
class BedrockDevices
internal constructor(
    private val logger: Logger,
    /**
     * How the Floodgate API class is found. Production looks it up by name through this plugin's
     * own classloader; a test hands in a stand-in of the same shape, so the reflection below is the
     * code that runs rather than a copy of it written twice.
     */
    private val lookup: () -> Class<*>?,
) {

    /** Resolved on first success and kept — the class does not come and go once Floodgate is up. */
    @Volatile private var api: Class<*>? = null

    /** True once Floodgate has been seen, so a later failure is reported rather than swallowed. */
    @Volatile private var seenFloodgate = false

    /**
     * Bedrock players per device platform, keyed by Floodgate's `DeviceOs` name (`ANDROID`, `IOS`,
     * `XBOX`, `NX`, `PS4`, `UWP`, …).
     *
     * Empty when Floodgate is not loaded, which is every Java proxy — and empty is the honest
     * answer there rather than zero, because "no Bedrock players" and "cannot see Bedrock players"
     * are different states and only one of them is worth a graph.
     */
    fun countsByDevice(): Map<String, Int> {
        val floodgate = resolve() ?: return emptyMap()
        return try {
            val instance = floodgate.getMethod("getInstance").invoke(null) ?: return emptyMap()
            val players =
                floodgate.getMethod("getPlayers").invoke(instance) as? Collection<*>
                    ?: return emptyMap()

            val counts = LinkedHashMap<String, Int>()
            for (player in players) {
                if (player == null) continue
                val device = deviceNameOf(player) ?: continue
                counts[device] = (counts[device] ?: 0) + 1
            }
            counts
        } catch (failure: ReflectiveOperationException) {
            // Floodgate is present but does not look the way it did. Reported once per read is too
            // noisy and never is too quiet; the endpoint keeps serving everything else either way.
            logger.debug("Could not read Bedrock device platforms from Floodgate", failure)
            emptyMap()
        } catch (failure: RuntimeException) {
            logger.debug("Floodgate refused to report its players", failure)
            emptyMap()
        }
    }

    /**
     * The player's `DeviceOs` as its enum constant name.
     *
     * `name` rather than `toString`: Floodgate's enum overrides `toString` with a display name
     * ("Android", "Nintendo Switch"), and a label that changes case and spacing between versions is
     * a label that silently splits a series in two.
     */
    private fun deviceNameOf(player: Any): String? {
        val device = player.javaClass.getMethod("getDeviceOs").invoke(player) ?: return null
        return (device as? Enum<*>)?.name ?: device.toString()
    }

    private fun resolve(): Class<*>? {
        api?.let {
            return it
        }
        val found = lookup() ?: return null
        if (!seenFloodgate) {
            seenFloodgate = true
            logger.info("Floodgate found; publishing Bedrock device platforms")
        }
        api = found
        return found
    }

    companion object {
        private const val FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi"

        fun of(logger: Logger): BedrockDevices =
            BedrockDevices(logger) {
                try {
                    Class.forName(FLOODGATE_API, false, BedrockDevices::class.java.classLoader)
                } catch (_: ClassNotFoundException) {
                    null
                }
            }
    }
}
