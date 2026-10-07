package tv.ororo.app.data.repository

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.ororo.app.data.api.OroroApi
import tv.ororo.app.data.domain.model.*
import tv.ororo.app.data.model.mapper.*
import tv.ororo.app.di.DefaultDispatcher

@Singleton
class OroroRepository @Inject constructor(
    private val api: OroroApi,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    private data class CatalogCache<T>(val items: List<T>, val fetchedAtMs: Long)

    @Volatile private var moviesCache: CatalogCache<Movie>? = null
    @Volatile private var showsCache: CatalogCache<Show>? = null
    private val moviesMutex = Mutex()
    private val showsMutex = Mutex()
    private val cacheGeneration = AtomicLong()
    private val cacheLock = Any()

    suspend fun getMovies(forceRefresh: Boolean = false): List<Movie> = moviesMutex.withLock {
        val cached = moviesCache
        if (!forceRefresh && cached != null && isCacheFresh(cached.fetchedAtMs)) {
            return@withLock cached.items
        }
        val generation = cacheGeneration.get()
        val response = api.getMovies()
        val movies = withContext(defaultDispatcher) { response.movies.map { it.toDomain() } }
        synchronized(cacheLock) {
            if (generation == cacheGeneration.get()) {
                moviesCache = CatalogCache(movies, System.currentTimeMillis())
            }
        }
        movies
    }

    suspend fun getMovieDetail(id: Int): MovieDetail {
        return api.getMovie(id).toDomain()
    }

    suspend fun getShows(forceRefresh: Boolean = false): List<Show> = showsMutex.withLock {
        val cached = showsCache
        if (!forceRefresh && cached != null && isCacheFresh(cached.fetchedAtMs)) {
            return@withLock cached.items
        }
        val generation = cacheGeneration.get()
        val response = api.getShows()
        val shows = withContext(defaultDispatcher) { response.shows.map { it.toDomain() } }
        synchronized(cacheLock) {
            if (generation == cacheGeneration.get()) {
                showsCache = CatalogCache(shows, System.currentTimeMillis())
            }
        }
        shows
    }

    suspend fun getShowDetail(id: Int): ShowDetail {
        val response = api.getShow(id)
        return withContext(defaultDispatcher) { response.toShowDetail() }
    }

    suspend fun getEpisodeDetail(id: Int): EpisodeDetail {
        return api.getEpisode(id).toDomain()
    }

    fun clearCache() = synchronized(cacheLock) {
        cacheGeneration.incrementAndGet()
        moviesCache = null
        showsCache = null
    }

    private fun isCacheFresh(cachedAtMs: Long?): Boolean {
        if (cachedAtMs == null) return false
        val ageMs = System.currentTimeMillis() - cachedAtMs
        return ageMs in 0..CACHE_TTL_MS
    }

    companion object {
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
    }
}
