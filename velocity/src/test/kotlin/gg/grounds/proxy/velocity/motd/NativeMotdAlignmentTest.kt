package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonParser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeMotdAlignmentTest {
    private fun characters(component: Component): List<Pair<String, Boolean>> {
        val chars = mutableListOf<Pair<String, Boolean>>()
        fun walk(node: Component, inherited: Style) {
            val style = node.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            if (node is TextComponent)
                node.content().codePoints().forEach {
                    chars.add(
                        String(Character.toChars(it)) to
                            (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE)
                    )
                }
            node.children().forEach { walk(it, style) }
        }
        walk(component, Style.empty())
        return chars
    }

    @Test
    fun `matches the shared native padding and resolved placeholder fixtures`() {
        val samples =
            JsonParser.parseReader(
                    javaClass.getResourceAsStream("/server-list/native-padding.json")!!.reader()
                )
                .asJsonArray
        val advances =
            JsonParser.parseReader(
                    javaClass.getResourceAsStream("/motd/java-1.21.1-advances.json")!!.reader()
                )
                .asJsonObject
        for (sample in samples) {
            val s = sample.asJsonObject
            val source = s.get("source").asString
            val document =
                JsonParser.parseString(
                        """{"nodes":[{"type":"opaque","format":"minimessage","raw":${s.get("source")}}]}"""
                    )
                    .asJsonObject
            val values =
                s.getAsJsonObject("values").entrySet().associate { it.key to it.value.asString }
            val result =
                CompiledMotd(document, s.getAsJsonArray("alignments").map { it.asString })
                    .render(values, s.get("width").asInt)
            val lines = componentLines(result)
            assertEquals(s.getAsJsonArray("expected").size(), lines.size, s.get("id").asString)
            for ((index, line) in lines.withIndex()) {
                val expected = s.getAsJsonArray("expected")[index].asJsonObject
                val chars = characters(line)
                val count = expected.get("regular").asInt + expected.get("bold").asInt
                val prefix = chars.take(count)
                assertTrue(prefix.all { it.first == " " }, source)
                assertEquals(expected.get("bold").asInt, prefix.count { it.second }, source)
                assertEquals(
                    expected.get("padding").asInt,
                    prefix.sumOf { if (it.second) 5 else 4 },
                    source,
                )
                assertEquals(
                    expected.get("width").asInt,
                    chars.drop(count).sumOf {
                        (advances[it.first]?.asInt ?: 6) + if (it.second) 1 else 0
                    },
                    source,
                )
                assertFalse(chars.joinToString("") { it.first }.contains("{{"), source)
            }
        }
    }

    @Test
    fun `uses the closest representable padding for short and full-width lines`() {
        val document =
            JsonParser.parseString("""{"nodes":[{"type":"text","value":"A"}]}""").asJsonObject
        for (alignment in listOf("center", "right")) for (remaining in 0..280) {
            val target = if (alignment == "center") remaining / 2.0 else remaining.toDouble()
            val candidates =
                (0..3)
                    .flatMap { b -> (0..(remaining / 4)).map { r -> r * 4 + b * 5 } }
                    .filter { it <= remaining }
            val expected =
                candidates.minWith(
                    compareBy<Int> { kotlin.math.abs(it - target) }.thenByDescending { it }
                )
            val chars =
                characters(
                    CompiledMotd(document, listOf(alignment)).render(emptyMap(), remaining + 6)
                )
            assertEquals("A", chars.last().first)
            assertFalse(chars.last().second, "padding bold must not affect the MOTD")
            assertEquals(
                expected,
                chars.dropLast(1).sumOf { if (it.second) 5 else 4 },
                "$alignment $remaining",
            )
        }
    }
}
