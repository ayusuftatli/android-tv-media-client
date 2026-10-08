package tv.ororo.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tv.ororo.app.data.domain.model.EpisodeDetail
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SessionRepository
import tv.ororo.app.data.repository.TrendingMoviesRepository
import tv.ororo.app.data.repository.TrendingShowsRepository
import tv.ororo.app.data.repository.WatchProgressRepository
import tv.ororo.app.di.DefaultDispatcher

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
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val ororoRepository: OroroRepository,
    private val trendingMoviesRepository: TrendingMoviesRepository,
    private val trendingShowsRepository: TrendingShowsRepository,
    private val watchProgressRepository: WatchProgressRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val episodeMetadataCache = ConcurrentHashMap<Int, EpisodeDetail>()
    private var indexedMovies: List<Movie>? = null
    private var moviesById: Map<Int, Movie> = emptyMap()
    private var indexedShows: List<Show>? = null
    private var showsByName: Map<String, Show> = emptyMap()
    private val episodeRequests = Semaphore(4)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = watchProgressRepository.inProgressWatchStatesFlow()
        .mapLatest { states ->
            HomeUiState(
                continueWatching = if (states.isEmpty()) emptyList() else buildContinueWatchingItems(states),
                isLoadingContinueWatching = false
            )
        }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    suspend fun logout() {
        sessionRepository.clearSession()
        ororoRepository.clearCache()
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            watchProgressRepository.clearAllProgress()
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch {
            ororoRepository.clearCache()
            trendingMoviesRepository.clearCache()
            trendingShowsRepository.clearCache()
            episodeMetadataCache.clear()
            context.imageLoader.memoryCache?.clear()
            withContext(Dispatchers.IO) {
                context.imageLoader.diskCache?.clear()
            }
        }
    }

    private suspend fun buildContinueWatchingItems(
        inProgressStates: List<tv.ororo.app.data.repository.WatchState>
    ): List<ContinueWatchingItem> {
        val visibleStates = inProgressStates.take(WatchProgressRepository.MAX_CONTINUE_WATCHING_ITEMS)
        coroutineScope {
            val movies = async {
                if (visibleStates.none { it.contentKey.startsWith("movie:") }) return@async
                val catalog = metadataOrNull { ororoRepository.getMovies() } ?: return@async
                if (catalog !== indexedMovies) {
                    moviesById = catalog.associateBy(Movie::id)
                    indexedMovies = catalog
                }
            }
            val shows = async {
                if (visibleStates.none { it.contentKey.startsWith("episode:") }) return@async
                val catalog = metadataOrNull { ororoRepository.getShows() } ?: return@async
                if (catalog !== indexedShows) {
                    showsByName = catalog.associateBy { normalizeShowName(it.name) }
                    indexedShows = catalog
                }
            }
            movies.await()
            shows.await()
        }

        return coroutineScope {
            visibleStates.map { watchState ->
                async {
                    val (contentType, contentId) = WatchProgressRepository.parseContentKey(watchState.contentKey)
                        ?: return@async null
                    val progressPercent = calculateProgressPercent(
                        positionMs = watchState.positionMs,
                        durationMs = watchState.durationMs
                    )
                    when (contentType.lowercase()) {
                        "movie" -> {
                            val movie = moviesById[contentId] ?: return@async null
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
                                ?: episodeRequests.withPermit {
                                    metadataOrNull { ororoRepository.getEpisodeDetail(contentId) }
                                }
                                    ?.also { episodeMetadataCache[contentId] = it }
                                ?: return@async null
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
                }
            }.awaitAll().filterNotNull().sortedByDescending { it.updatedAt }
        }
    }

    private suspend fun <T> metadataOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
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

}

internal fun calculateProgressPercent(positionMs: Long, durationMs: Long): Int {
    if (positionMs <= 0L || durationMs <= 0L) return 0
    val progress = (positionMs.toDouble() / durationMs.toDouble()) * 100.0
    return progress.toInt().coerceIn(1, 99)
}
