package tv.ororo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.repository.RankedImdbShow
import tv.ororo.app.data.repository.TrendingShowsRepository
import tv.ororo.app.data.repository.matchTrendingShows

class TrendingShowsRepositoryTest {

    @Test
    fun `IMDb ids are normalized across Ororo and TMDB TV formats`() {
        assertEquals("tt0944947", TrendingShowsRepository.normalizeImdbId("0944947"))
        assertEquals("tt0944947", TrendingShowsRepository.normalizeImdbId("TT0944947"))
        assertNull(TrendingShowsRepository.normalizeImdbId("not-an-imdb-id"))
        assertNull(TrendingShowsRepository.normalizeImdbId(null))
    }

    @Test
    fun `matching keeps TMDB TV rank order and removes unavailable series`() {
        val firstShow = show(id = 10, imdbId = "0944947")
        val secondShow = show(id = 20, imdbId = "tt0903747")

        val matches = matchTrendingShows(
            rankings = listOf(
                RankedImdbShow(rank = 1, imdbId = "tt9999999"),
                RankedImdbShow(rank = 2, imdbId = "TT0903747"),
                RankedImdbShow(rank = 3, imdbId = "tt0944947")
            ),
            ororoShows = listOf(firstShow, secondShow)
        )

        assertEquals(listOf(2, 3), matches.map { it.rank })
        assertEquals(listOf(20, 10), matches.map { it.show.id })
    }

    @Test
    fun `trending TV cache expires after 24 hours`() {
        val fetchedAt = 1_000L
        val oneDayMs = 24 * 60 * 60 * 1_000L

        assertTrue(
            TrendingShowsRepository.isTrendingCacheFresh(
                fetchedAtEpochMs = fetchedAt,
                nowEpochMs = fetchedAt + oneDayMs
            )
        )
        assertFalse(
            TrendingShowsRepository.isTrendingCacheFresh(
                fetchedAtEpochMs = fetchedAt,
                nowEpochMs = fetchedAt + oneDayMs + 1
            )
        )
        assertFalse(
            TrendingShowsRepository.isTrendingCacheFresh(
                fetchedAtEpochMs = fetchedAt,
                nowEpochMs = fetchedAt - 1
            )
        )
    }

    private fun show(id: Int, imdbId: String) = Show(
        id = id,
        name = "Show $id",
        year = 2000,
        imdbId = imdbId,
        posterUrl = null,
        backdropUrl = null,
        imdbRating = null,
        genres = emptyList(),
        description = null,
        ended = null,
        seasonCount = null,
        newestVideo = null,
        userPopularity = null
    )
}
