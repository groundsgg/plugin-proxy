package gg.grounds.proxy.velocity.maintenance

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Read-only service-config client for the shared maintenance document. */
class MaintenanceConfigStore
internal constructor(
    private val app: String,
    private val env: String,
    private val baseUri: URI,
    private val http: HttpClient,
    private val deadline: Duration = DEADLINE,
) : AutoCloseable {
    fun read(): MaintenanceDocument? {
        val request =
            HttpRequest.newBuilder(baseUri.resolve(path()))
                .timeout(deadline)
                .header("Accept", "application/json")
        token()?.let { request.header("Authorization", "Bearer $it") }
        val subscriber = BoundedBodySubscriber(MAX_RESPONSE_BYTES)
        val pending = http.sendAsync(request.GET().build()) { subscriber }
        val response =
            try {
                // JDK 25 request timeouts finish at headers. Bound the entire body exchange as
                // well.
                pending.get(deadline.toMillis(), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                subscriber.cancel()
                pending.cancel(true)
                throw MaintenanceStoreException("Maintenance request timed out")
            } catch (_: InterruptedException) {
                subscriber.cancel()
                pending.cancel(true)
                Thread.currentThread().interrupt()
                throw MaintenanceStoreException("Maintenance request interrupted")
            } catch (_: Exception) {
                subscriber.cancel()
                pending.cancel(true)
                throw MaintenanceStoreException("Maintenance response could not be read")
            }
        val body = response.body()
        if (response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299)
            throw MaintenanceStoreException(
                "Could not read maintenance (HTTP ${response.statusCode()})"
            )
        val content =
            JsonParser.parseString(body).asJsonObject.get("contentJson")?.asString
                ?: throw MaintenanceStoreException("Maintenance response has no contentJson")
        return parse(content)
    }

    override fun close() = http.close()

    private fun path() = "/v1/config/apps/$app/envs/$env/namespaces/maintenance/documents/active"

    companion object {
        private val DEADLINE = Duration.ofSeconds(5)
        private const val MAX_RESPONSE_BYTES = 262_144
        private val UUID_PATTERN =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        private fun canonicalUuid(raw: String): UUID {
            if (!UUID_PATTERN.matches(raw))
                throw MaintenanceStoreException("Maintenance UUID is not canonical")
            return UUID.fromString(raw)
        }

        fun open(app: String, env: String, target: String): MaintenanceConfigStore {
            val uri = URI.create(if (target.contains("://")) target else "http://$target")
            return MaintenanceConfigStore(app, env, uri, HttpClient.newHttpClient())
        }

        internal fun parse(json: String): MaintenanceDocument {
            val root =
                runCatching { JsonParser.parseString(json).asJsonObject }
                    .getOrElse {
                        throw MaintenanceStoreException("Maintenance document is not an object")
                    }
            fun string(name: String): String =
                root
                    .get(name)
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?: throw MaintenanceStoreException("Maintenance field '$name' is required")
            fun bool(name: String): Boolean =
                root
                    .get(name)
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                    ?.asBoolean
                    ?: throw MaintenanceStoreException("Maintenance field '$name' is required")
            fun instant(name: String, nullable: Boolean = false): Instant? {
                val value =
                    root.get(name)
                        ?: throw MaintenanceStoreException("Maintenance field '$name' is required")
                if (nullable && value.isJsonNull) return null
                return runCatching { Instant.parse(value.asString) }
                    .getOrElse {
                        throw MaintenanceStoreException("Maintenance field '$name' is not ISO UTC")
                    }
            }
            val schema = root.get("schemaVersion")
            if (
                schema == null ||
                    !schema.isJsonPrimitive ||
                    !schema.asJsonPrimitive.isNumber ||
                    schema.asString != "1"
            )
                throw MaintenanceStoreException("Unsupported maintenance schema")
            val idText = string("id")
            val id = canonicalUuid(idText)
            val startsAt = instant("startsAt")!!
            val endsAt = instant("endsAt", true)
            val estimatedEndAt = instant("estimatedEndAt", true)
            if (
                (endsAt != null && !endsAt.isAfter(startsAt)) ||
                    (estimatedEndAt != null && !estimatedEndAt.isAfter(startsAt))
            )
                throw MaintenanceStoreException("Maintenance end must be after start")
            val reason =
                string("reason").also {
                    if (it.isEmpty() || it.length > 500)
                        throw MaintenanceStoreException("Maintenance reason is invalid")
                }
            val join =
                string("joinMessage").also {
                    if (it.isEmpty() || it.length > 4096)
                        throw MaintenanceStoreException("Maintenance joinMessage is invalid")
                }
            val motd =
                string("motd").also {
                    if (it.length > 4096)
                        throw MaintenanceStoreException("Maintenance motd is invalid")
                }
            val announcement =
                string("announcementMessage").also {
                    if (it.isEmpty() || it.length > 4096)
                        throw MaintenanceStoreException(
                            "Maintenance announcementMessage is invalid"
                        )
                }
            val whitelist =
                root.getAsJsonArray("whitelist")
                    ?: throw MaintenanceStoreException("Maintenance field 'whitelist' is required")
            if (whitelist.size() > 1000)
                throw MaintenanceStoreException("Maintenance whitelist is too long")
            val uuids =
                whitelist.map { entry ->
                    if (!entry.isJsonPrimitive || !entry.asJsonPrimitive.isString)
                        throw MaintenanceStoreException("Whitelist entries must be UUID strings")
                    canonicalUuid(entry.asString)
                }
            if (uuids.distinct().size != uuids.size)
                throw MaintenanceStoreException("Maintenance whitelist has duplicate UUIDs")
            val offsets =
                root.getAsJsonArray("announcementSeconds")
                    ?: throw MaintenanceStoreException(
                        "Maintenance field 'announcementSeconds' is required"
                    )
            val seconds =
                offsets.map { entry ->
                    if (
                        !entry.isJsonPrimitive ||
                            !entry.asJsonPrimitive.isNumber ||
                            !entry.asString.matches(Regex("[1-9][0-9]*"))
                    )
                        throw MaintenanceStoreException("Invalid announcement seconds")
                    entry.asString.toLongOrNull()
                        ?: throw MaintenanceStoreException("Invalid announcement seconds")
                }
            if (
                seconds.size > 32 ||
                    seconds.distinct().size != seconds.size ||
                    seconds.any { it !in 1..604800 }
            )
                throw MaintenanceStoreException("Invalid announcement seconds")
            return MaintenanceDocument(
                bool("enabled"),
                id,
                startsAt,
                endsAt,
                estimatedEndAt,
                reason,
                join,
                motd,
                bool("kickExisting"),
                uuids.toSet(),
                seconds,
                announcement,
                root
                    .getAsJsonObject("serverList")
                    ?.let(gg.grounds.proxy.velocity.motd.ServerListTemplate::parse),
                root.get("serverListFallbackText")?.asString,
                root.getAsJsonArray("serverListOverrides")?.map { it.asString }?.toSet(),
            )
        }

        private fun token(): String? =
            runCatching {
                    val file =
                        Path.of(
                            System.getenv("GROUNDS_TOKEN_FILE") ?: "/var/run/secrets/grounds/token"
                        )
                    if (Files.exists(file)) Files.readString(file).trim().ifEmpty { null } else null
                }
                .getOrNull()
    }
}

class MaintenanceStoreException(message: String) : RuntimeException(message)

/** Bounds memory during streaming, before the response future is allowed to complete. */
private class BoundedBodySubscriber(private val limit: Int) : HttpResponse.BodySubscriber<String> {
    private val result = CompletableFuture<String>()
    private val bytes = ByteArrayOutputStream()
    @Volatile private var subscription: Flow.Subscription? = null

    override fun getBody(): CompletionStage<String> = result

    override fun onSubscribe(next: Flow.Subscription) {
        subscription = next
        if (result.isDone) next.cancel() else next.request(1)
    }

    override fun onNext(items: List<ByteBuffer>) {
        if (result.isDone) return
        for (buffer in items) {
            if (buffer.remaining() > limit - bytes.size()) {
                subscription?.cancel()
                result.completeExceptionally(
                    MaintenanceStoreException("Maintenance response is too large")
                )
                return
            }
            val chunk = ByteArray(buffer.remaining())
            buffer.get(chunk)
            bytes.write(chunk)
        }
        subscription?.request(1)
    }

    override fun onError(failure: Throwable) {
        result.completeExceptionally(failure)
    }

    override fun onComplete() {
        result.complete(bytes.toString(StandardCharsets.UTF_8))
    }

    fun cancel() {
        result.cancel(true)
        subscription?.cancel()
    }
}
