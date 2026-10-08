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
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.domain.model.TrendingShow
import tv.ororo.app.data.domain.model.TrendingShowsResult
import tv.ororo.app.di.ApplicationScope
import tv.ororo.app.di.DefaultDispatcher

@Singleton
class TrendingShowsRepository internal constructor(
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
        File(context.cacheDir, "tmdb_weekly_trending_shows.json"),
        ororoRepository, tmdbApi, json, tmdbRequestLimiter, defaultDispatcher,
        sessionRepository::getCacheScope, scope = scope,
        isConfigured = { BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank() }
    )

    private val loader = TrendingLoader(
        cacheFile, json, TrendingShow.serializer(), tmdbRequestLimiter, defaultDispatcher,
        scope, accountScope, now, isConfigured,
        catalog = { ororoRepository.getShows() },
        catalogFetchedAt = { ororoRepository.showsFetchedAtMs ?: now() },
        page = { number ->
            val response = tmdbApi.getTrendingShows("week", number)
            TrendingPage(response.results.map { it.id }, response.totalPages)
        },
        imdbId = { id -> tmdbApi.getTvExternalIds(id).imdbId },
        prepareMatch = { titles: List<Show> ->
            // Build the catalog index once, then reuse it for every progressive update.
            val byImdbId = titles.mapNotNull { title ->
                normalizeImdbId(title.imdbId)?.let { it to title }
            }.toMap()
            val matchEntries: (List<TrendingEntry>) -> List<TrendingShow> = { entries ->
                entries.mapNotNull { entry ->
                    byImdbId[normalizeImdbId(entry.imdbId)]?.let { TrendingShow(entry.rank, it) }
                }
            }
            matchEntries
        }
    )

    suspend fun getWeeklyTrendingShows(
        forceRefresh: Boolean = false,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
        onCachedResult: (TrendingShowsResult) -> Unit = {},
        onPartialResult: (TrendingShowsResult) -> Unit = {}
    ): TrendingShowsResult = loader.load(
        forceRefresh, onProgress,
        { onCachedResult(it.toResult()) },
        { onPartialResult(it.toResult()) }
    ).toResult()

    suspend fun clearCache() = loader.clear()

    private fun TrendingResult<TrendingShow>.toResult() =
        TrendingShowsResult(items, rankedCount, isStale)

    companion object {
        internal fun normalizeImdbId(value: String?): String? {
            return TrendingMoviesRepository.normalizeImdbId(value)
        }

        internal fun isTrendingCacheFresh(fetchedAtEpochMs: Long, nowEpochMs: Long): Boolean =
            TrendingLoader.fresh(fetchedAtEpochMs, nowEpochMs, TrendingLoader.RANKING_TTL)
    }
}

internal data class RankedImdbShow(
    val rank: Int,
    val imdbId: String?
)

internal fun matchTrendingShows(
    rankings: List<RankedImdbShow>,
    ororoShows: List<Show>
): List<TrendingShow> {
    val showsByImdbId = ororoShows.mapNotNull { show ->
        TrendingShowsRepository.normalizeImdbId(show.imdbId)
            ?.let { imdbId -> imdbId to show }
    }.toMap()
    return rankings.mapNotNull { ranking ->
        val normalizedId = TrendingShowsRepository.normalizeImdbId(ranking.imdbId)
        val show = normalizedId?.let(showsByImdbId::get) ?: return@mapNotNull null
        TrendingShow(rank = ranking.rank, show = show)
    }
}
