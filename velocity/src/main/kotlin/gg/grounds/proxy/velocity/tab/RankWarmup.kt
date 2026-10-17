package gg.grounds.proxy.velocity.tab

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Preserve a backend's initial display while a bounded remote lookup queue drains. */
internal class RankWarmup(private val now: () -> Long = System::nanoTime) {
    private data class Entry(val startedAt: Long, val lastSeenAt: Long)

    private val started = ConcurrentHashMap<UUID, Entry>()

    fun keep(id: UUID, hasBadge: Boolean, local: Boolean): Boolean {
        if (local) {
            clear(id)
            return false
        }
        // Another viewer without a badge must not re-arm this UUID-wide grace period.
        if (!hasBadge) return false
        val time = now()
        val existing = started.computeIfPresent(id) { _, entry -> entry.copy(lastSeenAt = time) }
        if (existing != null) return time - existing.startedAt < GRACE_NANOS
        if (started.size >= 512)
            started.entries.removeIf { time - it.value.lastSeenAt > GRACE_NANOS }
        if (!started.containsKey(id) && started.size >= 512) return false
        val first = started.putIfAbsent(id, Entry(time, time)) ?: Entry(time, time)
        return time - first.startedAt < GRACE_NANOS
    }

    fun clear(id: UUID) {
        started.remove(id)
    }

    private companion object {
        // 128 queued remote lookups may each consume their two-second HTTP timeout.
        // This is display-only validity; resolved roles replace the badge immediately.
        const val GRACE_NANOS = 300_000_000_000L
    }
}
