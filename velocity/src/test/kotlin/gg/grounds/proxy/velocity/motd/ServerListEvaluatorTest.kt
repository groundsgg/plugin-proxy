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
}
