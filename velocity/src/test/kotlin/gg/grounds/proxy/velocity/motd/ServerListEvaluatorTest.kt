package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonParser
import com.velocitypowered.api.proxy.server.ServerPing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ServerListEvaluatorTest {
    private fun canonicalLegacy(source: String) =
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().let {
            it.serialize(it.deserialize(source))
        }

    @Test
    fun `evaluates displayed count before resolving placeholders`() {
        val template =
            ServerListTemplate.parse(
                JsonParser.parseString(
                        """{"schemaVersion":1,"motd":{"text":{"nodes":[]},"alignments":[]},"version":{"mode":"automatic"},"icon":null,"players":{"hidden":false,"online":{"mode":"offset","value":2},"maximum":{"mode":"headroom","value":10}},"hover":{"mode":"inherit"}}"""
                    )
                    .asJsonObject
            )
        val base =
            ServerPing.builder()
                .version(ServerPing.Version(767, "1.21.1"))
                .onlinePlayers(3)
                .maximumPlayers(100)
                .description(Component.empty())
                .build()
        val result =
            ServerListEvaluator.evaluate(
                base,
                template,
                8,
                767,
                "{{players}} / {{max}}",
                "eu",
                "Europe",
            )
        assertEquals(10, result.players.get().online)
        assertEquals(20, result.players.get().max)
        assertEquals(
            "10 / 20",
            PlainTextComponentSerializer.plainText().serialize(result.descriptionComponent!!),
        )
    }

    @Test
    fun `aligns MOTD to the actual modern client content width`() {
        val template =
            ServerListTemplate.parse(
                JsonParser.parseString(
                        """{"schemaVersion":1,"motd":{"text":{"nodes":[{"type":"text","value":"A"}]},"alignments":["right"]},"version":{"mode":"automatic"},"icon":null,"players":{"hidden":false,"online":{"mode":"inherit"},"maximum":{"mode":"inherit"}},"hover":{"mode":"inherit"}}"""
                    )
                    .asJsonObject
            )
        val base =
            ServerPing.builder()
                .version(ServerPing.Version(777, "26.3"))
                .description(Component.empty())
                .onlinePlayers(8)
                .maximumPlayers(100)
                .build()
        for ((protocol, spaces) in listOf(767 to 66, 774 to 65, 775 to 65, 776 to 65, 777 to 65)) {
            val result =
                ServerListEvaluator.evaluate(
                    base,
                    template,
                    8,
                    protocol,
                    "A",
                    "eu",
                    "Europe",
                    compiledMotd = template.motd,
                )
            assertEquals(
                " ".repeat(spaces) + "A",
                PlainTextComponentSerializer.plainText().serialize(result.descriptionComponent!!),
                "protocol $protocol",
            )
        }
    }

    @Test
    fun `agrees with independently generated JavaScript status fixtures`() {
        val fixtures =
            JsonParser.parseReader(
                    javaClass.getResourceAsStream("/server-list/contract.json")!!.reader()
                )
                .asJsonArray
        for (entry in fixtures) {
            val f = entry.asJsonObject
            val ctx = f.getAsJsonObject("context")
            val expected = f.getAsJsonObject("expected")
            val template = ServerListTemplate.parse(f.getAsJsonObject("template"))
            val base =
                ServerPing.builder()
                    .version(ServerPing.Version(767, "1.21.1"))
                    .onlinePlayers(3)
                    .maximumPlayers(100)
                    .samplePlayers(
                        ServerPing.SamplePlayer(
                            "Ada",
                            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        )
                    )
                    .description(Component.empty())
                    .build()
            val actual =
                ServerListEvaluator.evaluate(
                    base,
                    template,
                    8,
                    ctx.get("clientProtocol").asInt,
                    f.get("source").asString,
                    "eu",
                    "Europe",
                    compiledMotd = template.motd,
                )
            assertEquals(
                expected.getAsJsonObject("version").get("protocol").asInt,
                actual.version.protocol,
                f.get("name").asString,
            )
            assertEquals(
                canonicalLegacy(expected.getAsJsonObject("version").get("name").asString),
                canonicalLegacy(actual.version.name),
            )
            if (expected.has("players")) {
                val p = expected.getAsJsonObject("players")
                assertEquals(p.get("online").asInt, actual.players.get().online)
                assertEquals(p.get("max").asInt, actual.players.get().max)
                assertEquals(
                    p.getAsJsonArray("sample").map {
                        canonicalLegacy(it.asJsonObject.get("name").asString)
                    },
                    actual.players.get().sample.map { canonicalLegacy(it.name) },
                )
            } else assertTrue(actual.players.isEmpty)
            assertEquals(
                f.get("expectedPlain").asString,
                PlainTextComponentSerializer.plainText().serialize(actual.descriptionComponent!!),
            )
        }
    }

    @Test
    fun `sample lines carry inherited colors independently`() {
        val text =
            CompiledServerText(
                com.google.gson.JsonParser.parseString(
                        """{"nodes":[{"type":"group","style":{"color":"#ff5555"},"children":[{"type":"text","value":"first"},{"type":"newline"},{"type":"text","value":"second"}]}]}"""
                    )
                    .asJsonObject
            )
        assertTrue(text.legacyLines(emptyMap())[1].contains("§c"))
    }

    @Test
    fun `runtime alignment resolves placeholders before measuring text`() {
        val json =
            JsonParser.parseReader(
                    javaClass.getResourceAsStream("/server-list/contract.json")!!.reader()
                )
                .asJsonArray[0]
                .asJsonObject
                .getAsJsonObject("template")
                .deepCopy()
        json
            .getAsJsonObject("motd")
            .add("alignments", JsonParser.parseString("""["center","left"]"""))
        val template = ServerListTemplate.parse(json)
        val rendered =
            PlainTextComponentSerializer.plainText()
                .serialize(
                    template.motd.render(mapOf("region" to "eu", "players" to "8", "max" to "100"))
                )
        assertFalse(rendered.contains("{{"))
        assertTrue(rendered.startsWith(" "))
        assertEquals("eu: 8 / 100", rendered.trim())
    }

    @Test
    fun `legacy maximum commands update runtime and editor rules`() {
        val root =
            JsonParser.parseReader(
                    javaClass.getResourceAsStream("/server-list/contract.json")!!.reader()
                )
                .asJsonArray[0]
                .asJsonObject
        val template = root.getAsJsonObject("template").deepCopy()
        template
            .getAsJsonObject("players")
            .add("maximum", JsonParser.parseString("""{"mode":"fixed","value":500}"""))
        val original = MotdDocument("text", serverList = ServerListTemplate.parse(template))
        val changed = original.withMaximum(200)
        assertEquals(200, changed.serverList!!.maximum.apply(100, 8))
        assertEquals("inherit", changed.withMaximum(null).serverList!!.maximum.mode)
    }
}
