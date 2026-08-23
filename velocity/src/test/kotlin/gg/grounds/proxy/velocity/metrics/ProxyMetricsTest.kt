package gg.grounds.proxy.velocity.metrics

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * The endpoint is the contract: a name that changes here is a panel that goes blank on a dashboard
 * in another repo, and nothing in between would say so.
 *
 * Port 0 throughout — a fixed one makes the suite fail on whichever machine happens to be running
 * something else, which reads as a broken change.
 */
class ProxyMetricsTest {

    private val logger = LoggerFactory.getLogger(ProxyMetricsTest::class.java)

    private val snapshot =
        object : ProxySnapshot {
            var players = 12
            var servers = 4
            var network: Int? = 37

            override fun playersOnline(): Int = players

            override fun serversRegistered(): Int = servers

            override fun networkPlayers(): Int? = network
        }

    /** A Floodgate stand-in whose player list the test moves between scrapes. */
    object FakeFloodgateApi {
        @JvmStatic var connected: Collection<Any?> = emptyList()

        @JvmStatic fun getInstance(): FakeFloodgateApi = this

        @JvmStatic fun getPlayers(): Collection<Any?> = connected
    }

    enum class FakeDeviceOs {
        ANDROID,
        NX,
    }

    class FakePlayer(private val device: FakeDeviceOs) {
        fun getDeviceOs(): FakeDeviceOs = device
    }

    private fun start(
        path: String = "/metrics",
        region: String? = "nl-ams1",
        devices: BedrockDevices = BedrockDevices.of(logger),
    ): ProxyMetrics =
        ProxyMetrics.start(
            config = MetricsConfig(enabled = true, host = "127.0.0.1", port = 0, path = path),
            snapshot = snapshot,
            logger = logger,
            region = region,
            devices = devices,
        )

    private fun withFloodgate() = BedrockDevices(logger) { FakeFloodgateApi::class.java }

    private fun scrape(metrics: ProxyMetrics, path: String = "/metrics"): HttpResponse<String> =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:${metrics.port}$path")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )

