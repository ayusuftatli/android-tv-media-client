package tv.ororo.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.ororo.app.data.api.HttpStatusException
import tv.ororo.app.data.auth.AuthEvent
import tv.ororo.app.data.auth.AuthEventBus
import tv.ororo.app.data.domain.model.Episode
import tv.ororo.app.data.domain.model.EpisodeDetail
import tv.ororo.app.data.domain.model.Subtitle
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SessionRepository
import tv.ororo.app.data.repository.SubtitlePreferencesRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import java.util.Locale
import javax.inject.Inject

data class NextEpisodeUi(
    val id: Int,
    val label: String,
    val title: String?
)

data class PlayerUiState(
    val streamUrl: String? = null,
    val title: String = "",
    val subtitles: List<Subtitle> = emptyList(),
    val resumePositionMs: Long = 0L,
    val selectedSubtitleLang: String? = null,
    val subtitlesEnabled: Boolean = true,
    val nextEpisode: NextEpisodeUi? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: OroroRepository,
    private val sessionRepository: SessionRepository,
    private val subtitlePreferencesRepository: SubtitlePreferencesRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val authEventBus: AuthEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadContent(type: String, id: Int) {
        if (_uiState.value.streamUrl != null) return

        viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            try {
                val subtitlesEnabled = subtitlePreferencesRepository.subtitlesEnabled.first()
                val preferredSubtitleLang = subtitlePreferencesRepository.preferredSubtitleLang.first()
                val contentKey = WatchProgressRepository.contentKey(type, id)
                val savedWatchState = watchProgressRepository.getWatchState(contentKey)
                val resumePositionMs = savedWatchState
                    ?.takeIf { !it.completed && it.positionMs > 0L }
                    ?.positionMs
                    ?: 0L

                when (type) {
                    "movie" -> {
                        val movie = repository.getMovieDetail(id)
                        _uiState.value = PlayerUiState(
                            streamUrl = movie.streamUrl,
                            title = movie.name,
                            subtitles = movie.subtitles,
                            resumePositionMs = resumePositionMs,
                            selectedSubtitleLang = selectPreferredSubtitleLanguage(
                                movie.subtitles,
                                preferredSubtitleLang,
                                subtitlesEnabled
                            ),
                            subtitlesEnabled = subtitlesEnabled
                        )
                    }

                    "episode" -> {
                        val episode = repository.getEpisodeDetail(id)
                        val nextEpisode = try {
                            resolveNextEpisode(episode)
                        } catch (e: HttpStatusException) {
                            if (e.code == 401) throw e
                            null
                        } catch (_: Exception) {
                            null
                        }
                        val title = if (episode.showName != null && episode.season != null && episode.number != null) {
                            "${episode.showName} S%02dE%02d".format(episode.season, episode.number)
                        } else {
                            episode.name ?: "Episode"
                        }
                        _uiState.value = PlayerUiState(
                            streamUrl = episode.streamUrl,
                            title = title,
                            subtitles = episode.subtitles,
                            resumePositionMs = resumePositionMs,
                            selectedSubtitleLang = selectPreferredSubtitleLanguage(
                                episode.subtitles,
                                preferredSubtitleLang,
                                subtitlesEnabled
                            ),
                            subtitlesEnabled = subtitlesEnabled,
                            nextEpisode = nextEpisode?.let {
                                NextEpisodeUi(
                                    id = it.id,
                                    label = "S%02dE%02d".format(it.season, it.number),
                                    title = it.name
                                )
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                val httpCode = (e as? HttpStatusException)?.code
                if (httpCode == 401) {
                    sessionRepository.clearSession()
                    repository.clearCache()
                    authEventBus.send(AuthEvent.SessionExpired)
                    return@launch
                }
                val errorMsg = when (httpCode) {
                    402 -> "Subscription required for playback."
                    else -> "Failed to load stream."
                }
                _uiState.value = PlayerUiState(error = errorMsg)
            }
        }
    }

    fun onSubtitleTrackChanged(selectedLanguage: String?, isTextTrackDisabled: Boolean) {
        viewModelScope.launch {
            if (isTextTrackDisabled) {
                subtitlePreferencesRepository.setSubtitlesEnabled(false)
                _uiState.value = _uiState.value.copy(
                    subtitlesEnabled = false,
                    selectedSubtitleLang = null
                )
                return@launch
            }

            val normalized = normalizeLanguage(selectedLanguage)
            if (normalized != null) {
                subtitlePreferencesRepository.updateSelection(normalized)
                _uiState.value = _uiState.value.copy(
                    subtitlesEnabled = true,
                    selectedSubtitleLang = normalized
                )
            }
        }
    }

    fun setSubtitleSelection(selectedLanguage: String?) {
        viewModelScope.launch {
            subtitlePreferencesRepository.updateSelection(selectedLanguage)
            _uiState.value = _uiState.value.copy(
                subtitlesEnabled = selectedLanguage != null,
                selectedSubtitleLang = selectedLanguage
            )
        }
    }

    fun onPlaybackProgress(
        contentType: String,
        contentId: Int,
        positionMs: Long,
        durationMs: Long,
        isEnded: Boolean
    ) {
        viewModelScope.launch {
            val key = WatchProgressRepository.contentKey(contentType, contentId)
            watchProgressRepository.saveProgress(key, positionMs, durationMs, isEnded)
        }
    }

    fun onStopPlaybackRequested(
        contentType: String,
        contentId: Int,
        positionMs: Long,
        durationMs: Long,
        onCompleted: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val key = WatchProgressRepository.contentKey(contentType, contentId)
                if (WatchProgressRepository.isCompleted(positionMs, durationMs, isEnded = false)) {
                    watchProgressRepository.saveProgress(
                        contentKey = key,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isEnded = false
                    )
                } else {
                    watchProgressRepository.clearProgress(key)
                }
            } finally {
                onCompleted()
            }
        }
    }

    private suspend fun resolveNextEpisode(currentEpisode: EpisodeDetail): Episode? {
        val currentSeason = currentEpisode.season ?: return null
        val currentNumber = currentEpisode.number ?: return null
        val showId = currentEpisode.showId ?: findShowIdByName(currentEpisode.showName) ?: return null
        val show = repository.getShowDetail(showId)
        return findNextEpisode(show.episodes, currentSeason, currentNumber)
    }

    private suspend fun findShowIdByName(showName: String?): Int? {
        val target = normalizeShowName(showName) ?: return null
        return repository.getShows()
            .firstOrNull { normalizeShowName(it.name) == target }
            ?.id
    }
}

internal fun findNextEpisode(
    episodes: List<Episode>,
    currentSeason: Int,
    currentNumber: Int
): Episode? {
    return episodes
        .sortedWith(compareBy(Episode::season, Episode::number))
        .firstOrNull { episode ->
            episode.season > currentSeason ||
                (episode.season == currentSeason && episode.number > currentNumber)
        }
}

internal fun selectPreferredSubtitleLanguage(
    subtitles: List<Subtitle>,
    preferredLanguage: String,
    subtitlesEnabled: Boolean
): String? {
    if (!subtitlesEnabled || subtitles.isEmpty()) return null

    val normalizedPreferred = normalizeLanguage(preferredLanguage) ?: return subtitles.first().lang

    return subtitles.firstOrNull { subtitle ->
        normalizeLanguage(subtitle.lang) == normalizedPreferred
    }?.lang ?: subtitles.first().lang
}

internal fun normalizeLanguage(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return value.substringBefore('-').substringBefore('_').lowercase(Locale.US)
}

private fun normalizeShowName(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return value.lowercase(Locale.US).trim().replace("\\s+".toRegex(), " ")
}
