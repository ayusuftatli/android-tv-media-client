package tv.ororo.app.data.domain.model

data class Movie(
    val id: Int,
    val name: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val imdbRating: Double?,
    val genres: List<String>,
    val description: String?,
    val updatedAt: String?
)

data class MovieDetail(
    val id: Int,
    val name: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val imdbRating: Double?,
    val genres: List<String>,
    val description: String?,
    val streamUrl: String?,
    val subtitles: List<Subtitle>
)

data class Show(
    val id: Int,
    val name: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val imdbRating: Double?,
    val genres: List<String>,
    val description: String?,
    val ended: Boolean?,
    val seasonCount: Int?,
    val newestVideo: String?,
    val userPopularity: Double?
)

data class ShowDetail(
    val id: Int,
    val name: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val imdbRating: Double?,
    val genres: List<String>,
    val description: String?,
    val ended: Boolean?,
    val seasonCount: Int?,
    val episodes: List<Episode>
)

data class Episode(
    val id: Int,
    val name: String?,
    val season: Int,
    val number: Int,
    val airdate: String?,
    val plot: String?,
    val resolution: String?
)

data class EpisodeDetail(
    val id: Int,
    val name: String?,
    val showId: Int?,
    val showName: String?,
    val season: Int?,
    val number: Int?,
    val streamUrl: String?,
    val subtitles: List<Subtitle>
)

data class Subtitle(
    val lang: String,
    val url: String
)
