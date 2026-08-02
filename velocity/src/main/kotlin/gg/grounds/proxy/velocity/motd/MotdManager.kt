package gg.grounds.proxy.velocity.motd

import java.time.Instant
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.slf4j.Logger

/**
 * Holds the MOTD this proxy is currently serving, and keeps it in step with service-config.
 *
 * Every proxy polls; none of them are told. service-config's own contract says the snapshot read is
 * the source of truth and its NATS change event is only a latency hint, and a poll on a document
 * this small is cheap enough that subscribing as well would buy a few seconds in exchange for a
 * second way for the two to disagree. An operator who just ran `/motd set` does not wait for the
 * poll — that path updates this proxy directly, and the others follow within one interval.
 *
 * A failed refresh keeps the previous MOTD rather than clearing it: service-config being briefly
 * unreachable must not empty the server list entry of every region at once.
 */
class MotdManager(
    private val store: MotdStore,
    private val region: String?,
    private val continent: String?,
    private val logger: Logger,
) {

    /**
     * What to serve, and the parse of it that does not have to be redone.
     *
     * [static] is the already-parsed component for a MOTD whose placeholders cannot change between
     * two pings — most of them. A ping arrives whenever anybody opens their server list, so the
     * common case does no MiniMessage work at all; only a MOTD that names the live player count is
     * rebuilt per ping.
     */
    private class State(val document: MotdDocument, val static: Component?)

    @Volatile private var state: State? = null
    @Volatile private var loaded = false

    /** The MOTD as stored, or null when none is set. */
    fun current(): MotdDocument? = state?.document

    /** True once a read has succeeded, whatever it found. Until then this proxy has no opinion. */
    fun loaded(): Boolean = loaded

    /**
     * Re-reads the stored MOTD.
     *
     * Runs on a scheduler thread. Never throws: a proxy that cannot reach service-config keeps
     * serving the MOTD it already has.
     */
    fun refresh() {
        try {
            adopt(store.read())
            loaded = true
        } catch (ex: MotdFormatException) {
            // Someone wrote something this version cannot read. Keeping the previous MOTD is right,
            // but this one needs a human, so it is logged every time rather than once.
            logger.warn("Stored MOTD is unreadable ({}); keeping the previous one", ex.message)
        } catch (ex: Exception) {
            logger.warn("MOTD refresh failed (reason={}); keeping the previous one", ex.message)
        }
    }

    /**
     * Writes [document] as the network's MOTD and applies it here immediately.
     *
     * @throws Exception when the write is refused — the caller reports that to whoever ran the
     *   command instead of leaving them believing it took.
     */
    fun set(document: MotdDocument, actor: String) {
        val stamped = document.copy(updatedBy = actor, updatedAt = Instant.now().toString())
        store.write(stamped, actor)
        adopt(stamped)
        loaded = true
    }

    /** Removes the stored MOTD, here and everywhere. Returns true when there was one. */
    fun clear(actor: String): Boolean {
        val deleted = store.clear(actor)
        adopt(null)
        loaded = true
        return deleted
    }

    /**
     * The MOTD to put in this ping, or null to leave Velocity's own alone.
     *
     * [players] and [maxPlayers] are what the same ping is about to report, so a MOTD that mentions
     * them agrees with the numbers beside it.
     */
    fun render(players: Int, maxPlayers: Int): Component? {
        val snapshot = state ?: return null
        snapshot.static?.let {
            return it
        }
        return parse(snapshot.document.text, players, maxPlayers)
    }

    /** The player cap to report, or null to keep Velocity's. */
    fun maxPlayers(): Int? = state?.document?.maxPlayers

    /**
     * Renders [text] the way a ping would, for `/motd preview` and for the confirmation after a set
     * — seeing the placeholders resolved is the only way to catch a typo before players do.
     */
    fun preview(text: String, players: Int, maxPlayers: Int): Component =
        parse(text, players, maxPlayers)

    private fun adopt(document: MotdDocument?) {
        state =
            document?.let {
                // Parsed once here rather than per ping, but only when the result cannot change.
                // The player count is resolved at ping time, so a MOTD using it has no static form.
                val static = if (MotdPlaceholders.isDynamic(it.text)) null else parse(it.text, 0, 0)
                State(it, static)
            }
    }

    private fun parse(text: String, players: Int, maxPlayers: Int): Component {
        val rendered =
            MotdPlaceholders.render(
                text,
                MotdPlaceholders.Context(
                    region = region,
                    continent = continent,
                    players = players,
                    maxPlayers = maxPlayers,
                ),
            )
        return MiniMessage.miniMessage().deserialize(rendered)
    }
}
