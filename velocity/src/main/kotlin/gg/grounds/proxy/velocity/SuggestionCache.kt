package gg.grounds.proxy.velocity

import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived cache of cross-proxy tab-complete answers, keyed by prefix.
 *
 * Velocity fires tab-complete on every keystroke, so one player typing an eight-letter name would
 * otherwise issue eight network-wide lookups — multiplied by everyone online. Two seconds is long
 * enough to cover the typing itself and short enough that a player who just joined shows up.
 */
class SuggestionCache(
    private val ttlMillis: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val entries = ConcurrentHashMap<String, Entry>()

    /** Cached names for [prefix], or the result of [load] — which is only called on a miss. */
    fun get(prefix: String, load: () -> List<String>): List<String> {
        val key = prefix.lowercase()
        val now = clock()
        entries[key]?.let { if (now - it.at < ttlMillis) return it.names }

        val names = load()
        entries[key] = Entry(names, now)
        // Every distinct prefix anyone types is a key, so it grows without bound otherwise.
        if (entries.size > maxEntries) {
            entries.entries.removeIf { now - it.value.at >= ttlMillis }
        }
        return names
    }

    fun size(): Int = entries.size

    private data class Entry(val names: List<String>, val at: Long)

    companion object {
        const val DEFAULT_TTL_MS = 2_000L
        const val DEFAULT_MAX_ENTRIES = 256

        /**
         * Local players first (they are certainly online and cost nothing to find), then the rest
         * of the network, de-duplicated and capped.
         */
        fun merge(local: List<String>, remote: List<String>, limit: Int): List<String> =
            (local + remote).distinct().take(limit)
    }
}
