package tv.ororo.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import tv.ororo.app.data.model.dto.*

interface OroroApi {

    @GET("movies")
    suspend fun getMovies(): MoviesResponse

    @GET("movies/{id}")
    suspend fun getMovie(@Path("id") id: Int): MovieDetailDto

    @GET("shows")
    suspend fun getShows(): ShowsResponse

    @GET("shows/{id}")
    suspend fun getShow(@Path("id") id: Int): ShowDto

    @GET("episodes/{id}")
    suspend fun getEpisode(@Path("id") id: Int): EpisodeDetailDto
}
