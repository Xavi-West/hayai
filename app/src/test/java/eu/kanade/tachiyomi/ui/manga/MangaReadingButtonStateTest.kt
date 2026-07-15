package eu.kanade.tachiyomi.ui.manga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaReadingButtonStateTest {

    @Test
    fun `partially read numbered chapter shows continue with chapter number`() {
        val state = MangaReadingButtonState(chapterNumber = 22.0, hasProgress = true)

        assertEquals(MangaReadingButtonLabel.CONTINUE_CHAPTER, state.label)
    }

    @Test
    fun `unstarted numbered chapter shows start with chapter number`() {
        val state = MangaReadingButtonState(chapterNumber = 23.0, hasProgress = false)

        assertEquals(MangaReadingButtonLabel.START_CHAPTER, state.label)
    }

    @Test
    fun `unnumbered chapter keeps a generic continue label`() {
        val state = MangaReadingButtonState(chapterNumber = -1.0, hasProgress = true)

        assertEquals(MangaReadingButtonLabel.CONTINUE, state.label)
    }

    @Test
    fun `fab becomes the fallback when header action is unavailable`() {
        val showFab = shouldShowMangaReadingFab(
            isTablet = false,
            hasReadingTarget = true,
            isSelectionMode = false,
            headerActionVisible = false,
            headerActionInViewport = false,
        )

        assertEquals(true, showFab)
    }

    @Test
    fun `fab stays hidden while header action is visible on screen`() {
        val showFab = shouldShowMangaReadingFab(
            isTablet = false,
            hasReadingTarget = true,
            isSelectionMode = false,
            headerActionVisible = true,
            headerActionInViewport = true,
        )

        assertEquals(false, showFab)
    }

    @Test
    fun `selection mode suppresses all reading fab motion`() {
        val showFab = shouldShowMangaReadingFab(
            isTablet = false,
            hasReadingTarget = true,
            isSelectionMode = true,
            headerActionVisible = false,
            headerActionInViewport = false,
        )

        assertEquals(false, showFab)
    }
}
