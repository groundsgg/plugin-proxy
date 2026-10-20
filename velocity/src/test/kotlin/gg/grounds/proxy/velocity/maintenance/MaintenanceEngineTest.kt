package gg.grounds.proxy.velocity.maintenance

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaintenanceEngineTest {
    @Test
    fun `maintenance protocol label follows the window on every proxy without overriding the motd`() {
        val start = Instant.parse("2026-10-20T20:00:00Z")
        val document = active(start, endsAt = start.plusSeconds(60)).copy(motd = "")
        val proxies = listOf(MaintenanceEngine(), MaintenanceEngine())
        proxies.forEach { engine ->
            engine.adopt(document, start.minusSeconds(1))
            assertEquals(null, engine.protocolName(start.minusSeconds(1)))
            assertEquals("§cWartungsarbeiten", engine.protocolName(start))
            assertEquals(null, engine.motd(start))
            assertEquals(null, engine.protocolName(start.plusSeconds(60)))
            engine.adopt(document.copy(enabled = false), start)
            assertEquals(null, engine.protocolName(start))
        }
    }

    @Test
    fun `an optional maintenance motd is rendered only during the active window`() {
        val start = Instant.parse("2026-10-20T20:00:00Z")
        val engine = MaintenanceEngine()
        engine.adopt(active(start, endsAt = start.plusSeconds(60)), start)
        assertEquals(null, engine.motd(start.minusSeconds(1)))
        assertEquals("motd", engine.motd(start))
        assertEquals(null, engine.motd(start.plusSeconds(60)))
        engine.adopt(active(start).copy(motd = "   "), start)
        assertEquals(null, engine.motd(start))
    }

    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val player = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `two proxies independently kick only their local non bypass players once`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val document = active(now)
        val first = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        val second = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))

        first.adopt(document, now)
        second.adopt(document, now)

        assertTrue(first.shouldKick(player, bypass = false))
        assertTrue(second.shouldKick(player, bypass = false))
        assertFalse(first.shouldKick(player, bypass = false))
    }

    @Test
    fun `permission and whitelist bypass permit joins and avoid kicks`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val whitelisted = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        engine.adopt(active(now, whitelist = setOf(whitelisted)), now)

        assertTrue(engine.admission(player, bypass = true).allowed)
        assertTrue(engine.admission(whitelisted, bypass = false).allowed)
        assertFalse(engine.shouldKick(player, bypass = true))
        assertFalse(engine.shouldKick(whitelisted, bypass = false))
    }

    @Test
    fun `listener access uses permission but whitelist skips unavailable provider`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val whitelisted = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        engine.adopt(active(now, whitelist = setOf(whitelisted)), now)

        assertTrue(MaintenanceAccess.admission(engine, player, { true }, now).allowed)
        assertFalse(MaintenanceAccess.shouldKick(engine, player, { true }, now))
        assertTrue(
            MaintenanceAccess.admission(
                    engine,
                    whitelisted,
                    { error("permission provider must not be queried") },
                    now,
                )
                .allowed
        )
        assertFalse(
            MaintenanceAccess.shouldKick(
                engine,
                whitelisted,
                { error("permission provider must not be queried") },
                now,
            )
        )
    }

    @Test
    fun `disabled scheduled start and automatic expiry change effective state`() {
        val start = Instant.parse("2026-10-19T20:00:00Z")
        val clock = java.time.Clock.fixed(start.minusSeconds(1), ZoneOffset.UTC)
        val engine = MaintenanceEngine(clock)
        engine.adopt(active(start, endsAt = start.plusSeconds(60)), start.minusSeconds(1))
        assertFalse(engine.activeAt(start.minusSeconds(1)))
        assertTrue(engine.activeAt(start))
        assertFalse(engine.activeAt(start.plusSeconds(60)))
        assertTrue(engine.admission(player, bypass = false, now = start.plusSeconds(60)).allowed)
    }

    @Test
    fun `invalid update keeps valid cache and stale cache denies ordinary joins only`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        engine.adopt(active(now), now)
        engine.refreshFailed(now.plusSeconds(31))

        assertFalse(engine.admission(player, bypass = false, now = now.plusSeconds(31)).allowed)
        assertTrue(engine.admission(player, bypass = true, now = now.plusSeconds(31)).allowed)
        assertTrue(engine.activeAt(now.plusSeconds(31)))
    }

    @Test
    fun `startup outage denies ordinary joins while still allowing bypass`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))

        assertFalse(engine.admission(player, bypass = false, now = now).allowed)
        assertTrue(engine.admission(player, bypass = true, now = now).allowed)
    }

    @Test
    fun `editing an episode does not re kick but a new id does`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        engine.adopt(active(now), now)
        assertTrue(engine.shouldKick(player, false))
        engine.adopt(active(now, reason = "new wording"), now.plusSeconds(1))
        assertFalse(engine.shouldKick(player, false))
        engine.adopt(
            active(now, id = UUID.fromString("00000000-0000-0000-0000-000000000004")),
            now.plusSeconds(2),
        )
        assertTrue(engine.shouldKick(player, false))
    }

    @Test
    fun `message values are escaped once before MiniMessage rendering`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        engine.adopt(
            active(now, reason = "<red>{end}\\path", endsAt = null)
                .copy(joinMessage = "<yellow>{reason}</yellow> / {end}"),
            now,
        )
        val rendered =
            PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(engine.denialMessage(now)))
        assertEquals("<red>{end}\\path / to be announced", rendered)
    }

    @Test
    fun `announcement thresholds are crossed once and never replayed after restart bootstrap`() {
        val startsAt = Instant.parse("2026-10-19T20:10:00Z")
        val engine = MaintenanceEngine(Clock.fixed(startsAt.minusSeconds(700), ZoneOffset.UTC))
        engine.adopt(active(startsAt, offsets = listOf(600, 300)), startsAt.minusSeconds(700))
        assertEquals(emptyList<Long>(), engine.announcementsAt(startsAt.minusSeconds(700)))
        assertEquals(listOf(600L), engine.announcementsAt(startsAt.minusSeconds(599)))
        assertEquals(emptyList<Long>(), engine.announcementsAt(startsAt.minusSeconds(590)))
    }

    @Test
    fun `rescheduling same episode resets baseline without replaying past announcements`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        engine.adopt(active(now.plusSeconds(700), offsets = listOf(600, 300)), now)
        assertEquals(listOf(600L), engine.announcementsAt(now.plusSeconds(100)))
        engine.adopt(active(now.plusSeconds(500), offsets = listOf(600, 300)), now.plusSeconds(101))
        assertEquals(emptyList<Long>(), engine.announcementsAt(now.plusSeconds(102)))
    }

    @Test
    fun `a removed maintenance document clears a previous active window`() {
        val now = Instant.parse("2026-10-19T20:00:00Z")
        val engine = MaintenanceEngine(Clock.fixed(now, ZoneOffset.UTC))
        engine.adopt(active(now), now)
        assertFalse(engine.admission(player, false, now).allowed)
        engine.adopt(null, now.plusSeconds(1))
        assertTrue(engine.admission(player, false, now.plusSeconds(1)).allowed)
        assertFalse(engine.shouldKick(player, false, now.plusSeconds(1)))
    }

    private fun active(
        startsAt: Instant,
        id: UUID = this.id,
        endsAt: Instant? = null,
        whitelist: Set<UUID> = emptySet(),
        reason: String = "Maintenance",
        offsets: List<Long> = emptyList(),
    ) =
        MaintenanceDocument(
            true,
            id,
            startsAt,
            endsAt,
            null,
            reason,
            "join",
            "motd",
            true,
            whitelist,
            offsets,
            "announce",
        )
}
