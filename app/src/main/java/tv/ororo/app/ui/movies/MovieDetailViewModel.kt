package tv.ororo.app.ui.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.ororo.app.data.api.HttpStatusException
import tv.ororo.app.data.auth.AuthEvent
import tv.ororo.app.data.auth.AuthEventBus
import tv.ororo.app.data.domain.model.MovieDetail
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SavedContentRepository
import tv.ororo.app.data.repository.SessionRepository
import javax.inject.Inject

data class MovieDetailUiState(
    val movie: MovieDetail? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OroroRepository,
    private val sessionRepository: SessionRepository,
    private val savedContentRepository: SavedContentRepository,
    private val authEventBus: AuthEventBus
) : ViewModel() {

    private val movieId: Int = savedStateHandle["movieId"] ?: 0

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        observeSavedState()
        loadMovie()
    }

    private fun observeSavedState() {
        viewModelScope.launch {
            savedContentRepository.isSavedFlow(SavedContentRepository.TYPE_MOVIE, movieId).collect { isSaved ->
                _uiState.value = _uiState.value.copy(isSaved = isSaved)
            }
        }
    }

    private fun loadMovie() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val movie = repository.getMovieDetail(movieId)
                _uiState.value = _uiState.value.copy(
                    movie = movie,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                val httpCode = (e as? HttpStatusException)?.code
                if (httpCode == 401) {
                    sessionRepository.clearSession()
                    repository.clearCache()
                    authEventBus.send(AuthEvent.SessionExpired)
                    return@launch
                }
                val errorMsg = when (httpCode) {
                    402 -> "Subscription required."
                    else -> "Failed to load movie details."
                }
                _uiState.value = _uiState.value.copy(
                    movie = null,
                    isLoading = false,
                    error = errorMsg
                )
            }
        }
    }

    fun toggleSaved() {
        viewModelScope.launch {
            savedContentRepository.toggleSaved(SavedContentRepository.TYPE_MOVIE, movieId)
        }
    }
}
