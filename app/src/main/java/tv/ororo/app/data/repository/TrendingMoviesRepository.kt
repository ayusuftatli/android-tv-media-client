package tv.ororo.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import tv.ororo.app.BuildConfig
import tv.ororo.app.data.api.TmdbApi
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.domain.model.TrendingMovie
import tv.ororo.app.data.domain.model.TrendingMoviesResult
import tv.ororo.app.di.ApplicationScope
import tv.ororo.app.di.DefaultDispatcher

class TmdbConfigurationException : IllegalStateException("TMDB_READ_ACCESS_TOKEN is not configured")

@Singleton
class TrendingMoviesRepository internal constructor(
    cacheFile: File,
    ororoRepository: OroroRepository,
    tmdbApi: TmdbApi,
    json: Json,
    tmdbRequestLimiter: TmdbRequestLimiter,
    defaultDispatcher: CoroutineDispatcher,
    accountScope: suspend () -> String? = { "local" },
    now: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher),
    isConfigured: () -> Boolean
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        ororoRepository: OroroRepository,
        tmdbApi: TmdbApi,
        json: Json,
        tmdbRequestLimiter: TmdbRequestLimiter,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        sessionRepository: SessionRepository,
        @ApplicationScope scope: CoroutineScope
    ) : this(
        File(context.cacheDir, "tmdb_weekly_trending_movies.json"),
        ororoRepository, tmdbApi, json, tmdbRequestLimiter, defaultDispatcher,
        sessionRepository::getCacheScope, scope = scope,
        isConfigured = { BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank() }
    )

    private val loader = TrendingLoader(
        cacheFile, json, TrendingMovie.serializer(), tmdbRequestLimiter, defaultDispatcher,
        scope, accountScope, now, isConfigured,
        catalog = { ororoRepository.getMovies() },
        catalogFetchedAt = { ororoRepository.moviesFetchedAtMs ?: now() },
        page = { number ->
            val response = tmdbApi.getTrendingMovies("week", number)
            TrendingPage(response.results.map { it.id }, response.totalPages)
        },
        imdbId = { id -> tmdbApi.getMovieDetails(id).imdbId },
        prepareMatch = { titles: List<Movie> ->
            // Build the catalog index once, then reuse it for every progressive update.
            val byImdbId = titles.mapNotNull { title ->
                normalizeImdbId(title.imdbId)?.let { it to title }
            }.toMap()
            val matchEntries: (List<TrendingEntry>) -> List<TrendingMovie> = { entries ->
                entries.mapNotNull { entry ->
                    byImdbId[normalizeImdbId(entry.imdbId)]?.let { TrendingMovie(entry.rank, it) }
                }
            }
            matchEntries
        }
    )

    suspend fun getWeeklyTrendingMovies(
        forceRefresh: Boolean = false,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        onCachedResult: (TrendingMoviesResult) -> Unit = {},
        onPartialResult: (TrendingMoviesResult) -> Unit = {}
    ): TrendingMoviesResult = loader.load(
        forceRefresh, onProgress,
        { onCachedResult(it.toResult()) },
        { onPartialResult(it.toResult()) }
    ).toResult()

    suspend fun clearCache() = loader.clear()

    private fun TrendingResult<TrendingMovie>.toResult() =
        TrendingMoviesResult(items, rankedCount, isStale)

    companion object {
        private val imdbIdPattern = Regex("tt\\d+")
        private val numericIdPattern = Regex("\\d+")

        internal fun normalizeImdbId(value: String?): String? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return when {
                normalized.matches(imdbIdPattern) -> normalized
                normalized.matches(numericIdPattern) -> "tt$normalized"
                else -> null
            }
        }

        internal fun isTrendingCacheFresh(fetchedAtEpochMs: Long, nowEpochMs: Long): Boolean =
            TrendingLoader.fresh(fetchedAtEpochMs, nowEpochMs, TrendingLoader.RANKING_TTL)
    }
}

internal data class RankedImdbMovie(
    val rank: Int,
    val imdbId: String?
)

internal fun matchTrendingMovies(
    rankings: List<RankedImdbMovie>,
    ororoMovies: List<Movie>
): List<TrendingMovie> {
    val moviesByImdbId = ororoMovies.mapNotNull { movie ->
        TrendingMoviesRepository.normalizeImdbId(movie.imdbId)
            ?.let { imdbId -> imdbId to movie }
    }.toMap()
    return rankings.mapNotNull { ranking ->
        val normalizedId = TrendingMoviesRepository.normalizeImdbId(ranking.imdbId)
        val movie = normalizedId?.let(moviesByImdbId::get) ?: return@mapNotNull null
        TrendingMovie(rank = ranking.rank, movie = movie)
    }
}
