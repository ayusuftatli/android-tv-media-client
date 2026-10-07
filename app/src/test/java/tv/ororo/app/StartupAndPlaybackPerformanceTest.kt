package tv.ororo.app

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.ororo.app.data.api.HttpStatusException
import tv.ororo.app.data.auth.AuthEvent
import tv.ororo.app.data.auth.AuthEventBus
import tv.ororo.app.data.model.dto.*
import tv.ororo.app.data.repository.*
import tv.ororo.app.ui.player.PlayerViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class StartupAndPlaybackPerformanceTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `Home becomes available before validation and network failure keeps the session`() = runTest {
        val session = SessionRepository(MemoryPreferences())
        session.saveCredentials("user", "password")
        val response = CompletableDeferred<MoviesResponse>()
        val api = FakeOroroApi().apply { movies = { response.await() } }
        val viewModel = MainViewModel(session, OroroRepository(api, dispatcher), AuthEventBus())

        runCurrent()
        assertEquals(true, viewModel.isLoggedIn.value)
        assertFalse(response.isCompleted)

        response.completeExceptionally(IOException("offline"))
        runCurrent()
        assertEquals("user" to "password", session.getCredentials())
    }

    @Test fun `unauthorized startup still expires the saved session`() = runTest {
        val session = SessionRepository(MemoryPreferences())
        session.saveCredentials("user", "password")
        val api = FakeOroroApi().apply { movies = { throw HttpStatusException(401) } }
        val events = AuthEventBus()
        val event = async { events.events.first() }
        MainViewModel(session, OroroRepository(api, dispatcher), events)

        runCurrent()
        assertNull(session.getCredentials())
        assertEquals(AuthEvent.SessionExpired, event.await())
    }

    @Test fun `episode stream is ready before next episode metadata and retains subtitle changes`() = runTest {
        val showResponse = CompletableDeferred<ShowDto>()
        val api = FakeOroroApi().apply {
            episode = {
                EpisodeDetailDto(it, showId = 10, showName = "Show", season = 1, number = 1,
                    url = "https://example.test/stream.m3u8")
            }
            show = { showResponse.await() }
        }
        val viewModel = PlayerViewModel(
            OroroRepository(api, dispatcher), SessionRepository(MemoryPreferences()),
            SubtitlePreferencesRepository(MemoryPreferences()),
            WatchProgressRepository(MemoryPreferences(), Json, backgroundScope, dispatcher),
            AuthEventBus(), backgroundScope
        )
        viewModel.loadContent("episode", 1)
        viewModel.loadContent("episode", 1)
        runCurrent()

        assertEquals(1, api.episodeCalls)
        assertEquals("https://example.test/stream.m3u8", viewModel.uiState.value.streamUrl)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.nextEpisode)

        viewModel.setSubtitleSelection("de")
        runCurrent()
        showResponse.complete(ShowDto(10, "Show", episodes = listOf(
            EpisodeDto(1, season = 1, number = 1), EpisodeDto(2, season = 1, number = 2)
        )))
        runCurrent()
        assertEquals(2, viewModel.uiState.value.nextEpisode?.id)
        assertEquals("de", viewModel.uiState.value.selectedSubtitleLang)
        assertEquals("https://example.test/stream.m3u8", viewModel.uiState.value.streamUrl)
    }

    @Test fun `simultaneous catalog callers share a download and clearing discards an in-flight result`() = runTest {
        val response = CompletableDeferred<MoviesResponse>()
        val api = FakeOroroApi().apply { movies = { response.await() } }
        val repository = OroroRepository(api, dispatcher)
        val first = async { repository.getMovies() }
        val second = async { repository.getMovies() }
        runCurrent()
        assertEquals(1, api.movieCalls)
        response.complete(MoviesResponse(listOf(MovieDto(1, "Movie"))))
        runCurrent()
        assertSame(first.await(), second.await())

        val refreshed = CompletableDeferred<MoviesResponse>()
        api.movies = { refreshed.await() }
        val pending = async { repository.getMovies(forceRefresh = true) }
        runCurrent()
        repository.clearCache()
        refreshed.complete(MoviesResponse(emptyList()))
        pending.await()
        repository.getMovies()
        assertEquals(3, api.movieCalls)
    }
}
