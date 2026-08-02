package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * The network's MOTD, as stored in service-config under `motd/active`.
 *
 * One document for the whole network rather than one per proxy: a player picks a region without
 * being told, so a MOTD that differs by which proxy answered would look like a bug. Where the text
 * *should* differ by region it says so itself, through the placeholders in [MotdPlaceholders].
 *
 * [text] is MiniMessage and carries its own line break — `<newline>` when it was typed, a plain
 * newline when it came from motd.gg — because a Minecraft server list shows two lines. One string
 * rather than a list of lines is what lets the whole thing come straight off the command line and
 * out of the import without a separator convention nobody would remember.
 *
 * [maxPlayers] is the number on the right of the slash. Null leaves Velocity's own value alone,
 * which is the honest default — a made-up cap is a decision, not a fallback.
 */
data class MotdDocument(
    val text: String,
    val maxPlayers: Int? = null,
    val source: String? = null,
    val updatedBy: String? = null,
    val updatedAt: String? = null,
) {

    fun toJson(): String {
        val json = JsonObject()
        json.addProperty("text", text)
        maxPlayers?.let { json.addProperty("maxPlayers", it) }
        source?.let { json.addProperty("source", it) }
        updatedBy?.let { json.addProperty("updatedBy", it) }
        updatedAt?.let { json.addProperty("updatedAt", it) }
        return json.toString()
    }

    companion object {
        /**
         * Reads a stored document, or throws [MotdFormatException] when it is not one.
         *
         * Hand-built off a [JsonObject] rather than reflected into the data class: this plugin is
         * shaded into a proxy alongside others, and reflective binding is the part of that which
         * breaks quietly. A missing optional field is absent, not null — anything the dashboard
         * adds later is ignored here rather than failing the read.
         */
        fun fromJson(raw: String): MotdDocument {
            val root =
                try {
                    JsonParser.parseString(raw)
                } catch (ex: JsonSyntaxException) {
                    throw MotdFormatException("stored MOTD is not JSON: ${ex.message}")
                }
            if (!root.isJsonObject) {
                throw MotdFormatException("stored MOTD is not a JSON object")
            }
            val json = root.asJsonObject
            val text =
                json.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: throw MotdFormatException("stored MOTD has no 'text'")
            return MotdDocument(
                text = text,
                maxPlayers = json.get("maxPlayers")?.takeIf { it.isJsonPrimitive }?.asInt,
                source = json.get("source")?.takeIf { it.isJsonPrimitive }?.asString,
                updatedBy = json.get("updatedBy")?.takeIf { it.isJsonPrimitive }?.asString,
                updatedAt = json.get("updatedAt")?.takeIf { it.isJsonPrimitive }?.asString,
            )
        }
    }
}

/** A stored or imported MOTD that could not be read. Carries a message fit to show in chat. */
class MotdFormatException(message: String) : RuntimeException(message)
