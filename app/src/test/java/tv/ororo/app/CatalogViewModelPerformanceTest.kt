package tv.ororo.app

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.ororo.app.data.model.dto.*
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import tv.ororo.app.ui.components.SortOption
import tv.ororo.app.ui.movies.MovieBrowseUiState
import tv.ororo.app.ui.movies.MovieBrowseViewModel
import tv.ororo.app.ui.search.SearchViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelPerformanceTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun `browse stays loading until sorted titles are ready and latest filter wins`() = runTest {
        val response = CompletableDeferred<MoviesResponse>()
        val api = FakeOroroApi().apply { movies = { response.await() } }
        val repository = WatchProgressRepository(MemoryPreferences(), Json, backgroundScope, dispatcher)
        val viewModel = MovieBrowseViewModel(OroroRepository(api, dispatcher), repository, dispatcher)
        store.put("browse", viewModel)
        val emissions = mutableListOf<MovieBrowseUiState>()
        backgroundScope.launch { viewModel.uiState.collect { emissions += it } }
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        response.complete(MoviesResponse(listOf(
            MovieDto(1, "Zulu", year = 2020, genres = listOf("Drama")),
            MovieDto(2, "alpha", year = 2024, genres = listOf("Drama")),
            MovieDto(3, "Beta", year = 2022, genres = listOf("Comedy"))
        )))
        runCurrent()
        assertEquals(listOf(2, 3, 1), viewModel.uiState.value.filteredMovies.map { it.id })
        assertTrue(emissions.filter { !it.isLoading }.all { it.filteredMovies.isNotEmpty() })

        viewModel.onGenreChanged("Comedy")
        viewModel.onSortChanged(SortOption.YEAR)
        viewModel.onGenreChanged("Drama")
        runCurrent()
        assertEquals(listOf(2, 1), viewModel.uiState.value.filteredMovies.map { it.id })
        assertEquals("Drama", viewModel.uiState.value.selectedGenre)
        assertEquals(SortOption.YEAR, viewModel.uiState.value.currentSort)
    }

    @Test fun `search starts both catalogs together and publishes only the current query`() = runTest {
        val movies = CompletableDeferred<MoviesResponse>()
        val shows = CompletableDeferred<ShowsResponse>()
        var showsStarted = false
        val api = FakeOroroApi().apply {
            this.movies = { movies.await() }
            this.shows = { showsStarted = true; shows.await() }
        }
        val viewModel = SearchViewModel(OroroRepository(api, dispatcher),
            WatchProgressRepository(MemoryPreferences(), Json, backgroundScope, dispatcher), dispatcher)
        store.put("search", viewModel)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(1, api.movieCalls)
        assertTrue(showsStarted)

        movies.complete(MoviesResponse(listOf(MovieDto(1, "Alpha Moon"), MovieDto(2, "Beta Moon"))))
        shows.complete(ShowsResponse(listOf(ShowDto(3, "Moon Beta"))))
        runCurrent()
        viewModel.onQueryChanged("alpha")
        advanceTimeBy(300)
        viewModel.onQueryChanged("BETA moon")
        advanceTimeBy(301)
        runCurrent()
        assertEquals(listOf(2), viewModel.uiState.value.movieResults.map { it.id })
        assertEquals(listOf(3), viewModel.uiState.value.showResults.map { it.id })
    }

    @Test fun `a failed parallel catalog request exits loading and allows retry`() = runTest {
        val movies = CompletableDeferred<MoviesResponse>()
        val api = FakeOroroApi().apply {
            this.movies = { movies.await() }
            shows = { throw IOException("offline") }
        }
        val viewModel = SearchViewModel(OroroRepository(api, dispatcher),
            WatchProgressRepository(MemoryPreferences(), Json, backgroundScope, dispatcher), dispatcher)
        store.put("search", viewModel)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)

        movies.complete(MoviesResponse(emptyList()))
        api.shows = { ShowsResponse(emptyList()) }
        viewModel.retry()
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }
}
