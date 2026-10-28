package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration

/** Splits resolved components without carrying implicit style across independent client values. */
internal fun componentLines(component: Component): List<Component> {
    val lines = mutableListOf<Component>()
    var line: Component = Component.empty()
    fun walk(node: Component, inherited: Style) {
        val style = node.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
        if (node is TextComponent) {
            node.content().codePoints().forEach { point ->
                if (point == 10) {
                    lines.add(line)
                    line = Component.empty()
                } else
                    line =
                        line.append(Component.text(String(Character.toChars(point))).style(style))
            }
        } else line = line.append(node.children(emptyList()).style(style))
        node.children().forEach { walk(it, style) }
    }
    walk(component, Style.empty())
    lines.add(line)
    return lines
}

/** Resolve native effects/placeholders before applying the shared profile's pixel spacing. */
class CompiledMotd(document: JsonObject, private val alignments: List<String>) {
    private val text = CompiledServerText(document)

    fun render(values: Map<String, String>, lineWidth: Int = 271): Component {
        val rendered = text.render(values)
        if (alignments.all { it == "left" }) return rendered
        var result: Component = Component.empty()
        for ((index, line) in componentLines(rendered).withIndex()) {
            var width = 0
            fun measure(node: Component, inherited: Style) {
                val style = node.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
                if (node is TextComponent)
                    node.content().codePoints().forEach { point ->
                        val char = String(Character.toChars(point))
                        width +=
                            (advances[char] ?: 6) +
                                (if (
                                    style.decoration(TextDecoration.BOLD) ==
                                        TextDecoration.State.TRUE
                                )
                                    1
                                else 0)
                    }
                node.children().forEach { measure(it, style) }
            }
            measure(line, Style.empty())
            val remaining = (lineWidth - width).coerceAtLeast(0)
            val alignment = alignments.getOrNull(index) ?: "left"
            val target =
                when (alignment) {
                    "center" -> remaining / 2.0
                    "right" -> remaining.toDouble()
                    else -> 0.0
                }
            val (regular, bold) = padding(target, remaining)
            if (index > 0) result = result.append(Component.newline())
            if (regular > 0)
                result =
                    result.append(
                        Component.text(" ".repeat(regular)).decoration(TextDecoration.BOLD, false)
                    )
            if (bold > 0)
                result =
                    result.append(
                        Component.text(" ".repeat(bold)).decoration(TextDecoration.BOLD, true)
                    )
            result = result.append(line)
        }
        return result
    }

    companion object {
        /** Same bounded 4/5-pixel native space search as library-motd. */
        private fun padding(target: Double, available: Int): Pair<Int, Int> {
            var regular = 0
            var bold = 0
            var advance = 0
            var error = kotlin.math.abs(target)
            for (b in 0..3) {
                val near = (target - b * 5) / 4
                for (r in listOf(kotlin.math.floor(near).toInt(), kotlin.math.ceil(near).toInt())) {
                    val candidate = r * 4 + b * 5
                    if (r < 0 || candidate > available) continue
                    val distance = kotlin.math.abs(candidate - target)
                    if (
                        distance < error ||
                            (distance == error &&
                                (candidate > advance || (candidate == advance && b < bold)))
                    ) {
                        regular = r
                        bold = b
                        advance = candidate
                        error = distance
                    }
                }
            }
            return regular to bold
        }

        private val advances: Map<String, Int> =
            CompiledMotd::class
                .java
                .getResourceAsStream("/motd/java-1.21.1-advances.json")!!
                .reader()
                .use { reader ->
                    JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (key, value)
                        ->
                        key to value.asInt
                    }
                }
    }
}
