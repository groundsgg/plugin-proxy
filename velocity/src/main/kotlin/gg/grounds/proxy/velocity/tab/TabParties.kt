package gg.grounds.proxy.velocity.tab

import com.google.gson.JsonParser
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who shares a party, from service-social's party feed. The subject gets an event after every
 * change to a party's online members, and `<subject>.of` answers for one player; both carry
 * `{"party":"<id>"|null,"members":["<uuid>",...]}`, with no members once the party is gone.
 */
internal class TabParties {
    private data class Update(val party: UUID?, val members: Set<UUID>)

    private val parties = ConcurrentHashMap<UUID, UUID>()

    fun partyOf(player: UUID): UUID? = parties[player]

    /**
     * Applies one payload and reports whether anyone's party changed. [player] names who a `.of`
     * reply was about, since a reply without a party carries no members to clear.
     */
    @Synchronized
    fun receive(raw: String, player: UUID? = null): Boolean {
        val update = parse(raw) ?: return false
        val party = update.party ?: return player != null && parties.remove(player) != null
        // Members who left or went offline drop out; a player moving in leaves their old party.
        var changed = parties.entries.removeIf { it.value == party && it.key !in update.members }
        update.members.forEach { if (parties.put(it, party) != party) changed = true }
        return changed
    }

    private fun parse(raw: String): Update? {
        if (raw.length > MAX_PAYLOAD) return null
        return runCatching {
                val obj = JsonParser.parseString(raw).asJsonObject
                val members = obj.getAsJsonArray("members")
                if (members.size() > MAX_MEMBERS) return null
                Update(
                    party =
                        obj.get("party")
                            ?.takeUnless { it.isJsonNull }
                            ?.asString
                            ?.let(UUID::fromString),
                    members = members.map { UUID.fromString(it.asString) }.toSet(),
                )
            }
            .getOrNull()
    }

    companion object {
        private const val MAX_PAYLOAD = 16 * 1024
        private const val MAX_MEMBERS = 100
        private val ENVIRONMENT = Regex("[A-Za-z0-9_-]{1,128}")

        fun subject(environment: String?): String? =
            environment?.trim()?.takeIf { it.matches(ENVIRONMENT) }?.let { "social.parties.$it" }
    }
}
