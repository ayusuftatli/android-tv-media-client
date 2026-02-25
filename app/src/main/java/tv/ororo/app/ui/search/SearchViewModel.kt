package tv.ororo.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import javax.inject.Inject

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
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var allMovies: List<Movie> = emptyList()
    private var allShows: List<Show> = emptyList()

    init {
        observeWatchStates()
        loadData()
    }

    private fun observeWatchStates() {
        viewModelScope.launch {
            watchProgressRepository.watchStatesFlow().collect { states ->
                val watchedMovieIds = states.values
                    .filter { it.completed && it.contentKey.startsWith("movie:") }
                    .mapNotNull { it.contentKey.substringAfter("movie:").toIntOrNull() }
                    .toSet()
                _uiState.value = _uiState.value.copy(watchedMovieIds = watchedMovieIds)
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                allMovies = repository.getMovies()
                allShows = repository.getShows()
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load data. Please try again."
                )
            }
        }
    }

    fun retry() {
        loadData()
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(
                movieResults = emptyList(),
                showResults = emptyList()
            )
            return
        }

        val terms = query.lowercase().split(" ").filter { it.isNotBlank() }
        val movies = allMovies.filter { movie ->
            terms.all { term -> movie.name.lowercase().contains(term) }
        }
        val shows = allShows.filter { show ->
            terms.all { term -> show.name.lowercase().contains(term) }
        }

        _uiState.value = _uiState.value.copy(
            movieResults = movies,
            showResults = shows
        )
    }
}
