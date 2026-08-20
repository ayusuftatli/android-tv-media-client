package tv.ororo.app.data.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbTrendingMoviesResponse(
    @SerialName("page") val page: Int,
    @SerialName("results") val results: List<TmdbTrendingMovieDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0
)

@Serializable
data class TmdbTrendingMovieDto(
    @SerialName("id") val id: Int
)

@Serializable
data class TmdbMovieDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("imdb_id") val imdbId: String? = null
)

@Serializable
data class TmdbTrendingShowsResponse(
    @SerialName("page") val page: Int,
    @SerialName("results") val results: List<TmdbTrendingShowDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0
)

@Serializable
data class TmdbTrendingShowDto(
    @SerialName("id") val id: Int
)

@Serializable
data class TmdbTvExternalIdsDto(
    @SerialName("id") val id: Int,
    @SerialName("imdb_id") val imdbId: String? = null
)
