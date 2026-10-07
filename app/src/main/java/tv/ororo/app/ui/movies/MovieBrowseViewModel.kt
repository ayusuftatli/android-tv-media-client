package tv.ororo.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import tv.ororo.app.di.DefaultDispatcher
import tv.ororo.app.ui.components.SortOption

data class MovieBrowseUiState(
    val movies: List<Movie> = emptyList(),
    val filteredMovies: List<Movie> = emptyList(),
    val watchedMovieIds: Set<Int> = emptySet(),
    val genres: List<String> = emptyList(),
    val currentSort: SortOption = SortOption.TITLE,
    val selectedGenre: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieBrowseViewModel @Inject constructor(
    private val repository: OroroRepository,
    private val watchProgressRepository: WatchProgressRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieBrowseUiState(isLoading = true))
    val uiState: StateFlow<MovieBrowseUiState> = combine(
        _uiState, watchProgressRepository.watchedMovieIdsFlow()
    ) { state, watchedIds -> state.copy(watchedMovieIds = watchedIds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovieBrowseUiState(isLoading = true))

    private var filterJob: Job? = null

    init {
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val movies = repository.getMovies()
                val genres = withContext(defaultDispatcher) {
                    movies.flatMap { it.genres }.distinct().sorted()
                }
                _uiState.value = _uiState.value.copy(
                    movies = movies,
                    genres = genres
                )
                applyFilters()
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load movies"
                )
            }
        }
    }

    fun onSortChanged(sort: SortOption) {
        _uiState.value = _uiState.value.copy(currentSort = sort)
        applyFilters()
    }

    fun onGenreChanged(genre: String?) {
        _uiState.value = _uiState.value.copy(selectedGenre = genre)
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val result = withContext(defaultDispatcher) {
                var filtered = state.movies

                if (state.selectedGenre != null) {
                    filtered = filtered.filter { state.selectedGenre in it.genres }
                }

                filtered = when (state.currentSort) {
                    SortOption.TITLE -> filtered.sortedBy { it.normalizedTitle }
                    SortOption.ADDED -> filtered.sortedByDescending { it.updatedAt ?: "" }
                    SortOption.YEAR -> filtered.sortedByDescending { it.year ?: 0 }
                    SortOption.RATING -> filtered.sortedByDescending { it.imdbRating ?: 0.0 }
                }

                filtered
            }
            _uiState.update { it.copy(filteredMovies = result, isLoading = false) }
        }
    }
}
