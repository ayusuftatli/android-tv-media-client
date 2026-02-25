package tv.ororo.app.data.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShowsResponse(
    @SerialName("shows") val shows: List<ShowDto>
)

@Serializable
data class ShowDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("poster_thumb") val posterThumb: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    @SerialName("tmdb_id") val tmdbId: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("array_genres") val genres: List<String> = emptyList(),
    @SerialName("array_countries") val countries: List<String> = emptyList(),
    @SerialName("desc") val desc: String? = null,
    @SerialName("ended") val ended: Boolean? = null,
    @SerialName("length") val length: Int? = null,
    @SerialName("newest_video") val newestVideo: String? = null,
    @SerialName("user_popularity") val userPopularity: Double? = null,
    @SerialName("seasons") val seasons: Int? = null,
    @SerialName("episodes") val episodes: List<EpisodeDto>? = null
)

@Serializable
data class EpisodeDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("season") val season: Int,
    @SerialName("number") val number: Int,
    @SerialName("airdate") val airdate: String? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("resolution") val resolution: String? = null
)

@Serializable
data class EpisodeDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("show_name") val showName: String? = null,
    @SerialName("season") val season: Int? = null,
    @SerialName("number") val number: Int? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("subtitles") val subtitles: List<SubtitleDto> = emptyList()
)

@Serializable
data class SubtitleDto(
    @SerialName("lang") val lang: String,
    @SerialName("url") val url: String
)
