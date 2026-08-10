package gg.grounds.proxy.velocity.motd

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * The MOTD's home in service-config: one document, `motd/active`, under a fixed app and the
 * deployment's environment.
 *
 * Reads go to the consumer API, which any authenticated caller may use; writes go to the admin API,
 * which service-config restricts to admin service accounts and to writers explicitly allowed for
 * this app. A proxy that is not on that list can still show the MOTD — it just cannot change it,
 * and `/motd set` says so rather than failing silently.
 *
 * Deliberately not built on the shared `plugin-config` client: that one reads only, so it cannot do
 * the writing half at all.
 */
class MotdConfigStore(
    private val app: String,
    private val env: String,
    private val baseUri: URI,
    private val http: HttpClient,
) : MotdStore, AutoCloseable {

    /**
     * The stored MOTD, or null when none is set. Null is the normal state of a fresh network, not
     * an error — the caller then leaves Velocity's own MOTD alone.
     */
    override fun read(): MotdDocument? {
        val response = send(request(consumerPath()).GET())
        if (response.statusCode() == 404) return null
        requireSuccess(response, "read the MOTD")
        val document =
            GSON.fromJson(response.body(), JsonObject::class.java)?.get("contentJson")?.asString
                ?: return null
        return MotdDocument.fromJson(document)
    }

    /** Stores [document] as the network's MOTD, replacing whatever was there. */
    override fun write(document: MotdDocument, updatedBy: String) {
        // No expectedVersion: two operators racing on /motd is a coin flip either way, and a
        // rejected write that says "someone else changed it, try again" is worse in chat than the
        // second one simply winning. The dashboard, which can show the conflict, is where
        // optimistic concurrency earns its keep.
        val body = GSON.toJson(mapOf("contentJson" to document.toJson(), "updatedBy" to updatedBy))
        val response =
            send(
                request(adminPath())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
            )
        requireSuccess(response, "set the MOTD")
    }

    /** Removes the stored MOTD. Returns true when there was one to remove. */
    override fun clear(deletedBy: String): Boolean {
        val response = send(request(adminPath()).DELETE())
        requireSuccess(response, "clear the MOTD")
        return GSON.fromJson(response.body(), JsonObject::class.java)?.get("deleted")?.asBoolean
            ?: false
    }

    override fun close() {
        http.close()
    }

    private fun consumerPath() =
        "/v1/config/apps/$app/envs/$env/namespaces/$NAMESPACE/documents/$CONFIG_KEY"

    private fun adminPath() =
        "/v1/config/admin/apps/$app/envs/$env/namespaces/$NAMESPACE/documents/$CONFIG_KEY"

    private fun request(path: String): HttpRequest.Builder {
        val builder =
            HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(DEADLINE)
                .header("Accept", "application/json")
        readToken()?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private fun send(builder: HttpRequest.Builder): HttpResponse<String> =
        http.send(builder.build(), HttpResponse.BodyHandlers.ofString())

    /**
     * Non-2xx becomes an exception carrying what service-config wrote for a human — a caller that
     * is not allowed to write says so in the problem's `detail`, and `/motd` shows that line rather
     * than a status code.
     */
    private fun requireSuccess(response: HttpResponse<String>, action: String) {
        if (response.statusCode() in 200..299) return
        val detail =
            runCatching {
                    GSON.fromJson(response.body(), JsonObject::class.java)?.get("detail")?.asString
                }
                .getOrNull()
        throw MotdStoreException(detail ?: "Could not $action (HTTP ${response.statusCode()})")
    }

    companion object {
        const val NAMESPACE = "motd"
        const val CONFIG_KEY = "active"

        private val GSON = Gson()
        private val DEADLINE: Duration = Duration.ofSeconds(5)

        /**
         * Where the projected ServiceAccount token is mounted. The kubelet rotates it well before
         * expiry, so it is read per call rather than held — a proxy that stays up longer than an
         * hour would otherwise start presenting an expired one.
         */
        private const val DEFAULT_TOKEN_PATH = "/var/run/secrets/grounds/token"

        fun open(app: String, env: String, target: String): MotdConfigStore {
            // The chart injects the address with no scheme; java.net.http throws parsing that
            // directly, so default to http.
            val baseUri = URI.create(if (target.contains("://")) target else "http://$target")
            return MotdConfigStore(app, env, baseUri, HttpClient.newHttpClient())
        }

        /**
         * A missing token is sent unauthenticated on purpose: locally there is no projected volume
         * and service-config runs with auth off, and in the cluster the server rejecting the call
         * is a clearer failure than the client refusing to make it.
         */
        private fun readToken(): String? {
            val path = Path.of(System.getenv("GROUNDS_TOKEN_FILE") ?: DEFAULT_TOKEN_PATH)
            return try {
                if (Files.exists(path)) Files.readString(path).trim().ifEmpty { null } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** A refusal or an outage from service-config, carrying the line it wrote for a human. */
class MotdStoreException(message: String) : RuntimeException(message)
