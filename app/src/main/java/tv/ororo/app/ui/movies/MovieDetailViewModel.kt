package tv.ororo.app.ui.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.ororo.app.data.domain.model.MovieDetail
import tv.ororo.app.data.repository.OroroRepository
import javax.inject.Inject

data class MovieDetailUiState(
    val movie: MovieDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OroroRepository
) : ViewModel() {

    private val movieId: Int = savedStateHandle["movieId"] ?: 0

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        loadMovie()
    }

    private fun loadMovie() {
        viewModelScope.launch {
            _uiState.value = MovieDetailUiState(isLoading = true)
            try {
                val movie = repository.getMovieDetail(movieId)
                _uiState.value = MovieDetailUiState(movie = movie)
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("401") == true -> "Session expired. Please log in again."
                    e.message?.contains("402") == true -> "Subscription required."
                    else -> "Failed to load movie details."
                }
                _uiState.value = MovieDetailUiState(error = errorMsg)
            }
        }
    }
}
