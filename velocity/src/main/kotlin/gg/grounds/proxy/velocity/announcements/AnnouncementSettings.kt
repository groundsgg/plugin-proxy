package gg.grounds.proxy.velocity.announcements

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.grounds.hud.HudMetrics
import java.util.Locale

data class Announcement(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val serverTypes: List<String>,
    val text: Map<String, String>,
    val address: String?,
) {
    fun caption(locale: Locale): String =
        text[locale.language.lowercase(Locale.ROOT)] ?: text.getValue("en")
}

data class AnnouncementSettings(val announcements: List<Announcement>) {
    companion object {
        fun parse(json: String): AnnouncementSettings {
            require(json.toByteArray(Charsets.UTF_8).size <= 262144) {
                "Announcement document too large"
            }
            try {
                val root = JsonParser.parseString(json).asJsonObject
                root.fields(setOf("schemaVersion", "announcements"))
                require(
                    root["schemaVersion"].isJsonPrimitive &&
                        root["schemaVersion"].asJsonPrimitive.isNumber &&
                        root["schemaVersion"].asBigDecimal.compareTo(java.math.BigDecimal.ONE) == 0
                )
                val entries = root["announcements"].asJsonArray
                require(entries.size() <= 32)
                val announcements =
                    entries.map { node ->
                        val e = node.asJsonObject
                        e.fields(setOf("id", "name", "enabled", "serverTypes", "text", "address"))
                        val id = e["id"].string().lowercase(Locale.ROOT)
                        require(
                            id.matches(
                                Regex(
                                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                                )
                            )
                        )
                        val name = e["name"].string()
                        require(name.isNotBlank() && name.length <= 80)
                        require(e["enabled"].asJsonPrimitive.isBoolean)
                        val enabled = e["enabled"].asBoolean
                        val roles =
                            e["serverTypes"].asJsonArray.map {
                                it.string().also { role ->
                                    require(role.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}")))
                                }
                            }
                        require(
                            roles.size <= 32 &&
                                roles.distinct().size == roles.size &&
                                (!enabled || roles.isNotEmpty())
                        )
                        val texts =
                            e["text"].asJsonObject.entrySet().associate { (lang, value) ->
                                require(lang in setOf("en", "de", "fr", "es"))
                                lang to value.string().also { validateText(it, 256) }
                            }
                        require("en" in texts)
                        val address =
                            e["address"]
                                .takeUnless { it.isJsonNull }
                                ?.string()
                                ?.also { validateText(it, 128) }
                        Announcement(id, name, enabled, roles, texts, address).also { entry ->
                            for (lang in texts.keys) require(
                                gg.grounds.hud.HudPills.width(
                                    AnnouncementLayout.pill(entry, Locale.forLanguageTag(lang))
                                ) <= HudMetrics.layout("maxWidth")
                            ) {
                                "Announcement exceeds HUD width"
                            }
                        }
                    }
                require(announcements.map { it.id }.distinct().size == announcements.size)
                val canonical =
                    com.google.gson
                        .GsonBuilder()
                        .serializeNulls()
                        .disableHtmlEscaping()
                        .create()
                        .toJson(mapOf("schemaVersion" to 1, "announcements" to announcements))
                require(canonical.toByteArray(Charsets.UTF_8).size <= 65536) {
                    "Announcement document too large"
                }
                return AnnouncementSettings(announcements)
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid announcement document", error)
            }
        }

        private fun validateText(value: String, max: Int) {
            require(value.isNotBlank() && value.length <= max)
            value.codePoints().forEach { cp ->
                require(
                    cp != 0xA7 &&
                        !Character.isISOControl(cp) &&
                        Character.getType(cp) !in
                            setOf(Character.FORMAT.toInt(), Character.PRIVATE_USE.toInt()) &&
                        HudMetrics.advance(cp) != null
                ) {
                    "Unsupported HUD text"
                }
            }
        }

        private fun JsonObject.fields(keys: Set<String>) {
            require(keySet() == keys) { "Unexpected or missing announcement fields" }
        }

        private fun JsonElement.string(): String {
            require(isJsonPrimitive && asJsonPrimitive.isString)
            return asString
        }
    }
}
