package gg.grounds.proxy.velocity.tab

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabOrderTest {
    @Test
    fun `rank wins over name then equal priorities use case insensitive alphabet`() {
        val names = listOf("Aaron", "zoe", "alice", "Bob", "ALICE", "Unknown")
        val priorities = listOf<Int?>(20, 0, 0, 0, 0, null)
        val entries =
            names.mapIndexed { i, name -> TabOrder.Entry(UUID(0, i.toLong()), name, priorities[i]) }
        val order = TabOrder.orders(entries)
        assertEquals(
            listOf("alice", "ALICE", "Bob", "zoe", "Aaron", "Unknown"),
            entries.sortedByDescending { order[it.id] }.map { it.name },
        )
    }

    @Test
    fun `extreme priorities do not overflow and an unknown rank sorts last`() {
        val entries =
            listOf(Int.MAX_VALUE, null, Int.MIN_VALUE).mapIndexed { i, priority ->
                TabOrder.Entry(UUID(0, i.toLong()), "Player$i", priority)
            }
        val order = TabOrder.orders(entries)
        assertEquals(listOf(entries[2].id, entries[0].id, entries[1].id), order.keys.toList())
    }

    @Test
    fun `the viewer's party comes before any rank`() {
        val entries =
            listOf(
                TabOrder.Entry(UUID(0, 1), "Admin", 0),
                TabOrder.Entry(UUID(0, 2), "zed", null, party = true),
                TabOrder.Entry(UUID(0, 3), "amy", 50, party = true),
            )
        val order = TabOrder.orders(entries)
        assertEquals(
            listOf("amy", "zed", "Admin"),
            entries.sortedByDescending { order[it.id] }.map { it.name },
        )
    }
}
