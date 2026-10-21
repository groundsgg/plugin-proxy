package gg.grounds.proxy.velocity.motd

import com.google.gson.JsonParser
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.api.proxy.server.ServerPing
import gg.grounds.proxy.velocity.maintenance.MaintenanceDocument
import gg.grounds.proxy.velocity.maintenance.MaintenanceEngine
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ServerListNetworkTest {
    private class Store : MotdStore {
        var document: MotdDocument? = null
        var failed = false

        override fun read(): MotdDocument? {
            if (failed) error("offline")
            return document
        }

        override fun write(document: MotdDocument, updatedBy: String) {
            this.document = document
        }

        override fun clear(deletedBy: String): Boolean {
            val had = document != null
            document = null
            return had
        }
    }

    private fun connection() =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(InboundConnection::class.java)) {
            _,
            method,
            _ ->
            when (method.name) {
                "getProtocolVersion" -> ProtocolVersion.MINECRAFT_1_21
                "toString" -> "test connection"
                else -> null
            }
        } as InboundConnection

    private fun ping(listener: ServerListPingListener, local: Int): ServerPing {
        val event =
            ProxyPingEvent(
                connection(),
                ServerPing.builder()
                    .version(ServerPing.Version(767, "1.21.1"))
                    .onlinePlayers(local)
                    .maximumPlayers(500)
                    .description(Component.empty())
                    .build(),
            )
        listener.onProxyPing(event)
        return event.ping
    }

    private fun legacyMaintenance(start: Instant, end: Instant) =
        MaintenanceDocument(
            enabled = true,
            id = UUID.randomUUID(),
            startsAt = start,
            endsAt = end,
            estimatedEndAt = null,
            reason = "Work",
            joinMessage = "Closed",
            motd = "",
            kickExisting = false,
            whitelist = emptySet(),
            announcementSeconds = emptyList(),
            announcementMessage = "Soon",
        )

    @Test
    fun `one ping uses one instant when maintenance ends during evaluation`() {
        val start = Instant.parse("2026-10-21T12:00:00Z")
        val end = start.plusSeconds(60)
        val engine = MaintenanceEngine()
        engine.adopt(legacyMaintenance(start, end), start)
        var reads = 0
        val listener =
            ServerListPingListener(
                { null },
                { null },
                { engine },
                { null },
                { null },
                { if (reads++ == 0) end.minusNanos(1) else end },
            )

        val result = ping(listener, 3)

        assertEquals(-1, result.version.protocol)
        assertEquals(1, reads)
    }

    @Test
    fun `maintenance refresh during one ping cannot crash legacy status evaluation`() {
        val now = Instant.parse("2026-10-21T12:00:00Z")
        val engine = MaintenanceEngine()
        engine.adopt(legacyMaintenance(now.minusSeconds(60), now.plusSeconds(60)), now)
        val listener =
            ServerListPingListener(
                { null },
                {
                    engine.adopt(null, now)
                    null
                },
                { engine },
                { null },
                { null },
                { now },
            )

        val result = ping(listener, 3)

        assertEquals(767, result.version.protocol)
    }

    @Test
    fun `two independent proxy stores serve shared counts retain cache and restore normal after maintenance`() {
        val a = Store()
        val b = Store()
        val fixture =
            JsonParser.parseReader(
                    javaClass.getResourceAsStream("/server-list/contract.json")!!.reader()
                )
                .asJsonArray[2]
                .asJsonObject
        val settings =
            MotdDocument(
                text = "{{region}} {{players}}/{{max}}",
                serverList = ServerListTemplate.parse(fixture.getAsJsonObject("template")),
            )
        a.document = settings
        b.document = MotdDocument.fromJson(settings.toJson())
        val ma = MotdManager(a, "eu", "Europe", LoggerFactory.getLogger(javaClass))
        val mb = MotdManager(b, "na", "North America", LoggerFactory.getLogger(javaClass))
        ma.refresh()
        mb.refresh()
        val now = Instant.parse("2026-10-21T12:00:00Z")
        val ea = MaintenanceEngine()
        val eb = MaintenanceEngine()
        val la = ServerListPingListener({ ma }, { 8 }, { ea }, { "eu" }, { "Europe" }, { now })
        val lb =
            ServerListPingListener({ mb }, { 8 }, { eb }, { "na" }, { "North America" }, { now })
        fun text(p: ServerPing) =
            PlainTextComponentSerializer.plainText().serialize(p.descriptionComponent!!)
        assertEquals(8, ping(la, 3).players.get().online)
        assertEquals(8, ping(lb, 5).players.get().online)
        assertEquals("eu 8/500", text(ping(la, 3)))
        assertEquals("na 8/500", text(ping(lb, 5)))
        assertEquals(
            ping(la, 3).players.get().sample.map { it.name },
            ping(lb, 5).players.get().sample.map { it.name },
        )
        val forced = fixture.getAsJsonObject("template").deepCopy()
        forced.add(
            "version",
            JsonParser.parseString(
                """{"mode":"always","text":{"nodes":[{"id":"v","type":"text","value":"Maintenance"}]}}"""
            ),
        )
        val maintenance =
            MaintenanceDocument(
                true,
                UUID.randomUUID(),
                now.minusSeconds(60),
                now.plusSeconds(60),
                null,
                "Work",
                "Closed",
                "",
                false,
                emptySet(),
                emptyList(),
                "",
                ServerListTemplate.parse(forced),
                "",
                setOf("version"),
            )
        ea.adopt(maintenance, now)
        eb.adopt(maintenance, now)
        assertEquals(-1, ping(la, 3).version.protocol)
        assertEquals(-1, ping(lb, 5).version.protocol)
        a.failed = true
        b.failed = true
        ma.refresh()
        mb.refresh()
        assertEquals(8, ping(la, 3).players.get().online)
        ea.adopt(maintenance.copy(enabled = false), now)
        eb.adopt(maintenance.copy(enabled = false), now)
        assertEquals(767, ping(la, 3).version.protocol)
        assertEquals(767, ping(lb, 5).version.protocol)
        ma.set(
            settings.copy(
                text = "legacy update",
                editor = JsonParser.parseString("""{"fallbackText":"old"}""").asJsonObject,
            ),
            "test",
        )
        assertNull(ma.current()!!.editor)
        assertNotNull(ma.current()!!.serverList)
    }
}
