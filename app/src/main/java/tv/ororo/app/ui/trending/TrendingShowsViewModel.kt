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
import tv.ororo.app.data.domain.model.TrendingShow
import tv.ororo.app.data.repository.TmdbConfigurationException
import tv.ororo.app.data.repository.TrendingShowsRepository

data class TrendingShowsUiState(
    val shows: List<TrendingShow> = emptyList(),
    val rankedShowCount: Int = 0,
    val completedLookups: Int = 0,
    val totalLookups: Int = 0,
    val isLoading: Boolean = false,
    val isStale: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TrendingShowsViewModel @Inject constructor(
    private val repository: TrendingShowsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrendingShowsUiState())
    val uiState: StateFlow<TrendingShowsUiState> = _uiState.asStateFlow()

    init {
        loadTrendingShows()
    }

    fun retry() {
        loadTrendingShows(forceRefresh = true)
    }

    private fun loadTrendingShows(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = TrendingShowsUiState(isLoading = true)
            try {
                val result = repository.getWeeklyTrendingShows(
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
                        shows = result.shows,
                        rankedShowCount = result.rankedShowCount,
                        isLoading = false,
                        isStale = result.isStale,
                        error = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = error.toTrendingShowsUserMessage()
                    )
                }
            }
        }
    }
}

private fun Exception.toTrendingShowsUserMessage(): String = when (this) {
    is TmdbConfigurationException ->
        "TMDB is not configured. Add TMDB_READ_ACCESS_TOKEN to local.properties."
    is HttpException -> when (code()) {
        401, 403 -> "TMDB rejected the configured access token."
        429 -> "TMDB is busy. Please wait a moment and try again."
        else -> "TMDB could not load trending TV series."
    }
    is IOException -> "Check your connection and try loading trending TV series again."
    else -> "Trending TV series could not be loaded."
}
