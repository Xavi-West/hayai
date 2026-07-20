package eu.kanade.tachiyomi.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootTabsMotionTest {

    @Test
    fun `forward tab motion enters from the end edge`() {
        assertEquals(1f, rootTabTransitionDirection(previousIndex = 0, currentIndex = 2, isRtl = false))
    }

    @Test
    fun `backward tab motion enters from the start edge`() {
        assertEquals(-1f, rootTabTransitionDirection(previousIndex = 2, currentIndex = 0, isRtl = false))
    }

    @Test
    fun `rtl mirrors tab motion`() {
        assertEquals(-1f, rootTabTransitionDirection(previousIndex = 0, currentIndex = 2, isRtl = true))
        assertEquals(1f, rootTabTransitionDirection(previousIndex = 2, currentIndex = 0, isRtl = true))
    }
}
