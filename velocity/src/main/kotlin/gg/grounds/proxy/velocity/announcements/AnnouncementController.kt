package gg.grounds.proxy.velocity.announcements

import gg.grounds.hud.HudPills
import java.util.Locale
import java.util.UUID
import net.kyori.adventure.bossbar.BossBar

/** A connection-scoped view, so tests exercise transitions without a Minecraft transport. */
interface AnnouncementViewer {
    val playerId: UUID
    val session: Any
    val serverType: String?
    val locale: Locale
    val hudReady: Boolean
    val connected: Boolean

    fun show(bar: BossBar)

    fun hide(bar: BossBar)
}

class AnnouncementController(
    private val viewers: () -> Collection<AnnouncementViewer>,
    private val settings: () -> AnnouncementSettings?,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private data class Active(val viewer: AnnouncementViewer, val bar: BossBar)

    private val active = mutableMapOf<UUID, Active>()

    private data class Pending(
        val session: Any,
        val attempt: Any,
        val source: Any?,
        val target: () -> String?,
    )

    private val pending = mutableMapOf<UUID, MutableList<Pending>>()
    private var closed = false

    @Synchronized
    fun beginSwitch(id: UUID, session: Any, attempt: Any, source: Any?, target: () -> String?) {
        if (closed || target() == null) return
        val attempts = pending.getOrPut(id) { mutableListOf() }
        attempts.removeAll { it.session !== session || it.target() == null }
        if (attempts.none { it.attempt === attempt })
            attempts += Pending(session, attempt, source, target)
        remove(id)
    }

    @Synchronized
    fun completedSwitch(id: UUID, session: Any, target: String, backend: Any?) {
        val attempts = pending[id] ?: return
        attempts.removeAll {
            it.session === session && it.target() == target && backend !== it.source
        }
        if (attempts.isEmpty()) pending.remove(id)
    }

    @Synchronized
    fun failedSwitch(id: UUID, session: Any, target: String, backend: Any?, connected: Boolean) {
        val attempts = pending[id] ?: return
        val matching = attempts.filter { it.session === session && it.target() == target }
        // A failure event has no attempt ID. Ambiguous overlapping attempts stay fail-closed.
        if (
            matching.size == 1 &&
                backend != null &&
                backend === matching.single().source &&
                connected
        )
            attempts.remove(matching.single())
        if (attempts.isEmpty()) pending.remove(id)
    }

    @Synchronized
    fun refreshAll() {
        if (closed) return
        val current = viewers()
        val sessions = current.associate { it.playerId to it.session }
        active.values
            .toList()
            .filter { sessions[it.viewer.playerId] !== it.viewer.session }
            .forEach { remove(it.viewer.playerId) }
        pending.keys.toList().forEach { id ->
            pending[id]?.removeAll { sessions[id] !== it.session }
            if (pending[id].isNullOrEmpty()) pending.remove(id)
        }
        current.forEach(::refresh)
    }

    @Synchronized
    fun refresh(viewer: AnnouncementViewer) {
        if (closed) return
        pending[viewer.playerId]?.let { attempts ->
            attempts.removeAll { it.session !== viewer.session || it.target() == null }
            if (attempts.isEmpty()) pending.remove(viewer.playerId)
            else {
                remove(viewer.playerId)
                return
            }
        }
        val config = settings()
        val role = viewer.serverType
        val pills =
            if (viewer.connected && viewer.hudReady && role != null && config != null)
                AnnouncementLayout.groupAt(
                    AnnouncementLayout.groups(config, role, viewer.locale),
                    clock(),
                )
            else null
        if (pills.isNullOrEmpty()) {
            remove(viewer.playerId)
            return
        }
        val old = active[viewer.playerId]
        if (old != null && old.viewer.session !== viewer.session) remove(viewer.playerId)
        val title = HudPills.render(pills)
        val existing = active[viewer.playerId]
        if (existing != null) {
            if (existing.bar.name() != title) existing.bar.name(title)
            return
        }
        val bar = BossBar.bossBar(title, 0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS)
        viewer.show(bar)
        active[viewer.playerId] = Active(viewer, bar)
    }

    @Synchronized
    fun forget(playerId: UUID, session: Any) {
        pending[playerId]?.removeAll { it.session === session }
        if (pending[playerId].isNullOrEmpty()) pending.remove(playerId)
        if (active[playerId]?.viewer?.session === session) remove(playerId)
    }

    private fun remove(id: UUID) {
        active.remove(id)?.let { it.viewer.hide(it.bar) }
    }

    @Synchronized
    override fun close() {
        closed = true
        pending.clear()
        active.keys.toList().forEach(::remove)
    }
}