    @Test
    fun `publishes the proxy gauges with the region as a label`() {
        start().use { metrics ->
            val body = scrape(metrics).body()

            assertHas(body, "velocity_players_online")
            assertHas(body, "velocity_servers_registered")
            assertHas(body, "velocity_network_players")
            assertHas(body, """region="nl-ams1"""")
        }
    }

    @Test
    fun `gauges read the proxy on every scrape rather than at registration`() {
        start().use { metrics ->
            assertTrue(value(scrape(metrics).body(), "velocity_players_online") == 12.0)

            snapshot.players = 96
            assertTrue(value(scrape(metrics).body(), "velocity_players_online") == 96.0)
        }
    }

    @Test
    fun `a network count nobody has broadcast yet is absent, not zero`() {
        snapshot.network = null
        start().use { metrics ->
            // NaN is Prometheus' "no value" — reporting 0 would claim an empty network, which is
            // exactly the lie the ping handler avoids before the first broadcast arrives.
            val reported = value(scrape(metrics).body(), "velocity_network_players")
            assertTrue(reported!!.isNaN(), "expected NaN, got $reported")
        }
    }

    @Test
    fun `publishes the JVM names the other dashboards already query`() {
        start().use { metrics ->
            val body = scrape(metrics).body()

            assertHas(body, "jvm_memory_used_bytes")
            assertHas(body, "jvm_threads_live_threads")
            assertHas(body, "process_cpu_usage")
            // The GC binder by a meter it registers eagerly: `jvm_gc_pause_seconds` only exists
            // after the first collection, so asserting on it would pass or fail on whether this
            // JVM happened to collect during the test.
            assertHas(body, "jvm_gc_memory_allocated_bytes_total")
        }
    }

    @Test
    fun `no region means no region label rather than an empty one`() {
        start(region = "  ").use { metrics ->
            assertFalse(scrape(metrics).body().contains("region="))
        }
    }

    @Test
    fun `serves the configured path only`() {
        start(path = "/q/metrics").use { metrics ->
            assertEquals(200, scrape(metrics, "/q/metrics").statusCode())
            assertEquals(404, scrape(metrics, "/metrics").statusCode())
        }
    }

    @Test
    fun `stops answering once closed`() {
        val metrics = start()
        assertEquals(200, scrape(metrics).statusCode())
        metrics.close()

        assertTrue(runCatching { scrape(metrics) }.isFailure, "still answered after close()")
    }

    @Test
    fun `configuration is off unless asked for`() {
        val config = MetricsConfig.fromEnvironment { null }

        assertFalse(config.enabled)
        assertEquals(9000, config.port)
        assertEquals("/metrics", config.path)
    }

    @Test
    fun `a path that is not a path is rejected at startup`() {
        val env = mapOf("GROUNDS_METRICS_ENABLED" to "true", "GROUNDS_METRICS_PATH" to "metrics")

        assertTrue(runCatching { MetricsConfig.fromEnvironment(env::get) }.isFailure)
    }

    @Test
    fun `publishes Bedrock players by device platform when Floodgate is there`() {
        FakeFloodgateApi.connected =
            listOf(
                FakePlayer(FakeDeviceOs.ANDROID),
                FakePlayer(FakeDeviceOs.ANDROID),
                FakePlayer(FakeDeviceOs.NX),
            )

        start(devices = withFloodgate()).use { metrics ->
            val body = scrape(metrics).body()

            assertHas(body, """velocity_bedrock_players{device_os="ANDROID"""")
            assertHas(body, """velocity_bedrock_players{device_os="NX"""")
            assertEquals(2.0, sampleAt(body, """velocity_bedrock_players{device_os="ANDROID""""))
            assertEquals(1.0, sampleAt(body, """velocity_bedrock_players{device_os="NX""""))
        }
    }

    @Test
    fun `a proxy without Floodgate publishes no device series at all`() {
        // Every Java proxy. Zero would be a claim about Bedrock players it cannot see; absent is
        // the honest answer, and it keeps the Java proxies out of a Bedrock panel entirely.
        start().use { metrics ->
            assertFalse(scrape(metrics).body().contains("velocity_bedrock_players"))
        }
    }

    @Test
    fun `a platform that empties reports zero rather than vanishing`() {
        FakeFloodgateApi.connected = listOf(FakePlayer(FakeDeviceOs.NX))
        val devices = withFloodgate()

        start(devices = devices).use { metrics ->
            assertEquals(
                1.0,
                sampleAt(scrape(metrics).body(), """velocity_bedrock_players{device_os="NX""""),
            )

            // The last Switch player leaves. Dropping the row would make the series stale, which
            // Grafana draws as a gap — indistinguishable from the endpoint being down.
            FakeFloodgateApi.connected = emptyList()

            assertEquals(
                0.0,
                sampleAt(scrape(metrics).body(), """velocity_bedrock_players{device_os="NX""""),
            )
        }
    }

    @Test
    fun `device counts are re-read on every scrape`() {
        FakeFloodgateApi.connected = listOf(FakePlayer(FakeDeviceOs.ANDROID))
        val devices = withFloodgate()

        start(devices = devices).use { metrics ->
            assertEquals(
                1.0,
                sampleAt(scrape(metrics).body(), """velocity_bedrock_players{device_os="ANDROID""""),
            )

            FakeFloodgateApi.connected =
                listOf(FakePlayer(FakeDeviceOs.ANDROID), FakePlayer(FakeDeviceOs.ANDROID))

            assertEquals(
                2.0,
                sampleAt(scrape(metrics).body(), """velocity_bedrock_players{device_os="ANDROID""""),
            )
        }
    }

    private fun assertHas(body: String, needle: String) =
        assertTrue(body.contains(needle), "the endpoint published no `$needle`")

    /**
     * The value of the first sample whose line starts with the given prefix.
     *
     * Separate from [value] because that one appends its own `{` or space to match a bare metric
     * name; a labelled sample has to be matched as the literal prefix it is.
     */
    private fun sampleAt(body: String, prefix: String): Double? =
        body
            .lines()
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfterLast(' ')
            ?.toDoubleOrNull()

    /** The value of the first sample whose name matches, or null if it is not published. */
    private fun value(body: String, name: String): Double? =
        body
            .lines()
            .firstOrNull { it.startsWith("$name{") || it.startsWith("$name ") }
            ?.substringAfterLast(' ')
            ?.toDoubleOrNull()
}
