package tv.ororo.app.ui.shows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage

@Composable
fun ShowDetailScreen(
    showId: Int,
    onEpisodeClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: ShowDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    color = Color(0xFF6C63FF),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    color = Color(0xFFFF6B6B),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.show != null -> {
                ShowDetailContent(
                    uiState = uiState,
                    onSeasonSelected = viewModel::onSeasonSelected,
                    onEpisodeClick = onEpisodeClick
                )
            }
        }
    }
}

@Composable
private fun ShowDetailContent(
    uiState: ShowDetailUiState,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (Int) -> Unit
) {
    val show = uiState.show!!
    val seasons = show.episodes.map { it.season }.distinct().sorted()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Left: Poster + Info
        Column(
            modifier = Modifier.width(280.dp)
        ) {
            AsyncImage(
                model = show.posterUrl,
                contentDescription = show.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = show.name, color = Color.White, fontSize = 22.sp)

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (show.year != null) {
                    Text(text = show.year.toString(), color = Color.Gray, fontSize = 14.sp)
                }
                if (show.imdbRating != null && show.imdbRating > 0) {
                    Text(
                        text = "★ ${"%.1f".format(show.imdbRating)}",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp
                    )
                }
                if (show.ended == true) {
                    Text(text = "Ended", color = Color.Gray, fontSize = 14.sp)
                }
            }

            if (show.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = show.genres.joinToString(" · "),
                    color = Color(0xFF6C63FF),
                    fontSize = 12.sp
                )
            }

            if (!show.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = show.description,
                    color = Color(0xFFB0B0B0),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Right: Seasons + Episodes
        Column(modifier = Modifier.weight(1f)) {
            // Season tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                seasons.forEach { season ->
                    Surface(
                        onClick = { onSeasonSelected(season) },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (season == uiState.selectedSeason) Color(0xFF6C63FF) else Color(0xFF16213e),
                            focusedContainerColor = Color(0xFF6C63FF)
                        ),
                        shape = ClickableSurfaceDefaults.shape(
                            shape = RoundedCornerShape(8.dp)
                        ),
                        modifier = Modifier.onFocusChanged { focusState ->
                            if (focusState.isFocused && season != uiState.selectedSeason) {
                                onSeasonSelected(season)
                            }
                        }
                    ) {
                        Text(
                            text = "S$season",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Episodes list
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uiState.seasonEpisodes.forEach { episode ->
                    EpisodeRow(
                        episode = episode,
                        showName = show.name,
                        isWatched = uiState.watchedEpisodeIds.contains(episode.id),
                        onClick = { onEpisodeClick(episode.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: tv.ororo.app.data.domain.model.Episode,
    showName: String,
    isWatched: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF16213e),
            focusedContainerColor = Color(0xFF6C63FF)
        ),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "S%02dE%02d".format(episode.season, episode.number),
                    color = Color.White,
                    fontSize = 14.sp
                )
                if (!episode.name.isNullOrBlank()) {
                    Text(
                        text = episode.name,
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (episode.resolution != null) {
                Text(
                    text = episode.resolution,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            if (isWatched) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Watched",
                    color = Color(0xFF77DD77),
                    fontSize = 11.sp
                )
            }
        }
    }
}
