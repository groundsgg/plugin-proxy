package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BedrockRosterTest {
    private val linked = UUID.fromString("12345678-1234-4234-8234-123456789abc")
    private val java = UUID.fromString("87654321-1234-4234-8234-123456789abc")

    @Test
    fun `a Java proxy sees a linked Bedrock player on another proxy`() {
        var payload = ""
        val source =
            BedrockRoster("bedrock", { listOf(linked, java) }, { it == linked }, { payload = it })
        source.refresh()
        val viewer = BedrockRoster("java", { emptyList() }, { false }, {})
        viewer.receive(payload)
        assertTrue(viewer.isBedrock(linked))
        assertFalse(viewer.isBedrock(java))
    }

    @Test
    fun `replacement snapshot removes disconnected players`() {
        var players = listOf(linked)
        var payload = ""
        val source = BedrockRoster("bedrock", { players }, { true }, { payload = it })
        val viewer = BedrockRoster("java", { emptyList() }, { false }, {})
        source.refresh()
        viewer.receive(payload)
        players = emptyList()
        source.refresh()
        viewer.receive(payload)
        assertFalse(viewer.isBedrock(linked))
    }

    @Test
    fun `expired remote snapshots do not mark linked Java accounts`() {
        var now = 0L
        var payload = ""
        val source = BedrockRoster("bedrock", { listOf(linked) }, { true }, { payload = it })
        val viewer = BedrockRoster("java", { emptyList() }, { false }, {}, { now })
        source.refresh()
        viewer.receive(payload)
        now = BedrockRoster.EXPIRY_MILLIS
        assertFalse(viewer.isBedrock(linked))
    }

    @Test
    fun `local connection overrides an older remote Bedrock snapshot`() {
        var payload = ""
        val source = BedrockRoster("bedrock", { listOf(linked) }, { true }, { payload = it })
        source.refresh()
        val viewer = BedrockRoster("java", { listOf(linked) }, { false }, {})
        viewer.receive(payload)
        viewer.refresh()
        assertFalse(viewer.isBedrock(linked))
    }

    @Test
    fun `malformed snapshots are ignored and own echoes cannot replace local state`() {
        val roster = BedrockRoster("java", { listOf(linked) }, { false }, {})
        roster.refresh()
        listOf(
                "not json",
                "[]",
                "{}",
                "{\"schemaVersion\":1,\"proxy\":\"other\",\"players\":{\"invalid\":true}}",
                "{\"schemaVersion\":1,\"proxy\":\"java\",\"players\":{\"$linked\":true}}",
            )
            .forEach { roster.receive(it) }
        assertFalse(roster.isBedrock(linked))
    }

    @Test
    fun `unlinked Floodgate UUID is recognized before a snapshot arrives`() {
        val roster = BedrockRoster("java", { emptyList() }, { false }, {})
        assertTrue(roster.isBedrock(UUID.fromString("00000000-0000-0000-0009-01f64d9a38a8")))
        assertFalse(roster.isBedrock(UUID(0, 0)))
        assertFalse(roster.isBedrock(java))
    }

    @Test
    fun `newer Java connection clears a remote linked Bedrock indication`() {
        var now = 0L
        var payload = ""
        val viewer = BedrockRoster("viewer", { emptyList() }, { false }, {}, { now })
        BedrockRoster("bedrock", { listOf(linked) }, { true }, { payload = it }).refresh()
        viewer.receive(payload)
        now++
        BedrockRoster("java", { listOf(linked) }, { false }, { payload = it }).refresh()
        viewer.receive(payload)
        assertFalse(viewer.isBedrock(linked))
    }

    @Test
    fun `platform broadcasts are scoped to a literal environment`() {
        assertEquals("proxy.platform.stage", BedrockRoster.subject("stage"))
        assertEquals("proxy.platform.prod", BedrockRoster.subject("prod"))
        assertNull(BedrockRoster.subject(null))
        assertNull(BedrockRoster.subject(""))
        assertNull(BedrockRoster.subject("stage.>"))
    }
}
