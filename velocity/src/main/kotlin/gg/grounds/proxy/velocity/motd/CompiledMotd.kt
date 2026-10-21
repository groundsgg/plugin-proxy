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

    fun render(values: Map<String, String>): Component {
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
                                    char != " " &&
                                        style.decoration(TextDecoration.BOLD) ==
                                            TextDecoration.State.TRUE
                                )
                                    1
                                else 0)
                    }
                node.children().forEach { measure(it, style) }
            }
            measure(line, Style.empty())
            val remaining = (271 - width).coerceAtLeast(0)
            val alignment = alignments.getOrNull(index) ?: "left"
            val spaces =
                when (alignment) {
                    "center" -> kotlin.math.floor(remaining / 8.0 + .5).toInt()
                    "right" -> kotlin.math.floor(remaining / 4.0 + .5).toInt()
                    else -> 0
                }
            if (index > 0) result = result.append(Component.newline())
            result = result.append(Component.text(" ".repeat(spaces))).append(line)
        }
        return result
    }

    companion object {
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
