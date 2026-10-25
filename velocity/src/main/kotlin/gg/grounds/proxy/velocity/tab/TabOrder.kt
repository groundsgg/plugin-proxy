package gg.grounds.proxy.velocity.tab

import java.util.Locale
import java.util.UUID

/**
 * A viewer's existing roster, ordered independently of local proxy membership. The viewer's own
 * party comes first, then rank and name.
 */
internal object TabOrder {
    data class Entry(
        val id: UUID,
        val name: String,
        val sortOrder: Int?,
        val party: Boolean = false,
    )

    fun orders(entries: List<Entry>): Map<UUID, Int> =
        entries
            .sortedWith(
                compareBy<Entry> { !it.party }
                    .thenBy { it.sortOrder?.toLong() ?: Long.MAX_VALUE }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
                    .thenBy { it.id }
            )
            .mapIndexed { index, entry -> entry.id to entries.size - index }
            .toMap()
}
