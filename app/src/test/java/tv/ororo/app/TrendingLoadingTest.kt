package tv.ororo.app

import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
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
class TrendingLoadingTest(private val shows: Boolean) {
    @get:Rule val folder = TemporaryFolder()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "shows={0}")
        fun types() = listOf(arrayOf(false), arrayOf(true))
    }

    @Test fun `restart renders fresh snapshot without any network`() = runTest {
        val fixture = Fixture(this)
        fixture.repository().load()
        fixture.beforeCatalog = { error("Must not fetch catalog") }
        fixture.api.beforePage = { error("Must not fetch rankings") }
        val cached = mutableListOf<Result>()
        val result = fixture.repository().load(cached = cached::add)
        assertEquals(listOf(1, 2, 3), result.ids)
        assertFalse(result.stale)
        assertEquals(listOf(result), cached)
        assertEquals(1, fixture.catalogCalls)
        assertEquals(1, fixture.api.pageCalls.size)
    }

    @Test fun `expired availability displays snapshot before blocked catalog and updates without TMDB`() = runTest {
        val fixture = Fixture(this)
        fixture.repository().load()
        fixture.expire("catalog_fetched_at_epoch_ms")
        fixture.catalogIds = listOf(2, 3)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeCatalog = { gate.await() }
        fixture.api.beforePage = { error("Rankings remain fresh") }
        val cached = CompletableDeferred<Result>()
        val refresh = async { fixture.repository().load(cached = { cached.complete(it) }) }
        assertEquals(listOf(1, 2, 3), cached.await().ids)
        assertTrue(cached.await().stale)
        assertFalse(refresh.isCompleted)
        gate.complete(Unit)
        assertEquals(listOf(2, 3), refresh.await().ids)
        assertEquals(1, fixture.api.pageCalls.size)
    }

    @Test fun `ranking expiry reuses mappings and retains cached list until replacement ready`() = runTest {
        val fixture = Fixture(this)
        fixture.repository().load()
        fixture.expire("fetched_at_epoch_ms")
        fixture.api.pages = listOf(listOf(3, 2, 4))
        val gate = CompletableDeferred<Unit>()
        fixture.api.beforeLookup = { if (it == 4) gate.await() }
        val cached = CompletableDeferred<Result>()
        val partial = mutableListOf<Result>()
        val refresh = async { fixture.repository().load(cached = { cached.complete(it) }, partial = partial::add) }
        assertEquals(listOf(1, 2, 3), cached.await().ids)
        runCurrent()
        assertTrue(partial.isEmpty())
        gate.complete(Unit)
        assertEquals(listOf(3, 2, 4), refresh.await().ids)
        assertEquals(listOf(1, 2, 3, 4), fixture.api.lookups.sorted())
    }

    @Test fun `out of order lookups publish only completed ranking prefix and do not save partial snapshot`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.api.beforeLookup = { if (it == 2) gate.await() }
        val first = CompletableDeferred<Result>()
        val partials = mutableListOf<Result>()
        val load = async { fixture.repository().load(partial = { partials += it; first.complete(it) }) }
        assertEquals(listOf(1), first.await().ids)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(3 in fixture.api.lookups)
        assertTrue(partials.all { it.ids == listOf(1) })
        assertFalse(fixture.file.exists())
        gate.complete(Unit)
        val final = load.await()
        assertEquals(listOf(1, 2, 3), final.ids)
        assertEquals(listOf(1, 2, 3), final.ranks)
        assertTrue(fixture.file.exists())
    }

    @Test fun `page one results overlap later pages and duplicates keep first rank`() = runTest {
        val fixture = Fixture(this)
        fixture.api.pages = listOf(listOf(1, 2), listOf(2, 3), listOf(4))
        val gate = CompletableDeferred<Unit>()
        fixture.api.beforePage = { if (it > 1) gate.await() }
        val first = CompletableDeferred<Result>()
        val load = async { fixture.repository().load(partial = { first.complete(it) }) }
        assertEquals(listOf(1), first.await().ids)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1, 2, 3), fixture.api.pageCalls.sorted())
        assertFalse(load.isCompleted)
        gate.complete(Unit)
        val result = load.await()
        assertEquals(listOf(1, 2, 3, 4), result.ids)
        assertEquals(listOf(1, 2, 3, 4), result.ranks)
        assertEquals(listOf(1, 2, 3, 4), fixture.api.lookups.sorted())
    }

    @Test fun `later page failure preserves earlier completed cards without persisting a partial chart`() = runTest {
        val fixture = Fixture(this)
        fixture.api.pages = listOf(listOf(1, 2), listOf(3))
        fixture.api.beforePage = { if (it == 2) throw IOException("page unavailable") }
        val partials = mutableListOf<Result>()
        val result = fixture.repository().load(partial = partials::add)
        assertEquals(listOf(1, 2), result.ids)
        assertTrue(result.stale)
        assertTrue(partials.isNotEmpty())
        assertFalse(fixture.file.exists())
    }

    @Test fun `uncached offline load fails instead of returning an empty success`() = runTest {
        val fixture = Fixture(this)
        fixture.beforeCatalog = { throw IOException("offline") }
        try {
            fixture.repository().load()
            fail("Expected a network failure")
        } catch (_: IOException) {
            assertFalse(fixture.file.exists())
        }
    }

    @Test fun `concurrent callers share work and cancellation of one does not cancel another`() = runTest {
        val fixture = Fixture(this)
        val repository = fixture.repository()
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        fixture.api.beforeLookup = { started.complete(Unit); gate.await() }
        val one = async { repository.load(force = true) }
        val two = async { repository.load(force = true) }
        started.await()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, fixture.api.pageCalls.size)
        assertEquals(1, fixture.catalogCalls)
        one.cancelAndJoin()
        gate.complete(Unit)
        assertEquals(listOf(1, 2, 3), two.await().ids)
        assertEquals(listOf(1, 2, 3), fixture.api.lookups.sorted())
    }

    @Test fun `clearing during refresh cannot recreate file or serve prior memory`() = runTest {
        val fixture = Fixture(this)
        val repository = fixture.repository()
        repository.load()
        val gate = CompletableDeferred<Unit>()
        fixture.api.beforePage = { gate.await() }
        val refresh = async { repository.load(force = true) }
        runCurrent()
        repository.clear()
        gate.complete(Unit)
        refresh.join()
        assertTrue(refresh.isCancelled)
        assertFalse(fixture.file.exists())
        fixture.api.beforePage = {}
        repository.load()
        assertEquals(listOf(1, 1, 2, 2, 3, 3), fixture.api.lookups.sorted())
    }

    @Test fun `account switch cannot display another accounts snapshot and does reuse public mappings`() = runTest {
        val fixture = Fixture(this)
        fixture.repository().load()
        fixture.owner = "second-login"
        fixture.catalogIds = listOf(2)
        val cached = mutableListOf<Result>()
        val result = fixture.repository().load(cached = cached::add)
        assertEquals(listOf(2), result.ids)
        assertTrue(cached.all { it.ids == listOf(2) })
        assertEquals(1, fixture.api.pageCalls.size)
    }

    @Test fun `logout during lookup cancels result and prevents persistence`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        fixture.api.beforeLookup = { started.complete(Unit); gate.await() }
        val load = async { fixture.repository().load() }
        started.await()
        advanceTimeBy(500)
        runCurrent()
        fixture.owner = null
        gate.complete(Unit)
        load.join()
        assertTrue(load.isCancelled)
        assertFalse(fixture.file.exists())
    }

    @Test fun `failed background refresh returns stale snapshot without replacing disk`() = runTest {
        val fixture = Fixture(this)
        val repository = fixture.repository()
        repository.load()
        val before = fixture.file.readText()
        fixture.api.beforePage = { throw IOException("offline") }
        val result = repository.load(force = true)
        assertTrue(result.stale)
        assertEquals(listOf(1, 2, 3), result.ids)
        assertEquals(before, fixture.file.readText())
    }

    @Test fun `partial lookup failure keeps visible results but never marks cache fresh`() = runTest {
        val fixture = Fixture(this)
        fixture.api.beforeLookup = { if (it == 2) throw IOException("offline") }
        val result = fixture.repository().load()
        assertTrue(result.stale)
        assertEquals(listOf(1, 3), result.ids)
        assertFalse(fixture.file.exists())
    }

    @Test fun `corrupt cache recovers and cancelled first load does not persist`() = runTest {
        val fixture = Fixture(this)
        fixture.file.writeText("not json")
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        fixture.api.beforeLookup = { started.complete(Unit); gate.await() }
        val load = async { fixture.repository().load() }
        started.await()
        advanceTimeBy(500)
        runCurrent()
        load.cancelAndJoin()
        assertEquals("not json", fixture.file.readText())
        fixture.api.beforeLookup = {}
        assertEquals(listOf(1, 2, 3), fixture.repository().load().ids)
    }

    @Test fun `no matches returns final empty result without false partial empty state`() = runTest {
        val fixture = Fixture(this)
        fixture.catalogIds = emptyList()
        val partials = mutableListOf<Result>()
        assertTrue(fixture.repository().load(partial = partials::add).ids.isEmpty())
        assertTrue(partials.isEmpty())
        assertTrue(fixture.file.exists())
    }

    @Test fun `cold loading latency improves with identical 200ms responses`() = runTest {
        val fixture = Fixture(this)
        fixture.api.pages = (1..100).chunked(20)
        fixture.api.beforePage = { delay(200) }
        fixture.api.beforeLookup = { delay(200) }
        fixture.beforeCatalog = { delay(200) }
        var firstCardAt: Long? = null
        val result = fixture.repository().load(partial = { if (firstCardAt == null) firstCardAt = testScheduler.currentTime })
        val completedAt = testScheduler.currentTime
        assertEquals(100, result.ids.size)
        assertEquals(105, fixture.api.pageCalls.size + fixture.api.lookups.size)
        // Baseline: five sequential pages (1000ms), then 25 groups of four (5000ms), plus spacing.
        val baseline = legacyDuration()
        assertTrue("First card at $firstCardAt", firstCardAt!! < 1_000)
        assertTrue("New $completedAt vs baseline $baseline", completedAt < baseline * 0.7)
        println("TRENDING_BENCHMARK shows=$shows latency=200ms requests=105 baselineFirstAndComplete=${baseline}ms first=${firstCardAt}ms complete=${completedAt}ms")
    }

    private suspend fun TestScope.legacyDuration(): Long {
        val start = testScheduler.currentTime
        repeat(5) { delay(200) }
        val permits = kotlinx.coroutines.sync.Semaphore(4)
        val starts = kotlinx.coroutines.sync.Mutex()
        var nextStart = testScheduler.currentTime
        coroutineScope {
            (1..100).map {
                async {
                    permits.acquire()
                    try {
                        starts.lock()
                        try {
                            delay((nextStart - testScheduler.currentTime).coerceAtLeast(0))
                            nextStart = testScheduler.currentTime + 26
                        } finally { starts.unlock() }
                        delay(200)
                    } finally { permits.release() }
                }
            }.awaitAll()
        }
        return testScheduler.currentTime - start
    }

    private data class Result(val ids: List<Int>, val ranks: List<Int>, val stale: Boolean)
    private class Repository(
        private val fetch: suspend (Boolean, (Result) -> Unit, (Result) -> Unit) -> Result,
        val clear: suspend () -> Unit
    ) {
        suspend fun load(force: Boolean = false, cached: (Result) -> Unit = {}, partial: (Result) -> Unit = {}) =
            fetch(force, cached, partial)
    }

    private inner class Fixture(private val test: TestScope) {
        val file = File(folder.newFolder(), "trending.json")
        val api = Api()
        var owner: String? = "first-login"
        var catalogIds = (1..100).toList()
        var beforeCatalog: suspend () -> Unit = {}
        var catalogCalls = 0
        fun expire(field: String) {
            val fields = Json.parseToJsonElement(file.readText()).jsonObject.toMutableMap()
            fields[field] = JsonPrimitive(0)
            file.writeText(JsonObject(fields).toString())
        }
        fun repository(): Repository {
            val dispatcher = StandardTestDispatcher(test.testScheduler)
            val ororo = OroroRepository(FakeOroroApi().apply {
                movies = {
                    catalogCalls++
                    beforeCatalog()
                    MoviesResponse(catalogIds.map { MovieDto(it, "Movie $it", imdbId = "tt$it") })
                }
                shows = {
                    catalogCalls++
                    beforeCatalog()
                    ShowsResponse(catalogIds.map { ShowDto(it, "Show $it", imdbId = "tt$it") })
                }
            }, dispatcher)
            val limiter = TmdbRequestLimiter { test.testScheduler.currentTime }
            val json = Json { ignoreUnknownKeys = true }
            return if (shows) {
                val repo = TrendingShowsRepository(file, ororo, api, json, limiter, dispatcher,
                    accountScope = { owner }, scope = CoroutineScope(test.backgroundScope.coroutineContext +
                        SupervisorJob(test.backgroundScope.coroutineContext[Job]))) { true }
                fun result(value: tv.ororo.app.data.domain.model.TrendingShowsResult) =
                    Result(value.shows.map { it.show.id }, value.shows.map { it.rank }, value.isStale)
                Repository({ force, cached, partial -> result(repo.getWeeklyTrendingShows(force,
                    onCachedResult = { cached(result(it)) }, onPartialResult = { partial(result(it)) })) }, repo::clearCache)
            } else {
                val repo = TrendingMoviesRepository(file, ororo, api, json, limiter, dispatcher,
                    accountScope = { owner }, scope = CoroutineScope(test.backgroundScope.coroutineContext +
                        SupervisorJob(test.backgroundScope.coroutineContext[Job]))) { true }
                fun result(value: tv.ororo.app.data.domain.model.TrendingMoviesResult) =
                    Result(value.movies.map { it.movie.id }, value.movies.map { it.rank }, value.isStale)
                Repository({ force, cached, partial -> result(repo.getWeeklyTrendingMovies(force,
                    onCachedResult = { cached(result(it)) }, onPartialResult = { partial(result(it)) })) }, repo::clearCache)
            }
        }
    }

    private class Api : TmdbApi {
        var pages = listOf(listOf(1, 2, 3))
        var beforePage: suspend (Int) -> Unit = {}
        var beforeLookup: suspend (Int) -> Unit = {}
        val pageCalls = mutableListOf<Int>()
        val lookups = mutableListOf<Int>()
        private suspend fun page(page: Int): List<Int> {
            pageCalls += page
            beforePage(page)
            return pages[page - 1]
        }
        private suspend fun lookup(id: Int): String {
            lookups += id
            beforeLookup(id)
            return "tt$id"
        }
        override suspend fun getTrendingMovies(timeWindow: String, page: Int, language: String) =
            TmdbTrendingMoviesResponse(page, page(page).map(::TmdbTrendingMovieDto), totalPages = pages.size)
        override suspend fun getTrendingShows(timeWindow: String, page: Int, language: String) =
            TmdbTrendingShowsResponse(page, page(page).map(::TmdbTrendingShowDto), totalPages = pages.size)
        override suspend fun getMovieDetails(movieId: Int, language: String) = TmdbMovieDetailsDto(movieId, lookup(movieId))
        override suspend fun getTvExternalIds(seriesId: Int) = TmdbTvExternalIdsDto(seriesId, lookup(seriesId))
    }
}
