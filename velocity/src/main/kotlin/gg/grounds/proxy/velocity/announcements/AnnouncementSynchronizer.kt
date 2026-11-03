package gg.grounds.proxy.velocity.announcements

import java.util.concurrent.atomic.AtomicBoolean

class AnnouncementSynchronizer(
    private val read: (Long?) -> AnnouncementSnapshot,
    private val apply: (AnnouncementSettings?) -> Unit,
    private val failure: (Throwable) -> Unit,
) : AutoCloseable {
    private val reading = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var revision: Long? = null

    fun refresh() {
        if (closed || !reading.compareAndSet(false, true)) return
        try {
            val snapshot = read(revision)
            synchronized(this) {
                if (!closed && snapshot is AnnouncementSnapshot.Changed) {
                    apply(snapshot.settings)
                    revision = snapshot.version
                }
            }
        } catch (error: Exception) {
            if (!closed) failure(error)
        } finally {
            reading.set(false)
        }
    }

    @Synchronized
    override fun close() {
        closed = true
    }
}
