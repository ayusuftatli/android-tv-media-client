package tv.ororo.app

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import tv.ororo.app.data.api.TmdbApi
import tv.ororo.app.data.model.dto.*
import tv.ororo.app.data.repository.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class TrendingCacheReuseTest(private val shows: Boolean) {
    @get:Rule val temporaryFolder = TemporaryFolder()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "shows={0}")
        fun types() = listOf(arrayOf(false), arrayOf(true))
    }

    @Test fun `refresh reuses persisted mappings and retries unresolved and newly ranked titles`() = runTest {
        val file = temporaryFolder.newFile()
        val api = FakeTmdbApi().apply { missingIds = setOf(2) }
        val first = repository(file, api)
        assertEquals(listOf(1), first.load(true) {})
        assertEquals(listOf(1, 2), api.lookups.sorted())

        // Recreate the repository to ensure reuse comes from disk, including legacy entries.
        api.missingIds = emptySet()
        val restarted = repository(file, api)
        assertEquals(listOf(1, 2), restarted.load(true) {})
        assertEquals(listOf(1, 2, 2), api.lookups.sorted())

        api.rankings = listOf(3, 2)
        assertEquals(listOf(3, 2), restarted.load(true) {})
        api.rankings = listOf(1, 3)
        assertEquals(listOf(1, 3), restarted.load(true) {})
        assertEquals(listOf(1, 2, 2, 3), api.lookups.sorted())
    }

    @Test fun `expired legacy cache is delivered before network refresh finishes`() = runTest {
        val file = temporaryFolder.newFile().apply {
            writeText("""{"fetched_at_epoch_ms":0,"entries":[{"rank":1,"tmdb_id":1,"imdb_id":"tt1"}]}""")
        }
        val gate = CompletableDeferred<Unit>()
        val api = FakeTmdbApi().apply { beforeRankings = { gate.await() } }
        val cached = CompletableDeferred<List<Int>>()
        val repository = repository(file, api)
        val refresh = async { repository.load(false) { cached.complete(it) } }

        assertEquals(listOf(1), cached.await())
        assertFalse(refresh.isCompleted)
        gate.complete(Unit)
        assertEquals(listOf(1, 2), refresh.await())
        assertEquals(listOf(2), api.lookups)
    }

    @Test fun `cancellation is propagated even when stale results exist`() = runTest {
        val file = temporaryFolder.newFile()
        val api = FakeTmdbApi()
        val repository = repository(file, api)
        repository.load(true) {}
        api.beforeRankings = { throw CancellationException("screen closed") }
        try {
            repository.load(true) {}
            fail("Refresh must propagate cancellation")
        } catch (_: CancellationException) {
            // Expected: cancellation must not be converted to a successful stale result.
        }
    }

    @Test fun `clear cache removes both rankings and reusable mappings`() = runTest {
        val api = FakeTmdbApi()
        val repository = repository(temporaryFolder.newFile(), api)
        repository.load(true) {}
        repository.clear()
        repository.load(false) {}
        assertEquals(listOf(1, 1, 2, 2), api.lookups.sorted())
    }

    private fun TestScope.repository(file: File, api: FakeTmdbApi): Repository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ororo = OroroRepository(FakeOroroApi().apply {
            movies = { MoviesResponse((1..3).map { MovieDto(it, "Movie $it", imdbId = "tt$it") }) }
            shows = { ShowsResponse((1..3).map { ShowDto(it, "Show $it", imdbId = "tt$it") }) }
        }, dispatcher)
        val limiter = TmdbRequestLimiter { testScheduler.currentTime }
        val json = Json { ignoreUnknownKeys = true }
        return if (shows) {
            val repository = TrendingShowsRepository(file, ororo, api, json, limiter, dispatcher) { true }
            Repository(
                load = { force, cached -> repository.getWeeklyTrendingShows(forceRefresh = force,
                    onCachedResult = { cached(it.shows.map { entry -> entry.show.id }) })
                    .shows.map { it.show.id } },
                clear = { repository.clearCache() }
            )
        } else {
            val repository = TrendingMoviesRepository(file, ororo, api, json, limiter, dispatcher) { true }
            Repository(
                load = { force, cached -> repository.getWeeklyTrendingMovies(forceRefresh = force,
                    onCachedResult = { cached(it.movies.map { entry -> entry.movie.id }) })
                    .movies.map { it.movie.id } },
                clear = { repository.clearCache() }
            )
        }
    }

    private class Repository(
        val load: suspend (Boolean, (List<Int>) -> Unit) -> List<Int>,
        val clear: suspend () -> Unit
    )

    private class FakeTmdbApi : TmdbApi {
        var rankings = listOf(1, 2)
        var missingIds = emptySet<Int>()
        val lookups = mutableListOf<Int>()
        var beforeRankings: suspend () -> Unit = {}

        override suspend fun getTrendingMovies(timeWindow: String, page: Int, language: String): TmdbTrendingMoviesResponse {
            beforeRankings()
            return TmdbTrendingMoviesResponse(page, rankings.map(::TmdbTrendingMovieDto), totalPages = 1)
        }
        override suspend fun getTrendingShows(timeWindow: String, page: Int, language: String): TmdbTrendingShowsResponse {
            beforeRankings()
            return TmdbTrendingShowsResponse(page, rankings.map(::TmdbTrendingShowDto), totalPages = 1)
        }
        override suspend fun getMovieDetails(movieId: Int, language: String): TmdbMovieDetailsDto {
            lookups += movieId
            return TmdbMovieDetailsDto(movieId, if (movieId in missingIds) null else "tt$movieId")
        }
        override suspend fun getTvExternalIds(seriesId: Int): TmdbTvExternalIdsDto {
            lookups += seriesId
            return TmdbTvExternalIdsDto(seriesId, if (seriesId in missingIds) null else "tt$seriesId")
        }
    }
}
