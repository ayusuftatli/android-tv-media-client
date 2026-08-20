package tv.ororo.app.ui.shows

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import tv.ororo.app.data.domain.model.Episode
import tv.ororo.app.ui.components.TvActionButton
import tv.ororo.app.ui.theme.OroroColors
import tv.ororo.app.ui.theme.OroroFocusDefaults
import tv.ororo.app.ui.theme.OroroShapes

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
                    Text(text = uiState.error!!, color = OroroColors.Error)
                    Spacer(modifier = Modifier.height(12.dp))
                    TvActionButton(
                        text = "Retry",
                        primary = true,
                        onClick = viewModel::retry
                    )
                }
            }

            uiState.show != null -> {
                ShowDetailContent(
                    uiState = uiState,
                    onSeasonSelected = viewModel::onSeasonSelected,
                    onSaveClick = viewModel::toggleSaved,
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
    onSaveClick: () -> Unit,
    onEpisodeClick: (Int) -> Unit
) {
    val show = uiState.show!!
    val seasons = show.episodes.map { it.season }.distinct().sorted()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(modifier = Modifier.width(280.dp)) {
            AsyncImage(
                model = show.posterUrl,
                contentDescription = show.name,
                modifier = Modifier
                    .width(254.dp)
                    .height(380.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = show.name, color = OroroColors.TextPrimary, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (show.year != null) {
                    Text(
                        text = show.year.toString(),
                        color = OroroColors.TextMuted,
                        fontSize = 14.sp
                    )
                }
                if (show.imdbRating != null && show.imdbRating > 0) {
                    Text(
                        text = "★ ${"%.1f".format(show.imdbRating)}",
                        color = OroroColors.Rating,
                        fontSize = 14.sp
                    )
                }
                if (show.ended == true) {
                    Text(text = "Ended", color = OroroColors.TextMuted, fontSize = 14.sp)
                }
            }

            if (show.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = show.genres.joinToString(" · "),
                    color = OroroColors.Accent,
                    fontSize = 12.sp
                )
            }

            if (!show.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = show.description,
                    color = OroroColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.resumeEpisode?.let { episode ->
                    TvActionButton(
                        text = "Resume ${formatEpisodeCode(episode)}",
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        onClick = { onEpisodeClick(episode.id) },
                        modifier = Modifier.height(44.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TvActionButton(
                    text = if (uiState.isSaved) "Saved" else "Save",
                    icon = if (uiState.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    selected = uiState.isSaved,
                    containerColor = if (uiState.isSaved) OroroColors.SuccessStrong else null,
                    onClick = onSaveClick,
                    modifier = Modifier.height(44.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                seasons.forEach { season ->
                    SeasonTab(
                        season = season,
                        selected = season == uiState.selectedSeason,
                        onSelected = { onSeasonSelected(season) }
                    )
                }
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.seasonEpisodes.forEach { episode ->
                    EpisodeRow(
                        episode = episode,
                        isWatched = uiState.watchedEpisodeIds.contains(episode.id),
                        onClick = { onEpisodeClick(episode.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonTab(
    season: Int,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = OroroShapes.Small
    val containerColor = if (selected) OroroColors.Accent else OroroColors.Surface

    Surface(
        onClick = onSelected,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
            pressedContainerColor = containerColor
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = OroroFocusDefaults.scale(),
        border = OroroFocusDefaults.border(shape),
        interactionSource = interactionSource,
        modifier = Modifier
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !selected) onSelected()
            }
    ) {
        Text(
            text = "S$season",
            color = OroroColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    isWatched: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = OroroShapes.Small

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OroroColors.Surface,
            focusedContainerColor = OroroColors.Surface,
            pressedContainerColor = OroroColors.Surface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = OroroFocusDefaults.scale(),
        border = OroroFocusDefaults.border(shape),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 1f else 0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = OroroColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatEpisodeCode(episode),
                    color = OroroColors.TextPrimary,
                    fontSize = 14.sp
                )
                if (!episode.name.isNullOrBlank()) {
                    Text(
                        text = episode.name,
                        color = OroroColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            episode.resolution?.let { resolution ->
                Text(text = resolution, color = OroroColors.TextMuted, fontSize = 11.sp)
            }
            if (isWatched) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Watched", color = OroroColors.Success, fontSize = 11.sp)
            }
        }
    }
}
