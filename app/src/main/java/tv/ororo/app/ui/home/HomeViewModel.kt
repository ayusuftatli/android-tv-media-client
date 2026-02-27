package tv.ororo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tv.ororo.app.data.domain.model.EpisodeDetail
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SessionRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import javax.inject.Inject

data class ContinueWatchingItem(
    val contentType: String,
    val contentId: Int,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    val progressPercent: Int,
    val updatedAt: Long
)

data class HomeUiState(
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val isLoadingContinueWatching: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val ororoRepository: OroroRepository,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val episodeMetadataCache = mutableMapOf<Int, EpisodeDetail>()

    init {
        observeContinueWatching()
    }

    suspend fun logout() {
        sessionRepository.clearSession()
        ororoRepository.clearCache()
    }

    fun clearCache() {
        ororoRepository.clearCache()
        episodeMetadataCache.clear()
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            watchProgressRepository.inProgressWatchStatesFlow().collectLatest { inProgressStates ->
                if (inProgressStates.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        continueWatching = emptyList(),
                        isLoadingContinueWatching = false
                    )
                    return@collectLatest
                }

                _uiState.value = _uiState.value.copy(isLoadingContinueWatching = true)
                val continueWatchingItems = buildContinueWatchingItems(inProgressStates)
                _uiState.value = _uiState.value.copy(
                    continueWatching = continueWatchingItems,
                    isLoadingContinueWatching = false
                )
            }
        }
    }

    private suspend fun buildContinueWatchingItems(
        inProgressStates: List<tv.ororo.app.data.repository.WatchState>
    ): List<ContinueWatchingItem> {
        val moviesById = runCatching {
            ororoRepository.getMovies().associateBy(Movie::id)
        }.getOrDefault(emptyMap())
        val showsByName = runCatching {
            ororoRepository.getShows().associateBy { normalizeShowName(it.name) }
        }.getOrDefault(emptyMap())

        return inProgressStates.take(MAX_CONTINUE_WATCHING_ITEMS).mapNotNull { watchState ->
            val (contentType, contentId) = WatchProgressRepository.parseContentKey(watchState.contentKey)
                ?: return@mapNotNull null
            val progressPercent = calculateProgressPercent(
                positionMs = watchState.positionMs,
                durationMs = watchState.durationMs
            )
            when (contentType.lowercase()) {
                "movie" -> {
                    val movie = moviesById[contentId] ?: return@mapNotNull null
                    ContinueWatchingItem(
                        contentType = "movie",
                        contentId = movie.id,
                        title = movie.name,
                        subtitle = null,
                        posterUrl = movie.posterUrl,
                        year = movie.year,
                        rating = movie.imdbRating,
                        progressPercent = progressPercent,
                        updatedAt = watchState.updatedAt
                    )
                }

                "episode" -> {
                    val episodeDetail = episodeMetadataCache[contentId]
                        ?: runCatching { ororoRepository.getEpisodeDetail(contentId) }
                            .getOrNull()
                            ?.also { episodeMetadataCache[contentId] = it }
                        ?: return@mapNotNull null
                    val normalizedShowName = normalizeShowName(episodeDetail.showName.orEmpty())
                    val show = showsByName[normalizedShowName]
                    ContinueWatchingItem(
                        contentType = "episode",
                        contentId = contentId,
                        title = episodeDetail.showName ?: (episodeDetail.name ?: "Episode"),
                        subtitle = formatEpisodeSubtitle(
                            season = episodeDetail.season,
                            number = episodeDetail.number,
                            episodeName = episodeDetail.name
                        ),
                        posterUrl = show?.posterUrl,
                        year = show?.year,
                        rating = show?.imdbRating,
                        progressPercent = progressPercent,
                        updatedAt = watchState.updatedAt
                    )
                }

                else -> null
            }
        }.sortedByDescending { it.updatedAt }
    }

    private fun normalizeShowName(name: String): String {
        return name.trim().lowercase()
    }

    private fun formatEpisodeSubtitle(season: Int?, number: Int?, episodeName: String?): String? {
        if (season == null || number == null) return episodeName
        val episodeLabel = "S%02dE%02d".format(season, number)
        if (episodeName.isNullOrBlank()) return episodeLabel
        return "$episodeLabel • $episodeName"
    }

    private fun calculateProgressPercent(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        val progress = (positionMs.toDouble() / durationMs.toDouble()) * 100.0
        return progress.toInt().coerceIn(1, 99)
    }

    companion object {
        private const val MAX_CONTINUE_WATCHING_ITEMS = 20
    }
}
