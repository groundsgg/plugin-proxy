package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonObject
import com.velocitypowered.api.util.Favicon
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** Validated immutable runtime snapshot. Images and static text are decoded at refresh time. */
data class ServerListTemplate(
    val json: JsonObject,
    val versionMode: String,
    val versionText: CompiledServerText?,
    val icon: Favicon?,
    val hidden: Boolean,
    val online: ServerCountRule,
    val maximum: ServerCountRule,
    val hoverMode: String,
    val hoverText: CompiledServerText?,
    val motd: CompiledMotd,
) {
    companion object {
        fun parse(json: JsonObject): ServerListTemplate {
            require(json.toString().length <= 262144) { "Server-list template is too large" }
            require(json.get("schemaVersion").asInt == 1) { "Unsupported server-list schema" }
            val version = json.getAsJsonObject("version")
            val versionMode = version.get("mode").asString
            require(versionMode in setOf("automatic", "when-incompatible", "always"))
            val players = json.getAsJsonObject("players")
            val hover = json.getAsJsonObject("hover")
            val hoverMode = hover.get("mode").asString
            require(hoverMode in setOf("inherit", "disabled", "custom"))
            val icon =
                json
                    .get("icon")
                    ?.takeUnless { it.isJsonNull }
                    ?.asJsonObject
                    ?.let {
                        val encoded = it.get("pngBase64").asString
                        require(encoded.length <= 131072)
                        val bytes = Base64.getDecoder().decode(encoded)
                        require(
                            bytes.size >= 24 &&
                                bytes.take(8) ==
                                    listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
                        )
                        // Bound dimensions before ImageIO allocates image memory.
                        val header = java.nio.ByteBuffer.wrap(bytes)
                        require(header.getInt(16) == 64 && header.getInt(20) == 64)
                        val image =
                            ImageIO.read(ByteArrayInputStream(bytes)) ?: error("Invalid PNG")
                        require(image.width == 64 && image.height == 64)
                        Favicon.create(image)
                    }
            return ServerListTemplate(
                json.deepCopy(),
                versionMode,
                if (versionMode == "automatic") null
                else CompiledServerText(version.getAsJsonObject("text")),
                icon,
                players.get("hidden").asBoolean,
                ServerCountRule.parse(players.getAsJsonObject("online"), false),
                ServerCountRule.parse(players.getAsJsonObject("maximum"), true),
                hoverMode,
                if (hoverMode == "custom") CompiledServerText(hover.getAsJsonObject("text"))
                else null,
                CompiledMotd(
                    json.getAsJsonObject("motd").getAsJsonObject("text"),
                    json.getAsJsonObject("motd").getAsJsonArray("alignments").map { it.asString },
                ),
            )
        }
    }
}

data class ServerCountRule(val mode: String, val value: Double = 0.0) {
    fun apply(base: Int, online: Int = base): Int {
        val result =
            when (mode) {
                "fixed" -> value
                "offset" -> base + value
                "percent" -> kotlin.math.ceil(base * value / 100)
                "headroom" -> online + value
                else -> base.toDouble()
            }
        return result.coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()
    }

    companion object {
        fun parse(json: JsonObject, maximum: Boolean): ServerCountRule {
            val mode = json.get("mode").asString
            require(
                mode in setOf("inherit", "fixed", "offset", "percent") ||
                    maximum && mode == "headroom"
            )
            val value = if (mode == "inherit") 0.0 else json.get("value").asDouble
            require(value.isFinite() && (mode == "offset" || value >= 0))
            require(mode == "percent" || value % 1 == 0.0)
            return ServerCountRule(mode, value)
        }
    }
}

/** Native Adventure handles protected MiniMessage subtrees. Templates never contain callbacks. */
class CompiledServerText private constructor(private val source: String) {
    constructor(document: JsonObject) : this(compile(document))

    private val static = if (PLACEHOLDER.containsMatchIn(source)) null else MM.deserialize(source)

    fun render(values: Map<String, String>): Component =
        static
            ?: MM.deserialize(
                PLACEHOLDER.replace(source) { match ->
                    values[match.groupValues[1].ifEmpty { match.groupValues[2] }]?.let(
                        MM::escapeTags
                    ) ?: match.value
                }
            )

    fun legacy(values: Map<String, String>): String =
        LegacyComponentSerializer.legacySection().serialize(render(values))

    fun legacyLines(values: Map<String, String>): List<String> =
        componentLines(render(values)).map {
            LegacyComponentSerializer.legacySection().serialize(it)
        }

