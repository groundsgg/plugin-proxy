package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/**
 * Imports a MOTD designed on [motd.gg](https://motd.gg).
 *
 * motd.gg is a MOTD editor with a live server-list preview; its own Bukkit plugin saves the MOTD
 * there and applies it back by writing `server.properties`. There is no proxy equivalent and no
 * write path we would want — a Velocity network has no `server.properties` and the MOTD belongs in
 * service-config, not on a pod's disk. So only the read half is mirrored here: design it there,
 * `/motd import` it, and it is stored like any other MOTD.
 *
 * Read-only and unauthenticated, which is what the endpoint is: `GET https://motd.gg/<id>.json`
 * returns the same document the editor loads. Nothing is uploaded, so importing does not publish
 * this network's MOTD anywhere.
 */
class MotdGgClient(private val userAgent: String, private val http: HttpClient = defaultClient()) {

    /**
     * Fetches the MOTD with this id and returns it as MiniMessage.
     *
     * @param idOrUrl either the id or the URL it appears in — `AbC123`, `https://motd.gg/AbC123`,
     *   and `motd.gg/AbC123.json` are all accepted, because all three are things someone will paste
     *   out of a browser.
     * @throws MotdFormatException when the input is not an id, the id is unknown, or the response
     *   is not a MOTD. The message is written to be shown in chat.
     */
    fun import(idOrUrl: String): Imported {
        val id =
            parseId(idOrUrl) ?: throw MotdFormatException("'$idOrUrl' is not a motd.gg id or link")

        val response =
            try {
                http.send(
                    HttpRequest.newBuilder(URI.create("$BASE_URL/$id.json"))
                        .header("Accept", "application/json")
                        .header("User-Agent", userAgent)
                        .timeout(TIMEOUT)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            } catch (ex: Exception) {
                throw MotdFormatException("could not reach motd.gg: ${ex.message}")
            }

        if (response.statusCode() == 404) {
            throw MotdFormatException("motd.gg does not know '$id'")
        }
        if (response.statusCode() != 200) {
            throw MotdFormatException("motd.gg answered ${response.statusCode()}")
        }

        return parse(id, response.body())
    }

    /**
     * The id inside anything someone might paste, or null when there is none.
     *
     * Visible for testing. The editor URL, the bare id and the `.json` the endpoint serves all name
     * the same document, so all three are accepted.
     */
    internal fun parseId(idOrUrl: String): String? =
        ID_PATTERN.matchEntire(idOrUrl.trim())?.groupValues?.get(1)

    /** Visible for testing — the half of [import] that does not do I/O. */
    internal fun parse(id: String, body: String): Imported {
        // An unknown id does not 404: motd.gg serves its editor page for anything it does not
        // recognise, so a stray character in a pasted id arrives here as HTML rather than as an
        // error. Saying so beats a JSON parse failure nobody can act on.
        if (body.trimStart().startsWith("<")) {
            throw MotdFormatException("motd.gg does not know '$id'")
        }
        val json =
            try {
                JsonParser.parseString(body)
            } catch (ex: JsonSyntaxException) {
                throw MotdFormatException("motd.gg returned something that is not JSON")
            }
        if (!json.isJsonObject) {
            throw MotdFormatException("motd.gg returned something that is not a MOTD")
        }
        val text =
            json.asJsonObject.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                ?: throw MotdFormatException("the motd.gg document '$id' has no text")
        val name = json.asJsonObject.get("name")?.takeIf { it.isJsonPrimitive }?.asString
        val hasFavicon = json.asJsonObject.get("favicon")?.isJsonNull == false

        return Imported(
            id = id,
            name = name,
            text = MiniMessage.miniMessage().serialize(LEGACY.deserialize(text)),
            hasFavicon = hasFavicon,
        )
    }

    /**
     * A MOTD as motd.gg holds it, converted to the form this plugin stores.
     *
     * [hasFavicon] only reports that the document carried a server icon. Importing it is not
     * implemented — the network's icon is brand, served from the CDN, and is not something a MOTD
     * edit should change — but silently dropping it would look like the import lost something.
     */
    data class Imported(
        val id: String,
        val name: String?,
        val text: String,
        val hasFavicon: Boolean,
    )

    private companion object {
        const val BASE_URL = "https://motd.gg"
        val TIMEOUT: Duration = Duration.ofSeconds(8)

        /**
         * The forms a motd.gg document gets named in: the bare id, the editor link, and the `.json`
         * the endpoint serves.
         *
         * The host and the extension are one optional group each and both are spelled out, rather
         * than the looser `(?:\..*)?` motd.gg's own plugin ends with. Left loose, a link to some
         * other site parses as an id — `https://example.com/AbC123` yields `example` — and the
         * import then fails as "motd.gg does not know 'example'" instead of "that is not a motd.gg
         * link".
         */
        val ID_PATTERN = Regex("(?:(?:https?://)?motd\\.gg/)?([a-zA-Z0-9]+)(?:\\.json)?")

        /**
         * motd.gg stores the MOTD the way `server.properties` holds it: section-sign colour codes,
         * hex written as the `§x§r§r§g§g§b§b` run that legacy clients understand.
         */
        val LEGACY: LegacyComponentSerializer =
            LegacyComponentSerializer.builder()
                .character(LegacyComponentSerializer.SECTION_CHAR)
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build()

        fun defaultClient(): HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
    }
}
