package tv.ororo.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeProgressTest {

    @Test
    fun invalidOrEmptyProgressIsZero() {
        assertEquals(0, calculateProgressPercent(positionMs = 0L, durationMs = 100L))
        assertEquals(0, calculateProgressPercent(positionMs = 10L, durationMs = 0L))
        assertEquals(0, calculateProgressPercent(positionMs = -1L, durationMs = 100L))
    }

    @Test
    fun progressIsNormalizedForDisplay() {
        assertEquals(1, calculateProgressPercent(positionMs = 1L, durationMs = 1_000L))
        assertEquals(50, calculateProgressPercent(positionMs = 500L, durationMs = 1_000L))
        assertEquals(99, calculateProgressPercent(positionMs = 1_500L, durationMs = 1_000L))
    }
}
