package tv.ororo.app.data.repository

import tv.ororo.app.data.api.OroroApi
import tv.ororo.app.data.domain.model.*
import tv.ororo.app.data.model.mapper.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OroroRepository @Inject constructor(
    private val api: OroroApi
) {
    private var moviesCache: List<Movie>? = null
    private var showsCache: List<Show>? = null

    suspend fun getMovies(forceRefresh: Boolean = false): List<Movie> {
        if (!forceRefresh && moviesCache != null) return moviesCache!!
        val movies = api.getMovies().movies.map { it.toDomain() }
        moviesCache = movies
        return movies
    }

    suspend fun getMovieDetail(id: Int): MovieDetail {
        return api.getMovie(id).toDomain()
    }

    suspend fun getShows(forceRefresh: Boolean = false): List<Show> {
        if (!forceRefresh && showsCache != null) return showsCache!!
        val shows = api.getShows().shows.map { it.toDomain() }
        showsCache = shows
        return shows
    }

    suspend fun getShowDetail(id: Int): ShowDetail {
        return api.getShow(id).toShowDetail()
    }

    suspend fun getEpisodeDetail(id: Int): EpisodeDetail {
        return api.getEpisode(id).toDomain()
    }

    fun clearCache() {
        moviesCache = null
        showsCache = null
    }
}
