package tv.ororo.app.data.model.mapper

import tv.ororo.app.data.domain.model.*
import tv.ororo.app.data.model.dto.*

fun MovieDto.toDomain() = Movie(
    id = id,
    name = name,
    year = year,
    posterUrl = posterThumb,
    backdropUrl = backdropUrl,
    imdbRating = imdbRating,
    genres = genres,
    description = desc,
    updatedAt = updatedAt
)

fun MovieDetailDto.toDomain() = MovieDetail(
    id = id,
    name = name,
    year = year,
    posterUrl = posterThumb,
    backdropUrl = backdropUrl,
    imdbRating = imdbRating,
    genres = genres,
    description = desc,
    streamUrl = url,
    subtitles = subtitles.map { it.toDomain() }
)

fun ShowDto.toDomain() = Show(
    id = id,
    name = name,
    year = year,
    posterUrl = posterThumb,
    backdropUrl = backdropUrl,
    imdbRating = imdbRating,
    genres = genres,
    description = desc,
    ended = ended,
    seasonCount = seasons,
    newestVideo = newestVideo,
    userPopularity = userPopularity
)

fun ShowDto.toShowDetail() = ShowDetail(
    id = id,
    name = name,
    year = year,
    posterUrl = posterThumb,
    backdropUrl = backdropUrl,
    imdbRating = imdbRating,
    genres = genres,
    description = desc,
    ended = ended,
    seasonCount = seasons,
    episodes = episodes?.map { it.toDomain() } ?: emptyList()
)

fun EpisodeDto.toDomain() = Episode(
    id = id,
    name = name,
    season = season,
    number = number,
    airdate = airdate,
    plot = plot,
    resolution = resolution
)

fun EpisodeDetailDto.toDomain() = EpisodeDetail(
    id = id,
    name = name,
    showId = showId,
    showName = showName,
    season = season,
    number = number,
    streamUrl = url,
    subtitles = subtitles.map { it.toDomain() }
)

fun SubtitleDto.toDomain() = Subtitle(
    lang = lang,
    url = url
)
