package tv.ororo.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import tv.ororo.app.di.DefaultDispatcher

data class SearchUiState(
    val query: String = "",
    val movieResults: List<Movie> = emptyList(),
    val showResults: List<Show> = emptyList(),
    val watchedMovieIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: OroroRepository,
    private val watchProgressRepository: WatchProgressRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = combine(
        _uiState, watchProgressRepository.watchedMovieIdsFlow()
    ) { state, watchedIds -> state.copy(watchedMovieIds = watchedIds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())
    private val queryFlow = MutableStateFlow("")

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    private var allMovies: List<Movie> = emptyList()
    private var allShows: List<Show> = emptyList()

    init {
        observeQueryChanges()
        loadData()
    }

    private fun loadData() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val (movies, shows) = coroutineScope {
                    val movies = async { repository.getMovies() }
                    val shows = async { repository.getShows() }
                    movies.await() to shows.await()
                }
                allMovies = movies
                allShows = shows
                _uiState.value = _uiState.value.copy(isLoading = false)
                applyQuery(_uiState.value.query)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load data. Please try again."
                )
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQueryChanges() {
        viewModelScope.launch {
            queryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    applyQuery(query)
                }
        }
    }

    fun retry() {
        loadData()
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        queryFlow.value = query
    }

    private fun applyQuery(query: String) {
        searchJob?.cancel()
        val moviesSnapshot = allMovies
        val showsSnapshot = allShows
        searchJob = viewModelScope.launch {
            val (movies, shows) = withContext(defaultDispatcher) {
                if (query.length < 2) return@withContext emptyList<Movie>() to emptyList<Show>()
                val terms = query.lowercase(Locale.ROOT).split(" ").filter { it.isNotBlank() }
                moviesSnapshot.filter { movie -> terms.all(movie.normalizedTitle::contains) } to
                    showsSnapshot.filter { show -> terms.all(show.normalizedTitle::contains) }
            }
            if (query == queryFlow.value) {
                _uiState.update { it.copy(movieResults = movies, showResults = shows) }
            }
        }
    }
}
