package tv.ororo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import tv.ororo.app.ui.theme.OroroColors
import tv.ororo.app.ui.theme.OroroFocusDefaults
import tv.ororo.app.ui.theme.OroroShapes

@Composable
fun ContentCard(
    title: String,
    posterUrl: String?,
    year: Int?,
    rating: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    progressPercent: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = OroroShapes.Small
    val progressFraction = progressPercent
        ?.coerceIn(0, 100)
        ?.div(100f)

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .zIndex(if (isFocused) 1f else 0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OroroColors.Surface,
            focusedContainerColor = OroroColors.Surface,
            pressedContainerColor = OroroColors.Surface
        ),
        shape = ClickableSurfaceDefaults.shape(
            shape = shape
        ),
        scale = OroroFocusDefaults.scale(),
        border = OroroFocusDefaults.border(shape),
        interactionSource = interactionSource
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(OroroColors.PosterPlaceholder)
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = OroroColors.TextMuted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp)
                )
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop
                )
                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(OroroColors.SuccessStrong, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Watched",
                            tint = OroroColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (progressFraction != null && progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(OroroColors.ProgressTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(5.dp)
                                .background(OroroColors.Accent)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = title,
                    color = OroroColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (year != null) {
                        Text(
                            text = year.toString(),
                            color = OroroColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (rating != null && rating > 0) {
                        Text(
                            text = "★ ${"%.1f".format(rating)}",
                            color = OroroColors.Rating,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
