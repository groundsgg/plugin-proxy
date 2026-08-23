package gg.grounds.proxy.velocity.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Floodgate is deliberately not on this classpath — a plugin that builds without it is the whole
 * reason the API is read reflectively. So the reflection runs against a stand-in of the same shape,
 * handed in through the class lookup, and the not-installed case runs against its real absence.
 *
 * The shape asserted here is Floodgate's, taken from its published API jar: a static
 * `FloodgateApi.getInstance()`, a `getPlayers()` returning a collection, and a
 * `FloodgatePlayer.getDeviceOs()` returning an enum.
 */
class BedrockDevicesTest {

    private val logger = LoggerFactory.getLogger(BedrockDevicesTest::class.java)

    /** `DeviceOs` in the way that matters here: an enum whose `toString` is not its name. */
    enum class FakeDeviceOs {
        ANDROID,
        NX;

        override fun toString(): String = if (this == ANDROID) "Android" else "Nintendo Switch"
    }

    class FakePlayer(private val device: FakeDeviceOs?) {
        fun getDeviceOs(): FakeDeviceOs? = device
    }

    object FakeFloodgateApi {
        @JvmStatic var connected: Collection<Any?> = emptyList()

        @JvmStatic fun getInstance(): FakeFloodgateApi = this

        @JvmStatic fun getPlayers(): Collection<Any?> = connected
    }

    /**
     * A Floodgate that is loaded but returns no instance, which is how it looks before it starts.
     */
    object NotStartedFloodgateApi {
        @JvmStatic fun getInstance(): Any? = null
    }

    /** Floodgate present but a different shape — a version that renamed or dropped a method. */
    object ChangedFloodgateApi {
        @JvmStatic fun getInstance(): ChangedFloodgateApi = this
    }

    private fun devices(api: Class<*>?) = BedrockDevices(logger) { api }

    @Test
    fun `reports nothing when Floodgate is not installed`() {
        // The state of every Java proxy: the class is simply not there.
        assertTrue(
            BedrockDevices.of(logger).countsByDevice().isEmpty(),
            "a proxy without Floodgate claimed to know about Bedrock players",
        )
    }

    @Test
    fun `counts Bedrock players by device platform`() {
        FakeFloodgateApi.connected =
            listOf(
                FakePlayer(FakeDeviceOs.ANDROID),
                FakePlayer(FakeDeviceOs.ANDROID),
                FakePlayer(FakeDeviceOs.NX),
            )

        assertEquals(
            mapOf("ANDROID" to 2, "NX" to 1),
            devices(FakeFloodgateApi::class.java).countsByDevice(),
        )
    }

    @Test
    fun `a player whose device Floodgate does not know is skipped, not invented`() {
        FakeFloodgateApi.connected = listOf(FakePlayer(FakeDeviceOs.NX), FakePlayer(null))

        assertEquals(mapOf("NX" to 1), devices(FakeFloodgateApi::class.java).countsByDevice())
    }

    @Test
    fun `the platform is the enum name, not its display string`() {
        // Floodgate renders NX as "Nintendo Switch". A label that changes case and spacing between
        // releases splits one series into two, and neither half is the whole truth afterwards.
        FakeFloodgateApi.connected = listOf(FakePlayer(FakeDeviceOs.NX))

        val counts = devices(FakeFloodgateApi::class.java).countsByDevice()

        assertEquals(setOf("NX"), counts.keys)
        assertEquals("Nintendo Switch", FakeDeviceOs.NX.toString())
    }

    @Test
    fun `no players is an empty map rather than a failure`() {
        FakeFloodgateApi.connected = emptyList()

        assertTrue(devices(FakeFloodgateApi::class.java).countsByDevice().isEmpty())
    }

    @Test
    fun `Floodgate loaded but not yet started reports nothing`() {
        assertTrue(devices(NotStartedFloodgateApi::class.java).countsByDevice().isEmpty())
    }

    @Test
    fun `a Floodgate that changed shape degrades instead of throwing`() {
        // The endpoint has to keep serving every other proxy metric even when this one cannot be
        // read — a NoSuchMethodError escaping here would take the whole scrape with it.
        assertTrue(devices(ChangedFloodgateApi::class.java).countsByDevice().isEmpty())
    }

    @Test
    fun `the class is resolved once and then remembered`() {
        FakeFloodgateApi.connected = listOf(FakePlayer(FakeDeviceOs.ANDROID))
        var lookups = 0
        val devices =
            BedrockDevices(logger) {
                lookups++
                FakeFloodgateApi::class.java
            }

        repeat(5) { devices.countsByDevice() }

        assertEquals(1, lookups, "the Floodgate class was looked up on every scrape")
    }
}
