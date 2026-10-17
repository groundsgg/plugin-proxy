package gg.grounds.proxy.velocity.tab

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Preserve a backend's initial badge for one refresh while remote display metadata loads. */
internal class RankWarmup(private val now: () -> Long = System::nanoTime) {
    private val started = ConcurrentHashMap<UUID, Long>()

    fun keep(id: UUID, hasBadge: Boolean, local: Boolean): Boolean {
        if (local) {
            clear(id)
            return false
        }
        // Another viewer without a badge must not re-arm this UUID-wide grace period.
        if (!hasBadge) return false
        val time = now()
        if (started.size >= 512) started.entries.removeIf { time - it.value > 60_000_000_000L }
        if (!started.containsKey(id) && started.size >= 512) return false
        val first = started.putIfAbsent(id, time) ?: time
        return time - first < 2_000_000_000L
    }

    fun clear(id: UUID) {
        started.remove(id)
    }
}
