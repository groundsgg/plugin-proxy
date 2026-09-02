package gg.grounds.proxy.velocity.tab

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Short-lived edition snapshots let Java proxies label linked Bedrock accounts too. */
internal class BedrockRoster(
    private val proxyId: String,
    private val connectedPlayers: () -> Collection<UUID>,
    private val detect: (UUID) -> Boolean,
    private val publish: (String) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Snapshot(val players: Map<UUID, Boolean>, val receivedAt: Long)

    private val remote = ConcurrentHashMap<String, Snapshot>()
    @Volatile private var local: Map<UUID, Boolean> = emptyMap()

    fun refresh() {
        local = connectedPlayers().associateWith(detect)
        expire()
        send(local)
    }

    fun close() {
        local = emptyMap()
        send(emptyMap())
        remote.clear()
    }

    fun isBedrock(id: UUID): Boolean {
        local[id]?.let {
            return it
        }
        val now = nowMillis()
        val current =
            remote.values
                .filter { now - it.receivedAt < EXPIRY_MILLIS && id in it.players }
                .maxByOrNull { it.receivedAt }
        return current?.players?.get(id) ?: BedrockPlayers.isUnlinkedUuid(id)
    }

    fun receive(raw: String) {
        if (raw.length > MAX_PAYLOAD) return
        val parsed =
            try {
                val root = JsonParser.parseString(raw)
                if (!root.isJsonObject) return
                val obj = root.asJsonObject
                if (obj.get("schemaVersion")?.asInt != 1) return
                val owner =
                    obj.get("proxy")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString ?: return
                if (owner == proxyId || !owner.matches(PROXY_ID)) return
                val entries = obj.get("players")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
                if (entries.size() > MAX_PLAYERS) return
                val players =
                    entries.entrySet().associate { (key, value) ->
                        val id = UUID.fromString(key)
                        if (
                            id.toString() != key ||
                                !value.isJsonPrimitive ||
                                !value.asJsonPrimitive.isBoolean
                        )
                            return
                        id to value.asBoolean
                    }
                owner to Snapshot(players, nowMillis())
            } catch (_: RuntimeException) {
                return
            }
        expire()
        if (!remote.containsKey(parsed.first) && remote.size >= MAX_PROXIES) return
        remote[parsed.first] = parsed.second
    }

    private fun send(players: Map<UUID, Boolean>) {
        if (!proxyId.matches(PROXY_ID) || players.size > MAX_PLAYERS) return
        val entries = JsonObject()
        players.forEach { (id, bedrock) -> entries.addProperty(id.toString(), bedrock) }
        val root = JsonObject()
        root.addProperty("schemaVersion", 1)
        root.addProperty("proxy", proxyId)
        root.add("players", entries)
        publish(root.toString())
    }

    private fun expire() {
        val now = nowMillis()
        remote.entries.removeIf { now - it.value.receivedAt >= EXPIRY_MILLIS }
    }

    companion object {
        const val EXPIRY_MILLIS = 30_000L
        private const val MAX_PAYLOAD = 512 * 1024
        private const val MAX_PLAYERS = 10_000
        private const val MAX_PROXIES = 256
        private val PROXY_ID = Regex("[A-Za-z0-9_-]{1,128}")

        fun subject(environment: String?): String? =
            environment?.trim()?.takeIf { it.matches(PROXY_ID) }?.let { "proxy.platform.$it" }
    }
}
