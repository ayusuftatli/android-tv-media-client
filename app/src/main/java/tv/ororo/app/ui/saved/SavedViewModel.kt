package tv.ororo.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SavedContentRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import tv.ororo.app.di.DefaultDispatcher

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
    private val watchProgressRepository: WatchProgressRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedUiState())
    val uiState: StateFlow<SavedUiState> = combine(
        _uiState, watchProgressRepository.watchedMovieIdsFlow()
    ) { state, watchedIds -> state.copy(watchedMovieIds = watchedIds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    private var filterJob: Job? = null
    private var loadJob: Job? = null

    private var allMovies: List<Movie> = emptyList()
    private var allShows: List<Show> = emptyList()
    private var savedKeys: Set<String> = emptySet()

    init {
        observeSavedKeys()
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
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                applySavedFilter()
            } catch (error: CancellationException) {
                throw error
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
        filterJob?.cancel()
        val keys = savedKeys
        val movies = allMovies
        val shows = allShows
        filterJob = viewModelScope.launch {
            val (savedMovies, savedShows) = withContext(defaultDispatcher) {
                val movieIds = SavedContentRepository.savedIdsForType(keys, SavedContentRepository.TYPE_MOVIE)
                val showIds = SavedContentRepository.savedIdsForType(keys, SavedContentRepository.TYPE_SHOW)
                movies.filter { it.id in movieIds }.sortedBy { it.normalizedTitle } to
                    shows.filter { it.id in showIds }.sortedBy { it.normalizedTitle }
            }
            _uiState.update { it.copy(savedMovies = savedMovies, savedShows = savedShows) }
        }
    }
}
