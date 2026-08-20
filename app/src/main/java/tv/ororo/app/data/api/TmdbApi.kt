package tv.ororo.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import tv.ororo.app.data.model.dto.TmdbMovieDetailsDto
import tv.ororo.app.data.model.dto.TmdbTrendingMoviesResponse
import tv.ororo.app.data.model.dto.TmdbTrendingShowsResponse
import tv.ororo.app.data.model.dto.TmdbTvExternalIdsDto

interface TmdbApi {

    @GET("trending/movie/{timeWindow}")
    suspend fun getTrendingMovies(
        @Path("timeWindow") timeWindow: String,
        @Query("page") page: Int,
        @Query("language") language: String = "en-US"
    ): TmdbTrendingMoviesResponse

    @GET("movie/{movieId}")
    suspend fun getMovieDetails(
        @Path("movieId") movieId: Int,
        @Query("language") language: String = "en-US"
    ): TmdbMovieDetailsDto

    @GET("trending/tv/{timeWindow}")
    suspend fun getTrendingShows(
        @Path("timeWindow") timeWindow: String,
        @Query("page") page: Int,
        @Query("language") language: String = "en-US"
    ): TmdbTrendingShowsResponse

    @GET("tv/{seriesId}/external_ids")
    suspend fun getTvExternalIds(
        @Path("seriesId") seriesId: Int
    ): TmdbTvExternalIdsDto
}
