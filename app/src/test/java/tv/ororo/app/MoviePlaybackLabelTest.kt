package tv.ororo.app.ui.movies

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.ororo.app.data.repository.WatchState

class MoviePlaybackLabelTest {

    @Test
    fun labelReflectsPlaybackState() {
        assertEquals("Play", moviePlaybackLabel(null))
        assertEquals(
            "Play",
            moviePlaybackLabel(watchState(positionMs = 0L, completed = false))
        )
        assertEquals(
            "Resume",
            moviePlaybackLabel(watchState(positionMs = 5_000L, completed = false))
        )
        assertEquals(
            "Play again",
            moviePlaybackLabel(watchState(positionMs = 95_000L, completed = true))
        )
    }

    private fun watchState(positionMs: Long, completed: Boolean): WatchState {
        return WatchState(
            contentKey = "movie:42",
            positionMs = positionMs,
            durationMs = 100_000L,
            completed = completed,
            updatedAt = 1L
        )
    }
}
