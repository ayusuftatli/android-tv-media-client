package tv.ororo.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import tv.ororo.app.ui.components.TvActionButton
import tv.ororo.app.ui.theme.OroroColors

@Composable
fun MovieDetailScreen(
    movieId: Int,
    onPlayClick: () -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OroroColors.Background)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    color = OroroColors.Accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.error!!,
                        color = OroroColors.Error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TvActionButton(
                        text = "Retry",
                        primary = true,
                        onClick = viewModel::retry
                    )
                }
            }
            uiState.movie != null -> {
                val movie = uiState.movie!!
                MovieDetailContent(
                    movie = movie,
                    isSaved = uiState.isSaved,
                    playbackLabel = moviePlaybackLabel(uiState.watchState),
                    onPlayClick = onPlayClick,
                    onSaveClick = viewModel::toggleSaved
                )
            }
        }
    }
}

@Composable
private fun MovieDetailContent(
    movie: tv.ororo.app.data.domain.model.MovieDetail,
    isSaved: Boolean,
    playbackLabel: String,
    onPlayClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Poster
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = movie.name,
            modifier = Modifier
                .width(250.dp)
                .height(375.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(32.dp))

        // Info
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = movie.name,
                color = OroroColors.TextPrimary,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (movie.year != null) {
                    Text(text = movie.year.toString(), color = OroroColors.TextMuted, fontSize = 16.sp)
                }
                if (movie.imdbRating != null && movie.imdbRating > 0) {
                    Text(
                        text = "★ ${"%.1f".format(movie.imdbRating)}",
                        color = OroroColors.Rating,
                        fontSize = 16.sp
                    )
                }
            }

            if (movie.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = movie.genres.joinToString(" · "),
                    color = OroroColors.Accent,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    text = playbackLabel,
                    onClick = onPlayClick,
                    icon = Icons.Default.PlayArrow,
                    primary = true,
                    modifier = Modifier.height(48.dp)
                )

                TvActionButton(
                    text = if (isSaved) "Saved" else "Save",
                    onClick = onSaveClick,
                    icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    selected = isSaved,
                    containerColor = if (isSaved) OroroColors.SuccessStrong else null,
                    modifier = Modifier.height(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!movie.description.isNullOrBlank()) {
                Text(
                    text = movie.description,
                    color = OroroColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }

            if (movie.subtitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Subtitles: ${movie.subtitles.joinToString(", ") { it.lang }}",
                    color = OroroColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
