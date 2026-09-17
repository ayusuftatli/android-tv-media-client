package tv.ororo.app.data.repository

import android.content.Context
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import tv.ororo.app.BuildConfig
import tv.ororo.app.data.api.TmdbApi
import tv.ororo.app.data.domain.model.Movie
import tv.ororo.app.data.domain.model.TrendingMovie
import tv.ororo.app.data.domain.model.TrendingMoviesResult

class TmdbConfigurationException : IllegalStateException(
    "TMDB_READ_ACCESS_TOKEN is not configured"
)

@Singleton
class TrendingMoviesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val ororoRepository: OroroRepository,
    private val tmdbApi: TmdbApi,
    private val json: Json,
    private val tmdbRequestLimiter: TmdbRequestLimiter
) {
    private val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
    private val cacheMutex = Mutex()

    suspend fun getWeeklyTrendingMovies(
        forceRefresh: Boolean = false,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): TrendingMoviesResult {
        val cached = readCache()
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && isTrendingCacheFresh(cached.fetchedAtEpochMs, now)) {
            onProgress(cached.entries.size, cached.entries.size)
            return cached.toResult(ororoRepository.getMovies(), isStale = false)
        }

        return try {
            if (BuildConfig.TMDB_READ_ACCESS_TOKEN.isBlank()) {
                throw TmdbConfigurationException()
            }
            val refreshed = refreshCache(onProgress)
            refreshed.toResult(ororoRepository.getMovies(), isStale = false)
        } catch (error: Exception) {
            if (cached != null) {
                onProgress(cached.entries.size, cached.entries.size)
                cached.toResult(ororoRepository.getMovies(), isStale = true)
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
    ): TrendingCachePayload {
        val tmdbIds = loadTopTrendingMovieIds()
        if (tmdbIds.isEmpty()) {
            throw IOException("TMDB returned no trending movies")
        }

        onProgress(0, tmdbIds.size)
        val completedCount = AtomicInteger(0)
        val entries = coroutineScope {
            tmdbIds.mapIndexed { index, tmdbId ->
                async {
                    val resolution = resolveImdbId(tmdbId)
                    onProgress(completedCount.incrementAndGet(), tmdbIds.size)
                    TrendingCacheEntry(
                        rank = index + 1,
                        tmdbId = tmdbId,
                        imdbId = resolution.imdbId,
                        lookupCompleted = resolution.completed
                    )
                }
            }.awaitAll()
        }

        if (entries.none { it.lookupCompleted && it.imdbId != null }) {
            throw IOException("TMDB movie matching failed")
        }

        val payload = TrendingCachePayload(
            fetchedAtEpochMs = System.currentTimeMillis(),
            entries = entries
        )
        writeCache(payload)
        return payload
    }

    private suspend fun loadTopTrendingMovieIds(): List<Int> {
        val ids = linkedSetOf<Int>()
        for (page in 1..TRENDING_PAGE_COUNT) {
            val response = executeWithRetry {
                tmdbApi.getTrendingMovies(
                    timeWindow = TRENDING_TIME_WINDOW,
                    page = page
                )
            }
            response.results.forEach { result ->
                if (ids.size < TRENDING_MOVIE_LIMIT) {
                    ids += result.id
                }
            }
            if (
                ids.size >= TRENDING_MOVIE_LIMIT ||
                response.results.isEmpty() ||
                (response.totalPages > 0 && page >= response.totalPages)
            ) {
                break
            }
        }
        return ids.take(TRENDING_MOVIE_LIMIT)
    }

    private suspend fun resolveImdbId(tmdbId: Int): MovieIdResolution {
        return try {
            val details = executeWithRetry { tmdbApi.getMovieDetails(tmdbId) }
            MovieIdResolution(
                imdbId = normalizeImdbId(details.imdbId),
                completed = true
            )
        } catch (error: HttpException) {
            when (error.code()) {
                401, 403 -> throw error
                404 -> MovieIdResolution(imdbId = null, completed = true)
                else -> MovieIdResolution(imdbId = null, completed = false)
            }
        } catch (_: IOException) {
            MovieIdResolution(imdbId = null, completed = false)
        }
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
            try {
                return tmdbRequestLimiter.execute(block)
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

    private fun retryDelayMs(error: HttpException, attempt: Int): Long {
        val retryAfterSeconds = error.response()
            ?.headers()
            ?.get("Retry-After")
            ?.toLongOrNull()
        return retryAfterSeconds?.times(1_000L)
            ?: DEFAULT_RETRY_DELAY_MS * (attempt + 1)
    }

    private suspend fun readCache(): TrendingCachePayload? {
        cacheMutex.lock()
        return try {
            withContext(Dispatchers.IO) {
                if (!cacheFile.exists()) return@withContext null
                runCatching {
                    json.decodeFromString<TrendingCachePayload>(cacheFile.readText())
                }.getOrNull()
            }
        } finally {
            cacheMutex.unlock()
        }
    }

    private suspend fun writeCache(payload: TrendingCachePayload) {
        cacheMutex.lock()
        try {
            withContext(Dispatchers.IO) {
                cacheFile.parentFile?.mkdirs()
                val encoded = json.encodeToString(TrendingCachePayload.serializer(), payload)
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

    private fun TrendingCachePayload.toResult(
        ororoMovies: List<Movie>,
        isStale: Boolean
    ): TrendingMoviesResult {
        val matchedMovies = matchTrendingMovies(
            rankings = entries.map { entry ->
                RankedImdbMovie(rank = entry.rank, imdbId = entry.imdbId)
            },
            ororoMovies = ororoMovies
        )
        return TrendingMoviesResult(
            movies = matchedMovies,
            rankedMovieCount = entries.size,
            isStale = isStale
        )
    }

    private data class MovieIdResolution(
        val imdbId: String?,
        val completed: Boolean
    )

    companion object {
        private const val CACHE_FILE_NAME = "tmdb_weekly_trending_movies.json"
        private const val TRENDING_TIME_WINDOW = "week"
        private const val TRENDING_PAGE_COUNT = 5
        private const val TRENDING_MOVIE_LIMIT = 100
        private const val DEFAULT_RETRY_DELAY_MS = 1_000L
        private const val MAX_REQUEST_ATTEMPTS = 3
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1_000L

        internal fun normalizeImdbId(value: String?): String? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty()) return null
            return when {
                normalized.matches(Regex("tt\\d+")) -> normalized
                normalized.matches(Regex("\\d+")) -> "tt$normalized"
                else -> null
            }
        }

        internal fun isTrendingCacheFresh(fetchedAtEpochMs: Long, nowEpochMs: Long): Boolean {
            val ageMs = nowEpochMs - fetchedAtEpochMs
            return ageMs in 0..CACHE_TTL_MS
        }
    }
}

@Serializable
private data class TrendingCachePayload(
    @SerialName("fetched_at_epoch_ms") val fetchedAtEpochMs: Long,
    @SerialName("entries") val entries: List<TrendingCacheEntry>
)

@Serializable
private data class TrendingCacheEntry(
    @SerialName("rank") val rank: Int,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("lookup_completed") val lookupCompleted: Boolean = true
)

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
