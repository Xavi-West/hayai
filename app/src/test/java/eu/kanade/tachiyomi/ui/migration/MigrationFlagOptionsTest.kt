package eu.kanade.tachiyomi.ui.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MigrationFlagOptionsTest {

    @Test
    fun `positions map through the available flag order`() {
        val options = MigrationFlagOptions(listOf(0b0001, 0b0010, 0b1000))

        assertEquals(
            0b1001,
            options.getFlagsFromPositions(arrayOf(true, false, true)),
        )
    }

    @Test
    fun `extra positions are ignored defensively`() {
        val options = MigrationFlagOptions(listOf(0b0001))

        assertEquals(
            0b0001,
            options.getFlagsFromPositions(arrayOf(true, true)),
        )
    }
}
