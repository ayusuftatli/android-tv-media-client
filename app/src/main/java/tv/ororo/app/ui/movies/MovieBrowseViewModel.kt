package tv.ororo.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.ui.components.SortOption
import javax.inject.Inject

data class MovieBrowseUiState(
    val movies: List<Movie> = emptyList(),
    val filteredMovies: List<Movie> = emptyList(),
    val genres: List<String> = emptyList(),
    val currentSort: SortOption = SortOption.TITLE,
    val selectedGenre: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieBrowseViewModel @Inject constructor(
    private val repository: OroroRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieBrowseUiState())
    val uiState: StateFlow<MovieBrowseUiState> = _uiState.asStateFlow()

    init {
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val movies = repository.getMovies()
                val genres = movies.flatMap { it.genres }.distinct().sorted()
                _uiState.value = _uiState.value.copy(
                    movies = movies,
                    genres = genres,
                    isLoading = false
                )
                applyFilters()
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
        var filtered = state.movies

        if (state.selectedGenre != null) {
            filtered = filtered.filter { state.selectedGenre in it.genres }
        }

        filtered = when (state.currentSort) {
            SortOption.TITLE -> filtered.sortedBy { it.name.lowercase() }
            SortOption.ADDED -> filtered.sortedByDescending { it.updatedAt ?: "" }
            SortOption.YEAR -> filtered.sortedByDescending { it.year ?: 0 }
            SortOption.RATING -> filtered.sortedByDescending { it.imdbRating ?: 0.0 }
        }

        _uiState.value = state.copy(filteredMovies = filtered)
    }
}
