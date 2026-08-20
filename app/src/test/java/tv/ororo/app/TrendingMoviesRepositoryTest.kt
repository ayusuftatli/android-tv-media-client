package tv.ororo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.repository.RankedImdbMovie
import tv.ororo.app.data.repository.TrendingMoviesRepository
import tv.ororo.app.data.repository.matchTrendingMovies

class TrendingMoviesRepositoryTest {

    @Test
    fun `IMDb ids are normalized across Ororo and TMDB formats`() {
        assertEquals("tt0133093", TrendingMoviesRepository.normalizeImdbId("0133093"))
        assertEquals("tt0133093", TrendingMoviesRepository.normalizeImdbId("TT0133093"))
        assertNull(TrendingMoviesRepository.normalizeImdbId("not-an-imdb-id"))
        assertNull(TrendingMoviesRepository.normalizeImdbId(null))
    }

    @Test
    fun `matching keeps TMDB rank order and removes unavailable titles`() {
        val firstMovie = movie(id = 10, imdbId = "0133093")
        val secondMovie = movie(id = 20, imdbId = "tt0816692")

        val matches = matchTrendingMovies(
            rankings = listOf(
                RankedImdbMovie(rank = 1, imdbId = "tt9999999"),
                RankedImdbMovie(rank = 2, imdbId = "TT0816692"),
                RankedImdbMovie(rank = 3, imdbId = "tt0133093")
            ),
            ororoMovies = listOf(firstMovie, secondMovie)
        )

        assertEquals(listOf(2, 3), matches.map { it.rank })
        assertEquals(listOf(20, 10), matches.map { it.movie.id })
    }

    @Test
    fun `trending cache expires after 24 hours`() {
        val fetchedAt = 1_000L
        val oneDayMs = 24 * 60 * 60 * 1_000L

        assertTrue(
            TrendingMoviesRepository.isTrendingCacheFresh(
                fetchedAtEpochMs = fetchedAt,
                nowEpochMs = fetchedAt + oneDayMs
            )
        )
        assertFalse(
            TrendingMoviesRepository.isTrendingCacheFresh(
                fetchedAtEpochMs = fetchedAt,
                nowEpochMs = fetchedAt + oneDayMs + 1
            )
        )
        assertFalse(
            TrendingMoviesRepository.isTrendingCacheFresh(
                fetchedAtEpochMs = fetchedAt,
                nowEpochMs = fetchedAt - 1
            )
        )
    }

    private fun movie(id: Int, imdbId: String) = Movie(
        id = id,
        name = "Movie $id",
        year = 2000,
        imdbId = imdbId,
        posterUrl = null,
        backdropUrl = null,
        imdbRating = null,
        genres = emptyList(),
        description = null,
        updatedAt = null
    )
}
