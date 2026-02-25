package tv.ororo.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import tv.ororo.app.ui.components.ContentCard
import tv.ororo.app.ui.components.GenreRow
import tv.ororo.app.ui.components.SortFilterBar

@Composable
fun MovieBrowseScreen(
    onMovieClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Log.d("MovieBrowse", "Compose: isLoading=${uiState.isLoading}, movies=${uiState.filteredMovies.size}, error=${uiState.error}")

    LaunchedEffect(uiState.isLoading) {
        Log.d("MovieBrowse", "LaunchedEffect: isLoading=${uiState.isLoading}, movies=${uiState.filteredMovies.size}")
        if (!uiState.isLoading && uiState.filteredMovies.isNotEmpty()) {
            try {
                focusRequester.requestFocus()
                Log.d("MovieBrowse", "Focus requested successfully")
            } catch (e: Exception) {
                Log.e("MovieBrowse", "Focus request failed: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .onKeyEvent { keyEvent ->
                Log.d("MovieBrowse", "Key event: $keyEvent")
                false
            }
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Movies",
                color = Color.White,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${uiState.filteredMovies.size} titles",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        // Sort bar
        SortFilterBar(
            currentSort = uiState.currentSort,
            selectedGenre = uiState.selectedGenre,
            genres = uiState.genres,
            onSortSelected = viewModel::onSortChanged,
            onGenreSelected = viewModel::onGenreChanged
        )

        // Genre chips
        if (uiState.genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            GenreRow(
                genres = uiState.genres,
                selectedGenre = uiState.selectedGenre,
                onGenreSelected = viewModel::onGenreChanged
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF6C63FF))
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.error!!, color = Color(0xFFFF6B6B))
                }
            }
            else -> {
                TvLazyVerticalGrid(
                    columns = TvGridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            Log.d("MovieBrowse", "Grid focus changed: $focusState")
                        }
                ) {
                    items(uiState.filteredMovies, key = { it.id }) { movie ->
                        ContentCard(
                            title = movie.name,
                            posterUrl = movie.posterUrl,
                            year = movie.year,
                            rating = movie.imdbRating,
                            isWatched = uiState.watchedMovieIds.contains(movie.id),
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }
    }
}
