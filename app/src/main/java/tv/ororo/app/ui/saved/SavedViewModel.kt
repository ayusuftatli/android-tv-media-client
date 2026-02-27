package tv.ororo.app.ui.saved

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
import tv.ororo.app.data.repository.SavedContentRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import javax.inject.Inject

data class SavedUiState(
    val savedMovies: List<Movie> = emptyList(),
    val savedShows: List<Show> = emptyList(),
    val watchedMovieIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val repository: OroroRepository,
    private val savedContentRepository: SavedContentRepository,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedUiState())
    val uiState: StateFlow<SavedUiState> = _uiState.asStateFlow()

    private var allMovies: List<Movie> = emptyList()
    private var allShows: List<Show> = emptyList()
    private var savedKeys: Set<String> = emptySet()

    init {
        observeSavedKeys()
        observeWatchStates()
        loadData()
    }

    private fun observeSavedKeys() {
        viewModelScope.launch {
            savedContentRepository.savedKeysFlow.collect { keys ->
                savedKeys = keys
                applySavedFilter()
            }
        }
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
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                applySavedFilter()
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load saved content."
                )
            }
        }
    }

    fun retry() {
        loadData()
    }

    private fun applySavedFilter() {
        val savedMovieIds = SavedContentRepository.savedIdsForType(
            savedKeys,
            SavedContentRepository.TYPE_MOVIE
        )
        val savedShowIds = SavedContentRepository.savedIdsForType(
            savedKeys,
            SavedContentRepository.TYPE_SHOW
        )

        _uiState.value = _uiState.value.copy(
            savedMovies = allMovies
                .filter { it.id in savedMovieIds }
                .sortedBy { it.name.lowercase() },
            savedShows = allShows
                .filter { it.id in savedShowIds }
                .sortedBy { it.name.lowercase() }
        )
    }
}
