package tv.ororo.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.ororo.app.data.domain.model.Subtitle
import tv.ororo.app.data.repository.OroroRepository
import javax.inject.Inject

data class PlayerUiState(
    val streamUrl: String? = null,
    val title: String = "",
    val subtitles: List<Subtitle> = emptyList(),
    val selectedSubtitleLang: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: OroroRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadContent(type: String, id: Int) {
        if (_uiState.value.streamUrl != null) return

        viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            try {
                when (type) {
                    "movie" -> {
                        val movie = repository.getMovieDetail(id)
                        _uiState.value = PlayerUiState(
                            streamUrl = movie.streamUrl,
                            title = movie.name,
                            subtitles = movie.subtitles
                        )
                    }
                    "episode" -> {
                        val episode = repository.getEpisodeDetail(id)
                        val title = if (episode.showName != null && episode.season != null && episode.number != null) {
                            "${episode.showName} S%02dE%02d".format(episode.season, episode.number)
                        } else {
                            episode.name ?: "Episode"
                        }
                        _uiState.value = PlayerUiState(
                            streamUrl = episode.streamUrl,
                            title = title,
                            subtitles = episode.subtitles
                        )
                    }
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("402") == true -> "Subscription required for playback."
                    else -> "Failed to load stream."
                }
                _uiState.value = PlayerUiState(error = errorMsg)
            }
        }
    }

    fun selectSubtitle(lang: String?) {
        _uiState.value = _uiState.value.copy(selectedSubtitleLang = lang)
    }
}
