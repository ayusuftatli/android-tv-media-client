package tv.ororo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.ororo.app.data.domain.model.Episode
import tv.ororo.app.ui.player.findNextEpisode

class PlayerNextEpisodeSelectionTest {

    @Test
    fun `next episode is selected from the same season`() {
        val episodes = listOf(
            Episode(id = 3, name = "Ep 3", season = 1, number = 3, airdate = null, plot = null, resolution = null),
            Episode(id = 1, name = "Ep 1", season = 1, number = 1, airdate = null, plot = null, resolution = null),
            Episode(id = 2, name = "Ep 2", season = 1, number = 2, airdate = null, plot = null, resolution = null)
        )

        val next = findNextEpisode(episodes, currentSeason = 1, currentNumber = 1)

        assertEquals(2, next?.id)
    }

    @Test
    fun `next episode rolls over to the following season`() {
        val episodes = listOf(
            Episode(id = 9, name = "S1E9", season = 1, number = 9, airdate = null, plot = null, resolution = null),
            Episode(id = 10, name = "S2E1", season = 2, number = 1, airdate = null, plot = null, resolution = null)
        )

        val next = findNextEpisode(episodes, currentSeason = 1, currentNumber = 9)

        assertEquals(10, next?.id)
    }

    @Test
    fun `next episode is null when current is the last episode`() {
        val episodes = listOf(
            Episode(id = 5, name = "Finale", season = 2, number = 10, airdate = null, plot = null, resolution = null)
        )

        val next = findNextEpisode(episodes, currentSeason = 2, currentNumber = 10)

        assertNull(next)
    }
}
