package gg.grounds.proxy.velocity.metrics

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.slf4j.Logger

/**
 * A Prometheus endpoint on the proxy.
 *
 * mc-router counts connections arriving at a region's front door, and each game server counts the
 * players in its own world. Between the two sits Velocity, and nothing measured it: a proxy holding
 * players it cannot hand to a backend looks, from either side, like a quiet region.
 *
 * ## Why these numbers
 * - **Players on this proxy.** Velocity's own count, per replica. Against mc-router's connection
 *   count it says whether the router's idea of the load matches the proxy's, and against the sum of
 *   `minecraft_players_online` it says how many players are on the proxy but not on a server —
 *   which is the transfer path, and where a stuck player sits.
 * - **Registered backends.** plugin-agones registers a GameServer as a backend when it becomes
 *   Ready and unregisters it when it stops. Zero here with a healthy Fleet is discovery broken, and
 *   it is invisible everywhere else: Agones reports the servers as Ready, and the proxy simply has
 *   nowhere to send anyone.
 * - **Network player count.** What this proxy reports in the server list, taken from
 *   service-player's broadcast. It disagreeing with the sum of the proxies is a stale or missing
 *   broadcast — the server list lying to everyone who has not joined yet.
 * - **JVM and process.** Micrometer's binders, the same names the services and the game servers
 *   publish, so one query covers all three.
 *
 * ## What is deliberately not published
 *
 * Players **per backend**. Velocity knows it, but a backend's name is the Agones GameServer name —
 * `lobby-nl-ams1-tr9pf-s9fwt` — which changes every time the Fleet replaces a pod. That is a label
 * that churns with the workload, and the same breakdown already exists with stable labels:
 * `minecraft_players_online` per `app`, and `mc_router_server_active_connections` per hostname.
 */
