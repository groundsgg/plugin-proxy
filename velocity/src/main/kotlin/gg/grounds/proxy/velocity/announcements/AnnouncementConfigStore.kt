package gg.grounds.proxy.velocity.announcements

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit

sealed interface AnnouncementSnapshot {
    data object Unchanged : AnnouncementSnapshot

    data class Changed(val version: Long, val settings: AnnouncementSettings?) :
        AnnouncementSnapshot
}

/** Read-only authority. Neither missing documents nor outages ever write defaults. */
class AnnouncementConfigStore(
    baseUri: URI,
    environment: String,
    private val token: () -> String?,
    private val http: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : AutoCloseable {
    private val uri: URI

    init {
        require(environment in setOf("stage", "prod"))
        require(baseUri.scheme in setOf("http", "https") && baseUri.userInfo == null)
        uri =
            baseUri.resolve(
                "/v1/config/apps/velocity/envs/$environment/namespaces/announcements/snapshot"
            )
    }

    fun read(knownVersion: Long?): AnnouncementSnapshot {
        val builder =
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
        token()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { builder.header("Authorization", "Bearer $it") }
        knownVersion?.let { builder.header("If-None-Match", "\"$it\"") }
        val pending =
            http.sendAsync(builder.GET().build(), HttpResponse.BodyHandler { BoundedBody() })
        val response =
            try {
                pending.get(5, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                pending.cancel(true)
                throw failure
            }
        if (response.statusCode() == 304) {
            require(knownVersion != null)
            return AnnouncementSnapshot.Unchanged
        }
        require(response.statusCode() == 200) {
            "Announcement snapshot HTTP ${response.statusCode()}"
        }
        val root = JsonParser.parseString(response.body().toString(Charsets.UTF_8)).asJsonObject
        val revision = root["version"].asString.toLong()
        require(revision >= 0 && (knownVersion == null || revision >= knownVersion))
        val matching =
            root["documents"].asJsonArray.filter {
                val d = it.asJsonObject
                d["namespace"].asString == "announcements" && d["configKey"].asString == "active"
            }
        require(matching.size <= 1)
        val settings =
            if (matching.isEmpty()) null
            else {
                val content = matching.single().asJsonObject.get("contentJson")
                require(
                    content != null && content.isJsonPrimitive && content.asJsonPrimitive.isString
                ) {
                    "Matching announcement document is missing string contentJson"
                }
                AnnouncementSettings.parse(content.asString)
            }
        return AnnouncementSnapshot.Changed(revision, settings)
    }

    override fun close() {
        http.close()
    }

    private class BoundedBody : HttpResponse.BodySubscriber<ByteArray> {
        private val result = CompletableFuture<ByteArray>()
        private val bytes = ByteArrayOutputStream()
        private lateinit var subscription: Flow.Subscription

        override fun getBody(): CompletionStage<ByteArray> = result

        override fun onSubscribe(value: Flow.Subscription) {
            subscription = value
            subscription.request(1)
        }

        override fun onNext(items: List<ByteBuffer>) {
            for (item in items) {
                if (bytes.size().toLong() + item.remaining() > 262144) {
                    subscription.cancel()
                    result.completeExceptionally(IllegalArgumentException("Snapshot exceeds limit"))
                    return
                }
                val chunk = ByteArray(item.remaining())
                item.get(chunk)
                bytes.write(chunk)
            }
            subscription.request(1)
        }

        override fun onError(error: Throwable) {
            result.completeExceptionally(error)
        }

        override fun onComplete() {
            result.complete(bytes.toByteArray())
        }
    }
}
