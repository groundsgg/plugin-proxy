package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonParser
import com.velocitypowered.api.proxy.server.ServerPing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ObjectComponent
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.`object`.SpriteObjectContents
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeMotdSpriteTest {
    private val document =
        JsonParser.parseString(
                """{"nodes":[{"type":"group","style":{"color":"#ffffff","shadowColor":0},"children":[{"type":"text","value":"A"},{"type":"sprite","atlas":"minecraft:gui","sprite":"minecraft:icon/trial_available"},{"type":"text","value":"B"}]}]}"""
            )
            .asJsonObject

    private fun sprites(node: Component): List<ObjectComponent> =
        (if (node is ObjectComponent) listOf(node) else emptyList()) +
            node.children().flatMap(::sprites)

    @Test
    fun `typed source compiles to native sprite and preserves transparent shadow`() {
        val result = CompiledServerText(document).render(emptyMap())
        val sprite = sprites(result).single()
        assertTrue(sprite.contents() is SpriteObjectContents)
        fun shadow(c: Component, inherited: Style): Int? {
            val style = c.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            return if (c is ObjectComponent) style.shadowColor()?.value()
            else c.children().firstNotNullOfOrNull { shadow(it, style) }
        }
        assertEquals(0, shadow(result, Style.empty()))
        assertEquals(
            "minecraft:icon/trial_available",
            (sprite.contents() as SpriteObjectContents).sprite().asString(),
        )
    }

    @Test
    fun `native sprite width participates in center alignment`() {
        val result = CompiledMotd(document, listOf("center")).render(emptyMap(), 100)
        var prefix = 0
        fun walk(c: Component, inherited: Style) {
            val style = c.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            if (c is TextComponent)
                for (char in c.content()) if (char == ' ')
                    prefix +=
                        if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) 5
                        else 4
            c.children().forEach { walk(it, style) }
        }
        walk(result, Style.empty())
        assertEquals(40, prefix)
        assertEquals(1, sprites(result).size)
    }

    @Test
    fun `older protocol omits sprites while keeping adjacent content and network counts`() {
        val template =
            ServerListTemplate.parse(
                JsonParser.parseString(
                        """{"schemaVersion":1,"motd":{"text":$document,"alignments":["left"]},"version":{"mode":"automatic"},"icon":null,"players":{"hidden":false,"online":{"mode":"inherit"},"maximum":{"mode":"inherit"}},"hover":{"mode":"inherit"}}"""
                    )
                    .asJsonObject
            )
        val base =
            ServerPing.builder()
                .version(ServerPing.Version(777, "26.3"))
                .description(Component.empty())
                .onlinePlayers(1)
                .maximumPlayers(100)
                .build()
        for ((protocol, count) in listOf(767 to 17, 777 to 29)) {
            val result =
                ServerListEvaluator.evaluate(
                    base,
                    template,
                    count,
                    protocol,
                    null,
                    "eu",
                    "Europe",
                    compiledMotd = template.motd,
                )
            assertEquals(if (protocol == 777) 1 else 0, sprites(result.descriptionComponent!!).size)
            assertEquals(count, result.players.get().online)
            fun literal(c: Component): String =
                (if (c is TextComponent) c.content() else "") +
                    c.children().joinToString("") { literal(it) }
            assertEquals("AB", literal(result.descriptionComponent!!))
            if (protocol < 773)
                assertEquals(
                    "AB",
                    PlainTextComponentSerializer.plainText()
                        .serialize(result.descriptionComponent!!),
                )
        }
    }

    @Test
    fun `uneven gradients retain and tint native sprite objects`() {
        val copy = document.deepCopy()
        copy
            .getAsJsonArray("nodes")[0]
            .asJsonObject
            .getAsJsonObject("style")
            .add(
                "gradient",
                JsonParser.parseString(
                    """[{"color":"#ff0000","position":0},{"color":"#00ff00","position":0.2},{"color":"#0000ff","position":1}]"""
                ),
            )
        val result = CompiledServerText(copy).render(emptyMap())
        assertEquals(1, sprites(result).size)
        fun color(c: Component, inherited: Style): String? {
            val style = c.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            return if (c is ObjectComponent) style.color()?.asHexString()?.lowercase()
            else c.children().firstNotNullOfOrNull { color(it, style) }
        }
        assertEquals("#009f60", color(result, Style.empty()))
    }
}
