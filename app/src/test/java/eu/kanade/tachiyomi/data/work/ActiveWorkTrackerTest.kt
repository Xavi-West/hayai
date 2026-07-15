package eu.kanade.tachiyomi.data.work

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ActiveWorkTrackerTest {

    @Test
    fun `old worker cannot clear replacement ownership`() {
        val tracker = ActiveWorkTracker()
        val old = UUID.randomUUID()
        val replacement = UUID.randomUUID()

        tracker.replace(old)
        tracker.replace(replacement)

        assertFalse(tracker.finish(old))
        assertTrue(tracker.isActive)
        assertTrue(tracker.finish(replacement))
        assertFalse(tracker.isActive)
    }

    @Test
    fun `only one pending request can claim empty ownership`() {
        val tracker = ActiveWorkTracker()
        val first = UUID.randomUUID()

        assertTrue(tracker.tryTrack(first))
        assertFalse(tracker.tryTrack(UUID.randomUUID()))
        assertTrue(tracker.finish(first))
        assertFalse(tracker.isActive)
    }
}
