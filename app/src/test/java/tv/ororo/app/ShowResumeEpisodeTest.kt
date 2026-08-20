package tv.ororo.app.ui.shows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.ororo.app.data.domain.model.Episode
import tv.ororo.app.data.repository.WatchState

class ShowResumeEpisodeTest {

    private val firstEpisode = episode(id = 10, season = 1, number = 1)
    private val secondEpisode = episode(id = 20, season = 2, number = 4)

    @Test
    fun newestIncompleteEpisodeIsSelected() {
        val result = findResumeEpisode(
            episodes = listOf(firstEpisode, secondEpisode),
            watchStatesByEpisodeId = mapOf(
                firstEpisode.id to watchState(firstEpisode.id, updatedAt = 100L),
                secondEpisode.id to watchState(secondEpisode.id, updatedAt = 200L)
            )
        )

        assertEquals(secondEpisode, result)
        assertEquals("S02E04", formatEpisodeCode(result!!))
    }

    @Test
    fun completedAndInvalidStatesAreIgnored() {
        val result = findResumeEpisode(
            episodes = listOf(firstEpisode, secondEpisode),
            watchStatesByEpisodeId = mapOf(
                firstEpisode.id to watchState(firstEpisode.id, updatedAt = 200L, completed = true),
                secondEpisode.id to watchState(secondEpisode.id, updatedAt = 300L, positionMs = 0L)
            )
        )

        assertNull(result)
    }

    private fun episode(id: Int, season: Int, number: Int): Episode {
        return Episode(
            id = id,
            name = null,
            season = season,
            number = number,
            airdate = null,
            plot = null,
            resolution = null
        )
    }

    private fun watchState(
        episodeId: Int,
        updatedAt: Long,
        completed: Boolean = false,
        positionMs: Long = 10_000L
    ): WatchState {
        return WatchState(
            contentKey = "episode:$episodeId",
            positionMs = positionMs,
            durationMs = 100_000L,
            completed = completed,
            updatedAt = updatedAt
        )
    }
}
