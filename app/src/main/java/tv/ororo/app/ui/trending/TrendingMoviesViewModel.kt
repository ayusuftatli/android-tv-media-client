package tv.ororo.app.ui.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import tv.ororo.app.data.domain.model.TrendingMovie
import tv.ororo.app.data.repository.TmdbConfigurationException
import tv.ororo.app.data.repository.TrendingMoviesRepository

data class TrendingMoviesUiState(
    val movies: List<TrendingMovie> = emptyList(),
    val rankedMovieCount: Int = 0,
    val completedLookups: Int = 0,
    val totalLookups: Int = 0,
    val isLoading: Boolean = false,
    val isStale: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TrendingMoviesViewModel @Inject constructor(
    private val repository: TrendingMoviesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrendingMoviesUiState())
    val uiState: StateFlow<TrendingMoviesUiState> = _uiState.asStateFlow()

    init {
        loadTrendingMovies()
    }

    fun retry() {
        loadTrendingMovies(forceRefresh = true)
    }

    private fun loadTrendingMovies(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = TrendingMoviesUiState(isLoading = true)
            try {
                val result = repository.getWeeklyTrendingMovies(
                    forceRefresh = forceRefresh,
                    onProgress = { completed, total ->
                        _uiState.update { state ->
                            state.copy(
                                completedLookups = completed,
                                totalLookups = total
                            )
                        }
                    }
                )
                _uiState.update { state ->
                    state.copy(
                        movies = result.movies,
                        rankedMovieCount = result.rankedMovieCount,
                        isLoading = false,
                        isStale = result.isStale,
                        error = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = error.toUserMessage()
                    )
                }
            }
        }
    }
}

internal fun Exception.toUserMessage(): String = when (this) {
    is TmdbConfigurationException ->
        "TMDB is not configured. Add TMDB_READ_ACCESS_TOKEN to local.properties."
    is HttpException -> when (code()) {
        401, 403 -> "TMDB rejected the configured access token."
        429 -> "TMDB is busy. Please wait a moment and try again."
        else -> "TMDB could not load trending movies."
    }
    is IOException -> "Check your connection and try loading trending movies again."
    else -> "Trending movies could not be loaded."
}
