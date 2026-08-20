package tv.ororo.app.data.repository

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import tv.ororo.app.BuildConfig
import tv.ororo.app.data.api.TmdbApi
import tv.ororo.app.data.domain.model.Show
import tv.ororo.app.data.domain.model.TrendingShow
import tv.ororo.app.data.domain.model.TrendingShowsResult

@Singleton
class TrendingShowsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val ororoRepository: OroroRepository,
    private val tmdbApi: TmdbApi,
    private val json: Json
) {
    private val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
    private val cacheMutex = Mutex()
    private val requestMutex = Mutex()
    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private var nextRequestAtElapsedMs = 0L

    suspend fun getWeeklyTrendingShows(
        forceRefresh: Boolean = false,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): TrendingShowsResult {
        val cached = readCache()
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && isTrendingCacheFresh(cached.fetchedAtEpochMs, now)) {
            onProgress(cached.entries.size, cached.entries.size)
            return cached.toResult(ororoRepository.getShows(), isStale = false)
        }

        return try {
            if (BuildConfig.TMDB_READ_ACCESS_TOKEN.isBlank()) {
                throw TmdbConfigurationException()
            }
            val refreshed = refreshCache(onProgress)
            refreshed.toResult(ororoRepository.getShows(), isStale = false)
        } catch (error: Exception) {
            if (cached != null) {
                onProgress(cached.entries.size, cached.entries.size)
                cached.toResult(ororoRepository.getShows(), isStale = true)
            } else {
                throw error
            }
        }
    }

    suspend fun clearCache() {
        cacheMutex.lock()
        try {
            withContext(Dispatchers.IO) {
                cacheFile.delete()
            }
        } finally {
            cacheMutex.unlock()
        }
    }

    private suspend fun refreshCache(
        onProgress: (completed: Int, total: Int) -> Unit
    ): TrendingShowsCachePayload {
        val tmdbIds = loadTopTrendingShowIds()
        if (tmdbIds.isEmpty()) {
            throw IOException("TMDB returned no trending TV shows")
        }

        onProgress(0, tmdbIds.size)
        val completedCount = AtomicInteger(0)
        val entries = coroutineScope {
            tmdbIds.mapIndexed { index, tmdbId ->
                async {
                    val resolution = resolveImdbId(tmdbId)
                    onProgress(completedCount.incrementAndGet(), tmdbIds.size)
                    TrendingShowsCacheEntry(
                        rank = index + 1,
                        tmdbId = tmdbId,
                        imdbId = resolution.imdbId,
                        lookupCompleted = resolution.completed
                    )
                }
            }.awaitAll()
        }

        if (entries.none { it.lookupCompleted && it.imdbId != null }) {
            throw IOException("TMDB TV show matching failed")
        }

        val payload = TrendingShowsCachePayload(
            fetchedAtEpochMs = System.currentTimeMillis(),
            entries = entries
        )
        writeCache(payload)
        return payload
    }

    private suspend fun loadTopTrendingShowIds(): List<Int> {
        val ids = linkedSetOf<Int>()
        for (page in 1..TRENDING_PAGE_COUNT) {
            val response = executeWithRetry {
                tmdbApi.getTrendingShows(
                    timeWindow = TRENDING_TIME_WINDOW,
                    page = page
                )
            }
            response.results.forEach { result ->
                if (ids.size < TRENDING_SHOW_LIMIT) {
                    ids += result.id
                }
            }
            if (
                ids.size >= TRENDING_SHOW_LIMIT ||
                response.results.isEmpty() ||
                (response.totalPages > 0 && page >= response.totalPages)
            ) {
                break
            }
        }
        return ids.take(TRENDING_SHOW_LIMIT)
    }

    private suspend fun resolveImdbId(tmdbId: Int): ShowIdResolution {
        return try {
            val externalIds = executeWithRetry { tmdbApi.getTvExternalIds(tmdbId) }
            ShowIdResolution(
                imdbId = normalizeImdbId(externalIds.imdbId),
                completed = true
            )
        } catch (error: HttpException) {
            when (error.code()) {
                401, 403 -> throw error
                404 -> ShowIdResolution(imdbId = null, completed = true)
                else -> ShowIdResolution(imdbId = null, completed = false)
            }
        } catch (_: IOException) {
            ShowIdResolution(imdbId = null, completed = false)
        }
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
            try {
                requestSemaphore.acquire()
                try {
                    awaitRequestSlot()
                    return block()
                } finally {
                    requestSemaphore.release()
                }
            } catch (error: HttpException) {
                if (error.code() == 401 || error.code() == 403 || error.code() == 404) {
                    throw error
                }
                if (error.code() != 429 && error.code() < 500) {
                    throw error
                }
                lastError = error
                if (attempt < MAX_REQUEST_ATTEMPTS - 1) {
                    delay(retryDelayMs(error, attempt))
                }
            } catch (error: IOException) {
                lastError = error
                if (attempt < MAX_REQUEST_ATTEMPTS - 1) {
                    delay(DEFAULT_RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastError ?: IOException("TMDB request failed")
    }

    private suspend fun awaitRequestSlot() {
        requestMutex.lock()
        try {
            val now = SystemClock.elapsedRealtime()
            val delayMs = (nextRequestAtElapsedMs - now).coerceAtLeast(0L)
            if (delayMs > 0) {
                delay(delayMs)
            }
            nextRequestAtElapsedMs = SystemClock.elapsedRealtime() + REQUEST_INTERVAL_MS
        } finally {
            requestMutex.unlock()
        }
    }

    private fun retryDelayMs(error: HttpException, attempt: Int): Long {
        val retryAfterSeconds = error.response()
            ?.headers()
            ?.get("Retry-After")
            ?.toLongOrNull()
        return retryAfterSeconds?.times(1_000L)
            ?: DEFAULT_RETRY_DELAY_MS * (attempt + 1)
    }

    private suspend fun readCache(): TrendingShowsCachePayload? {
        cacheMutex.lock()
        return try {
            withContext(Dispatchers.IO) {
                if (!cacheFile.exists()) return@withContext null
                runCatching {
                    json.decodeFromString<TrendingShowsCachePayload>(cacheFile.readText())
                }.getOrNull()
            }
        } finally {
            cacheMutex.unlock()
        }
    }

    private suspend fun writeCache(payload: TrendingShowsCachePayload) {
        cacheMutex.lock()
        try {
            withContext(Dispatchers.IO) {
                cacheFile.parentFile?.mkdirs()
                val encoded = json.encodeToString(TrendingShowsCachePayload.serializer(), payload)
                val temporaryFile = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
                temporaryFile.writeText(encoded)
                if (!temporaryFile.renameTo(cacheFile)) {
                    cacheFile.writeText(encoded)
                    temporaryFile.delete()
                }
            }
        } finally {
            cacheMutex.unlock()
        }
    }

    private fun TrendingShowsCachePayload.toResult(
        ororoShows: List<Show>,
        isStale: Boolean
    ): TrendingShowsResult {
        val matchedShows = matchTrendingShows(
            rankings = entries.map { entry ->
                RankedImdbShow(rank = entry.rank, imdbId = entry.imdbId)
            },
            ororoShows = ororoShows
        )
        return TrendingShowsResult(
            shows = matchedShows,
            rankedShowCount = entries.size,
            isStale = isStale
        )
    }

    private data class ShowIdResolution(
        val imdbId: String?,
        val completed: Boolean
    )

    companion object {
        private const val CACHE_FILE_NAME = "tmdb_weekly_trending_shows.json"
        private const val TRENDING_TIME_WINDOW = "week"
        private const val TRENDING_PAGE_COUNT = 5
        private const val TRENDING_SHOW_LIMIT = 100
        private const val REQUEST_INTERVAL_MS = 100L
        private const val MAX_CONCURRENT_REQUESTS = 4
        private const val DEFAULT_RETRY_DELAY_MS = 1_000L
        private const val MAX_REQUEST_ATTEMPTS = 3
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1_000L

        internal fun normalizeImdbId(value: String?): String? =
            TrendingMoviesRepository.normalizeImdbId(value)

        internal fun isTrendingCacheFresh(fetchedAtEpochMs: Long, nowEpochMs: Long): Boolean {
            val ageMs = nowEpochMs - fetchedAtEpochMs
            return ageMs in 0..CACHE_TTL_MS
        }
    }
}

@Serializable
private data class TrendingShowsCachePayload(
    @SerialName("fetched_at_epoch_ms") val fetchedAtEpochMs: Long,
    @SerialName("entries") val entries: List<TrendingShowsCacheEntry>
)

@Serializable
private data class TrendingShowsCacheEntry(
    @SerialName("rank") val rank: Int,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("lookup_completed") val lookupCompleted: Boolean = true
)

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
