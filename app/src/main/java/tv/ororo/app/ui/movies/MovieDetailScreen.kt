package tv.ororo.app.ui.movies

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

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
            uiState.movie != null -> {
                val movie = uiState.movie!!
                MovieDetailContent(movie = movie, onPlayClick = onPlayClick)
            }
        }
    }
}

@Composable
private fun MovieDetailContent(
    movie: tv.ororo.app.data.domain.model.MovieDetail,
    onPlayClick: () -> Unit
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
                .fillMaxHeight()
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
                color = Color.White,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (movie.year != null) {
                    Text(text = movie.year.toString(), color = Color.Gray, fontSize = 16.sp)
                }
                if (movie.imdbRating != null && movie.imdbRating > 0) {
                    Text(
                        text = "★ ${"%.1f".format(movie.imdbRating)}",
                        color = Color(0xFFFFD700),
                        fontSize = 16.sp
                    )
                }
            }

            if (movie.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = movie.genres.joinToString(" · "),
                    color = Color(0xFF6C63FF),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Play button
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!movie.description.isNullOrBlank()) {
                Text(
                    text = movie.description,
                    color = Color(0xFFB0B0B0),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }

            if (movie.subtitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Subtitles: ${movie.subtitles.joinToString(", ") { it.lang }}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
