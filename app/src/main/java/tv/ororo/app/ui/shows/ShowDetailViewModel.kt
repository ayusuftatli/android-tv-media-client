package tv.ororo.app.ui.shows

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.ororo.app.data.domain.model.Episode
import tv.ororo.app.data.domain.model.ShowDetail
import tv.ororo.app.data.repository.OroroRepository
import javax.inject.Inject

data class ShowDetailUiState(
    val show: ShowDetail? = null,
    val selectedSeason: Int = 1,
    val seasonEpisodes: List<Episode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShowDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OroroRepository
) : ViewModel() {

    private val showId: Int = savedStateHandle["showId"] ?: 0

    private val _uiState = MutableStateFlow(ShowDetailUiState())
    val uiState: StateFlow<ShowDetailUiState> = _uiState.asStateFlow()

    init {
        loadShow()
    }

    private fun loadShow() {
        viewModelScope.launch {
            _uiState.value = ShowDetailUiState(isLoading = true)
            try {
                val show = repository.getShowDetail(showId)
                val seasons = show.episodes.map { it.season }.distinct().sorted()
                val firstSeason = seasons.firstOrNull() ?: 1
                _uiState.value = ShowDetailUiState(
                    show = show,
                    selectedSeason = firstSeason,
                    seasonEpisodes = show.episodes.filter { it.season == firstSeason }
                        .sortedBy { it.number }
                )
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("401") == true -> "Session expired. Please log in again."
                    e.message?.contains("402") == true -> "Subscription required."
                    else -> "Failed to load show details."
                }
                _uiState.value = ShowDetailUiState(error = errorMsg)
            }
        }
    }

    fun onSeasonSelected(season: Int) {
        val show = _uiState.value.show ?: return
        _uiState.value = _uiState.value.copy(
            selectedSeason = season,
            seasonEpisodes = show.episodes.filter { it.season == season }
                .sortedBy { it.number }
        )
    }
}
