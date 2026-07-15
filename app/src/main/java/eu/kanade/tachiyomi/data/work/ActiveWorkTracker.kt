package eu.kanade.tachiyomi.data.work

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-local ownership for a WorkManager request, used by UI hot paths that must not
 * synchronously query WorkManager's database. UUID ownership prevents a cancelled old worker
 * from clearing the state of a newer replacement.
 */
internal class ActiveWorkTracker {
    private val activeId = AtomicReference<UUID?>(null)

    val isActive: Boolean
        get() = activeId.get() != null

    fun tryTrack(id: UUID): Boolean = activeId.compareAndSet(null, id)

    fun replace(id: UUID) {
        activeId.set(id)
    }

    fun finish(id: UUID): Boolean = activeId.compareAndSet(id, null)

    fun clear() {
        activeId.set(null)
    }
}
