package gg.grounds.proxy.velocity.tab

import com.google.gson.JsonParser
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who has voice on, from plugin-voice's per-server snapshots on `voice.players.<env>.<zone>`:
 * `{"schemaVersion":1,"server":"<podIP:port>","players":{"<uuid>":"on"|"off"}}`. Each server sends
 * one after every change and every 5 s, so a snapshot older than [EXPIRY_MILLIS] belongs to a
 * server that is gone. Cosmetic: a wrong entry only draws the wrong icon.
 */
internal class VoicePresences(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private data class Snapshot(val players: Map<UUID, Boolean>, val receivedAt: Long)

    private val servers = ConcurrentHashMap<String, Snapshot>()

    /** True for voice on, false for voice switched off, null for no voice (or no mod). */
    fun voiceOf(player: UUID): Boolean? {
        val now = nowMillis()
        return servers.values
            .filter { now - it.receivedAt < EXPIRY_MILLIS && player in it.players }
            .maxByOrNull { it.receivedAt }
            ?.players
            ?.get(player)
    }

    /** Applies one snapshot and reports whether it changed what a tab list would show. */
    fun receive(raw: String): Boolean {
        if (raw.length > MAX_PAYLOAD) return false
        val (server, players) =
            runCatching {
                    val root = JsonParser.parseString(raw).asJsonObject
                    if (root.get("schemaVersion")?.asInt != 1) return false
                    val entries = root.getAsJsonObject("players")
                    if (entries.size() > MAX_PLAYERS) return false
                    root.get("server").asString to
                        entries.entrySet().associate { (id, state) ->
                            UUID.fromString(id) to
                                when (state.asString) {
                                    "on" -> true
                                    "off" -> false
                                    else -> return false
                                }
                        }
                }
                .getOrNull() ?: return false
        val now = nowMillis()
        servers.entries.removeIf { now - it.value.receivedAt >= EXPIRY_MILLIS }
        if (!servers.containsKey(server) && servers.size >= MAX_SERVERS) return false
        return servers.put(server, Snapshot(players, now))?.players != players
    }

    companion object {
        const val EXPIRY_MILLIS = 20_000L
        private const val MAX_PAYLOAD = 256 * 1024
        private const val MAX_PLAYERS = 5_000
        private const val MAX_SERVERS = 1_024
        private val ENVIRONMENT = Regex("[A-Za-z0-9_-]{1,128}")

        /** Every zone of [environment]; each server publishes under its own zone. */
        fun subject(environment: String?): String? =
            environment?.trim()?.takeIf { it.matches(ENVIRONMENT) }?.let { "voice.players.$it.>" }
    }
}
