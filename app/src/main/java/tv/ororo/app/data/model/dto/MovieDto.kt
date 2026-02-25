package tv.ororo.app.data.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoviesResponse(
    @SerialName("movies") val movies: List<MovieDto>
)

@Serializable
data class MovieDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("poster_thumb") val posterThumb: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("array_genres") val genres: List<String> = emptyList(),
    @SerialName("desc") val desc: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class MovieDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("poster_thumb") val posterThumb: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("array_genres") val genres: List<String> = emptyList(),
    @SerialName("desc") val desc: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("subtitles") val subtitles: List<SubtitleDto> = emptyList()
)