    companion object {
        private val PLACEHOLDER = Regex("\\{\\{([A-Za-z0-9_]+)}}|\\{(reason|end|remaining)}")

        fun fromMiniMessage(source: String) = CompiledServerText(source)

        private val MM = MiniMessage.miniMessage()

        private fun compile(document: JsonObject): String {
            var budget = 0
            fun node(n: JsonObject, depth: Int): String {
                require(++budget <= 100000 && depth <= 128)
                return when (n.get("type").asString) {
                    "text" -> escape(n.get("value").asString)
                    "newline" -> "<newline>"
                    "placeholder" -> "{{${n.get("name").asString}}}"
                    "opaque" ->
                        when (n.get("format").asString) {
                            "minimessage" -> n.get("raw").asString
                            "legacy-section" ->
                                MM.serialize(
                                    LegacyComponentSerializer.legacySection()
                                        .deserialize(n.get("raw").asString)
                                )
                            "legacy-ampersand" ->
                                MM.serialize(
                                    LegacyComponentSerializer.legacyAmpersand()
                                        .deserialize(n.get("raw").asString)
                                )
                            else -> escape(n.get("fallback")?.asString ?: n.get("raw").asString)
                        }
                    "group" -> {
                        val style = n.getAsJsonObject("style")
                        var text =
                            n.getAsJsonArray("children").joinToString("") {
                                node(it.asJsonObject, depth + 1)
                            }
                        style.get("color")?.asString?.let { text = "<$it>$text</$it>" }
                        style.getAsJsonArray("gradient")?.let { stops ->
                            require(stops.size() >= 2)
                            val colors =
                                stops.joinToString(":") { it.asJsonObject.get("color").asString }
                            val phase = style.get("gradientPhase")?.asDouble ?: 0.0
                            // Native MiniMessage is authoritative for uniform gradients.
                            val uneven =
                                stops.withIndex().any { (i, s) ->
                                    kotlin.math.abs(
                                        s.asJsonObject.get("position").asDouble -
                                            i.toDouble() / (stops.size() - 1)
                                    ) > 1e-6
                                }
                            if (uneven && phase == 0.0) {
                                val rendered = MM.deserialize(text)
                                val flat =
                                    mutableListOf<
                                        Pair<String, net.kyori.adventure.text.format.Style>
                                    >()
                                fun flatten(
                                    c: Component,
                                    inherited: net.kyori.adventure.text.format.Style,
                                ) {
                                    val effective =
                                        c.style()
                                            .merge(
                                                inherited,
                                                net.kyori.adventure.text.format.Style.Merge.Strategy
                                                    .IF_ABSENT_ON_TARGET,
                                            )
                                    if (c is net.kyori.adventure.text.TextComponent)
                                        c.content().codePoints().forEach {
                                            flat.add(String(Character.toChars(it)) to effective)
                                        }
                                    c.children().forEach { flatten(it, effective) }
                                }
                                flatten(rendered, net.kyori.adventure.text.format.Style.empty())
                                val visible = flat.count { it.first != "\n" }
                                var index = 0
                                text =
                                    MM.serialize(
                                        flat.fold(Component.empty() as Component) {
                                            acc,
                                            (char, oldStyle) ->
                                            if (char == "\n")
                                                acc.append(Component.text(char).style(oldStyle))
                                            else {
                                                val t =
                                                    if (visible <= 1) 0.0
                                                    else index++.toDouble() / (visible - 1)
                                                val right =
                                                    (1 until stops.size()).firstOrNull {
                                                        t <=
                                                            stops[it]
                                                                .asJsonObject
                                                                .get("position")
                                                                .asDouble
                                                    } ?: stops.size() - 1
                                                val a = stops[right - 1].asJsonObject
                                                val b = stops[right].asJsonObject
                                                val start = a.get("position").asDouble
                                                val end = b.get("position").asDouble
                                                val fraction =
                                                    ((t - start) / (end - start)).coerceIn(0.0, 1.0)
                                                val from =
                                                    Integer.parseInt(
                                                        a.get("color").asString.drop(1),
                                                        16,
                                                    )
                                                val to =
                                                    Integer.parseInt(
                                                        b.get("color").asString.drop(1),
                                                        16,
                                                    )
                                                fun channel(shift: Int) =
                                                    kotlin.math
                                                        .floor(
                                                            ((from shr shift) and 255) +
                                                                ((((to shr shift) and 255) -
                                                                    ((from shr shift) and 255)) *
                                                                    fraction) +
                                                                0.5000000001
                                                        )
                                                        .toInt()
                                                val color =
                                                    net.kyori.adventure.text.format.TextColor.color(
                                                        channel(16),
                                                        channel(8),
                                                        channel(0),
                                                    )
                                                acc.append(
                                                    Component.text(char)
                                                        .style(
                                                            if (
                                                                oldStyle.color() == null ||
                                                                    oldStyle
                                                                        .color()
                                                                        ?.asHexString() ==
                                                                        style.get("color")?.asString
                                                            )
                                                                oldStyle.color(color)
                                                            else oldStyle
                                                        )
                                                )
                                            }
                                        }
                                    )
                            } else text = "<gradient:$colors:$phase>$text</gradient>"
                        }
                        style.getAsJsonObject("rainbow")?.let { r ->
                            text =
                                "<rainbow:${if(r.get("reversed").asBoolean)"!" else ""}${r.get("phase").asDouble}>$text</rainbow>"
                        }
                        mapOf(
                                "bold" to "b",
                                "italic" to "i",
                                "underlined" to "u",
                                "strikethrough" to "st",
                                "obfuscated" to "obf",
                            )
                            .forEach { (key, tag) ->
                                style.get(key)?.asBoolean?.let {
                                    text = "<${if(it)"" else "!"}$tag>$text</$tag>"
                                }
                            }
                        text
                    }
                    else -> error("Invalid text node")
                }
            }
            return document.getAsJsonArray("nodes").joinToString("") { node(it.asJsonObject, 0) }
        }

        private fun escape(text: String) = text.replace("\\", "\\\\").replace("<", "\\<")
    }
}
