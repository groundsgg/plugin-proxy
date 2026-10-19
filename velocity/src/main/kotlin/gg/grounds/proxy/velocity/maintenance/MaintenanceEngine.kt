package gg.grounds.proxy.velocity.maintenance

import java.time.Clock
import java.time.Instant
import java.util.UUID
import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * Local, side-effect-free maintenance state. Each proxy owns one instance: the shared document is
 * only the decision input, while kicking and messaging intentionally remain local operations.
 */
class MaintenanceEngine(private val clock: Clock = Clock.systemUTC()) {
    data class Admission(val allowed: Boolean, val message: String? = null)

    private var document: MaintenanceDocument? = null
    private var lastSuccess: Instant? = null
    private val kicked = mutableSetOf<UUID>()
    private val announced = mutableSetOf<Long>()
    private var lastRemaining: Long? = null

    @Synchronized
    fun adopt(next: MaintenanceDocument?, receivedAt: Instant = clock.instant()) {
        val old = document
        val oldId = old?.id
        document = next
        lastSuccess = receivedAt
        if (next?.id != oldId) {
            kicked.clear()
            announced.clear()
            // A fresh proxy must never replay old countdown notices on bootstrap.
            lastRemaining = next?.startsAt?.let { it.epochSecond - receivedAt.epochSecond }
        } else if (
            next != null &&
                (next.startsAt != old?.startsAt || (old?.enabled == false && next.enabled))
        ) {
            // Rescheduling an existing episode preserves delivered notices and kick history, while
            // establishing a new countdown baseline so earlier offsets do not replay at once.
            lastRemaining = next.startsAt.epochSecond - receivedAt.epochSecond
        }
    }

    /** Records a failed or malformed fetch while retaining the last known valid snapshot. */
    @Synchronized
    fun refreshFailed(at: Instant = clock.instant()) {
        // The timestamp is intentionally not changed. Freshness is time since a valid read.
    }

    @Synchronized
    fun activeAt(now: Instant = clock.instant()): Boolean =
        document?.let {
            it.enabled &&
                !now.isBefore(it.startsAt) &&
                (it.endsAt == null || now.isBefore(it.endsAt))
        } ?: false

    @Synchronized fun whitelisted(uuid: UUID): Boolean = document?.whitelist?.contains(uuid) == true

    @Synchronized
    fun admission(uuid: UUID, bypass: Boolean, now: Instant = clock.instant()): Admission {
        if (bypass || document?.whitelist?.contains(uuid) == true) return Admission(true)
        // A recorded end is a local schedule too: once it has elapsed, maintenance ends even if
        // service-config is temporarily unavailable.
        if (document?.endsAt?.let { !now.isBefore(it) } == true) return Admission(true)
        if (unhealthy(now))
            return Admission(false, "Maintenance configuration is temporarily unavailable.")
        if (!activeAt(now)) return Admission(true)
        return Admission(false, denialMessage(now))
    }

    /** Returns true once per local player per maintenance episode. */
    @Synchronized
    fun shouldKick(uuid: UUID, bypass: Boolean, now: Instant = clock.instant()): Boolean {
        val current = document ?: return false
        if (!current.kickExisting || bypass || uuid in current.whitelist || !activeAt(now))
            return false
        return kicked.add(uuid)
    }

    /** Announcements whose threshold was crossed since the previous local scheduler tick. */
    @Synchronized
    fun announcementsAt(now: Instant = clock.instant()): List<Long> {
        val current = document ?: return emptyList()
        if (!current.enabled || !now.isBefore(current.startsAt)) return emptyList()
        val remaining = current.startsAt.epochSecond - now.epochSecond
        val previous = lastRemaining.also { lastRemaining = remaining } ?: return emptyList()
        val crossed =
            current.announcementSeconds.filter {
                it < previous && it >= remaining && announced.add(it)
            }
        return crossed.minOrNull()?.let(::listOf) ?: emptyList()
    }

    @Synchronized
    fun motd(now: Instant = clock.instant()): String? =
        document
            ?.takeIf {
                it.enabled &&
                    !now.isBefore(it.startsAt) &&
                    (it.endsAt == null || now.isBefore(it.endsAt))
            }
            ?.let { render(it.motd, now) }

    @Synchronized
    fun announcementMessage(now: Instant = clock.instant()): String? =
        document
            ?.takeIf { it.enabled && now.isBefore(it.startsAt) }
            ?.let { render(it.announcementMessage, now) }

    private fun unhealthy(now: Instant): Boolean =
        lastSuccess?.let { now.epochSecond - it.epochSecond > STALE_AFTER_SECONDS } ?: true

    @Synchronized
    fun denialMessage(now: Instant = clock.instant()): String =
        document?.let { render(it.joinMessage, now) }.orEmpty()

    private fun render(template: String, now: Instant): String {
        val end = document?.endsAt ?: document?.estimatedEndAt
        val values =
            mapOf(
                "reason" to escape(document?.reason.orEmpty()),
                "end" to escape(end?.toString() ?: "to be announced"),
                "remaining" to
                    formatRemaining(
                        (document?.startsAt?.epochSecond ?: now.epochSecond) - now.epochSecond
                    ),
            )
        return PLACEHOLDER.replace(template) { values.getValue(it.groupValues[1]) }
    }

    private fun escape(value: String): String = MiniMessage.miniMessage().escapeTags(value)

    private fun formatRemaining(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val rest = safe % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${rest}s"
        }
    }

    companion object {
        const val STALE_AFTER_SECONDS = 30L
        private val PLACEHOLDER = Regex("\\{(reason|end|remaining)}")
    }
}

data class MaintenanceDocument(
    val enabled: Boolean,
    val id: UUID,
    val startsAt: Instant,
    val endsAt: Instant?,
    val estimatedEndAt: Instant?,
    val reason: String,
    val joinMessage: String,
    val motd: String,
    val kickExisting: Boolean,
    val whitelist: Set<UUID>,
    val announcementSeconds: List<Long>,
    val announcementMessage: String,
)
