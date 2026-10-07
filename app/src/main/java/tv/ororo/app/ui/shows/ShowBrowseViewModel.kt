package tv.ororo.app.ui.shows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.di.DefaultDispatcher
import tv.ororo.app.ui.components.SortOption

data class ShowBrowseUiState(
    val shows: List<Show> = emptyList(),
    val filteredShows: List<Show> = emptyList(),
    val genres: List<String> = emptyList(),
    val currentSort: SortOption = SortOption.TITLE,
    val selectedGenre: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShowBrowseViewModel @Inject constructor(
    private val repository: OroroRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShowBrowseUiState(isLoading = true))
    val uiState: StateFlow<ShowBrowseUiState> = _uiState.asStateFlow()

    private var filterJob: Job? = null

    init {
        loadShows()
    }

    private fun loadShows() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val shows = repository.getShows()
                val genres = withContext(defaultDispatcher) {
                    shows.flatMap { it.genres }.distinct().sorted()
                }
                _uiState.value = _uiState.value.copy(
                    shows = shows,
                    genres = genres
                )
                applyFilters()
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load shows"
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
                var filtered = state.shows

                if (state.selectedGenre != null) {
                    filtered = filtered.filter { state.selectedGenre in it.genres }
                }

                filtered = when (state.currentSort) {
                    SortOption.TITLE -> filtered.sortedBy { it.normalizedTitle }
                    SortOption.ADDED -> filtered.sortedByDescending { it.newestVideo ?: "" }
                    SortOption.YEAR -> filtered.sortedByDescending { it.year ?: 0 }
                    SortOption.RATING -> filtered.sortedByDescending { it.imdbRating ?: 0.0 }
                }

                filtered
            }
            _uiState.update { it.copy(filteredShows = result, isLoading = false) }
        }
    }
}