class ProxyMetrics
private constructor(
    private val registry: PrometheusMeterRegistry,
    private val http: HttpServer,
    private val closeables: List<AutoCloseable>,
    private val logger: Logger,
) : AutoCloseable {

    /**
     * The bound port. Equals the configured one unless that was 0, which asks for any free port.
     */
    val port: Int
        get() = http.address.port

    override fun close() {
        // Zero, not a grace period: the proxy is already going down and a scrape in flight is worth
        // nothing next to the delay.
        http.stop(0)
        closeables.forEach { closeable ->
            runCatching { closeable.close() }
                .onFailure { failure -> logger.warn("Failed to close a metrics binder", failure) }
        }
        registry.close()
    }

    companion object {
        /** What Prometheus expects a text-format body to be labelled as. */
        private const val CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8"

        fun start(
            config: MetricsConfig,
            snapshot: ProxySnapshot,
            logger: Logger,
            region: String? = System.getenv("REGION"),
            devices: BedrockDevices = BedrockDevices.of(logger),
        ): ProxyMetrics {
            val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            // `cluster` and `pod` are stamped by the satellite's metrics agent; the region as the
            // proxy itself understands it is what the /region command answers from, and the two
            // disagreeing is worth being able to see.
            region
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { value -> registry.config().commonTags(Tags.of("region", value)) }

            val closeables = bindJvmAndProcess(registry)
            bindProxyGauges(registry, snapshot)

            // One series per Bedrock device platform, and the row set is not known ahead of time —
            // it is whatever players happen to be connected. A MultiGauge is the one meter that
            // takes a changing set of label values without registering a meter per value by hand.
            val bedrock =
                MultiGauge.builder("velocity.bedrock.players")
                    .description("Bedrock players by the device platform they are playing on")
                    .register(registry)
            val seenDevices = ConcurrentHashMap.newKeySet<String>()

            val http = HttpServer.create(InetSocketAddress(config.host, config.port), 0)
            http.createContext("/") { exchange ->
                // Refreshed here rather than on a timer: the endpoint is the only reader, so a
                // scrape gets the count as it is at that moment and an unscraped proxy pays
                // nothing. The walk is one pass over the connected Bedrock players.
                refreshDevices(bedrock, seenDevices, devices)
                handle(exchange, config.path, registry)
            }
            http.executor =
                Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "grounds-proxy-metrics").apply { isDaemon = true }
                }
            http.start()

            val metrics = ProxyMetrics(registry, http, closeables, logger)
            logger.info(
                "Metrics endpoint listening on http://{}:{}{}",
                config.host,
                metrics.port,
                config.path,
            )
            return metrics
        }

        /**
         * Rewrite the device rows from what Floodgate reports right now.
         *
         * Every platform ever seen keeps a row, reporting 0 when nobody is on it. Registering only
         * the platforms currently present would make a series go **stale** the moment its last
         * player leaves, which Grafana draws as a gap — indistinguishable from the endpoint being
         * down, and exactly wrong for the number that says "nobody is playing on a Switch".
         */
        private fun refreshDevices(
            gauge: MultiGauge,
            seen: MutableSet<String>,
            devices: BedrockDevices,
        ) {
            val counts = devices.countsByDevice()
            seen.addAll(counts.keys)
            if (seen.isEmpty()) return
            gauge.register(
                seen.map { device ->
                    MultiGauge.Row.of(Tags.of("device_os", device), counts[device] ?: 0)
                },
                true,
            )
        }

        private fun handle(
            exchange: HttpExchange,
            path: String,
            registry: PrometheusMeterRegistry,
        ) {
            exchange.use {
                if (exchange.requestURI.path != path) {
                    exchange.sendResponseHeaders(404, -1)
                    return@use
                }
                val body = registry.scrape().toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", CONTENT_TYPE)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { stream -> stream.write(body) }
            }
        }

        /**
         * `JvmGcMetrics` registers a notification listener that has to be closed, which is why the
         * binders are returned rather than dropped — a proxy restarted in place would otherwise
         * leak one per cycle.
         */
        private fun bindJvmAndProcess(registry: MeterRegistry): List<AutoCloseable> {
            val gc = JvmGcMetrics()
            listOf(
                    gc,
                    JvmMemoryMetrics(),
                    JvmThreadMetrics(),
                    ClassLoaderMetrics(),
                    ProcessorMetrics(),
                    UptimeMetrics(),
                )
                .forEach { binder -> binder.bindTo(registry) }
            return listOf(gc)
        }

        /**
         * `strongReference(true)` on all of them: Micrometer holds a gauge's state object weakly by
         * default, so a snapshot nobody else keeps alive is collected and every proxy gauge starts
         * reporting NaN — hours in, endpoint still up, JVM series still correct.
         */
        private fun bindProxyGauges(registry: MeterRegistry, snapshot: ProxySnapshot) {
            gauge(
                registry,
                "velocity.players.online",
                "Players connected to this proxy",
                snapshot,
            ) {
                it.playersOnline().toDouble()
            }
            gauge(
                registry,
                "velocity.servers.registered",
                "Backends this proxy knows about",
                snapshot,
            ) {
                it.serversRegistered().toDouble()
            }
            // Before the first broadcast there is no network count, and reporting 0 would claim an
            // empty network — the same distinction the ping handler makes. NaN is Prometheus'
            // "no value", and a gap in the graph is the honest rendering of "nobody has told us".
            gauge(
                registry,
                "velocity.network.players",
                "Network-wide player count this proxy reports in the server list",
                snapshot,
            ) {
                it.networkPlayers()?.toDouble() ?: Double.NaN
            }
        }

        private fun gauge(
            registry: MeterRegistry,
            name: String,
            description: String,
            snapshot: ProxySnapshot,
            read: (ProxySnapshot) -> Double,
        ) {
            Gauge.builder(name, snapshot, read)
                .description(description)
                .strongReference(true)
                .register(registry)
        }
    }
}

/**
 * The proxy-side numbers, read fresh on every scrape.
 *
 * An interface because the real one calls into a running `ProxyServer`, which a unit test has no
 * way to stand up.
 */
interface ProxySnapshot {
    fun playersOnline(): Int

    fun serversRegistered(): Int

    /** Null until service-player's first broadcast arrives — not zero. */
    fun networkPlayers(): Int?
}

/**
 * Where the endpoint listens, and whether it exists at all.
 *
 * Off by default: the satellite's metrics agent only scrapes pods carrying `prometheus.io/scrape`,
 * so this switch and the chart's have to agree before anything is published.
 */
data class MetricsConfig(
    val enabled: Boolean = false,
    val host: String = "0.0.0.0",
    val port: Int = 9000,
    val path: String = "/metrics",
) {
    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): MetricsConfig {
            fun string(name: String, default: String) =
                env(name)?.trim()?.takeIf { it.isNotEmpty() } ?: default

            val port = string("GROUNDS_METRICS_PORT", "9000")
            val config =
                MetricsConfig(
                    enabled = string("GROUNDS_METRICS_ENABLED", "false").lowercase() == "true",
                    host = string("GROUNDS_METRICS_HOST", "0.0.0.0"),
                    port =
                        port.toIntOrNull()
                            ?: throw IllegalArgumentException(
                                "GROUNDS_METRICS_PORT is not a number: $port"
                            ),
                    path = string("GROUNDS_METRICS_PATH", "/metrics"),
                )
            require(config.path.startsWith("/")) {
                "GROUNDS_METRICS_PATH must start with '/': ${config.path}"
            }
            return config
        }
    }
}
