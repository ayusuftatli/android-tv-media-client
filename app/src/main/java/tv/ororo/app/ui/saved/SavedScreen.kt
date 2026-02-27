package tv.ororo.app.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import tv.ororo.app.ui.components.ContentCard

private data class SavedItem(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    val isMovie: Boolean
)

@Composable
fun SavedScreen(
    onMovieClick: (Int) -> Unit,
    onShowClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SavedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val totalSaved = uiState.savedMovies.size + uiState.savedShows.size
    val savedItems = uiState.savedMovies.map { movie ->
        SavedItem(
            id = movie.id,
            title = movie.name,
            posterUrl = movie.posterUrl,
            year = movie.year,
            rating = movie.imdbRating,
            isMovie = true
        )
    } + uiState.savedShows.map { show ->
        SavedItem(
            id = show.id,
            title = show.name,
            posterUrl = show.posterUrl,
            year = show.year,
            rating = show.imdbRating,
            isMovie = false
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved",
                color = Color.White,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$totalSaved titles",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6C63FF))
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.error!!,
                        color = Color(0xFFFF6B6B),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.retry() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            }

            totalSaved == 0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No saved titles yet.",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Open a movie or show and use Save to add it here.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }

            else -> {
                TvLazyVerticalGrid(
                    columns = TvGridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedItems, key = { item -> "${if (item.isMovie) "movie" else "show"}-${item.id}" }) { item ->
                        ContentCard(
                            title = item.title,
                            posterUrl = item.posterUrl,
                            year = item.year,
                            rating = item.rating,
                            isWatched = item.isMovie && uiState.watchedMovieIds.contains(item.id),
                            onClick = {
                                if (item.isMovie) onMovieClick(item.id) else onShowClick(item.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
