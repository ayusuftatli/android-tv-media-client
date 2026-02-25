package tv.ororo.app.ui.shows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import tv.ororo.app.ui.components.GenreRow
import tv.ororo.app.ui.components.SortFilterBar

@Composable
fun ShowBrowseScreen(
    onShowClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: ShowBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                text = "TV Shows",
                color = Color.White,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${uiState.filteredShows.size} titles",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        SortFilterBar(
            currentSort = uiState.currentSort,
            selectedGenre = uiState.selectedGenre,
            genres = uiState.genres,
            onSortSelected = viewModel::onSortChanged,
            onGenreSelected = viewModel::onGenreChanged
        )

        if (uiState.genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            GenreRow(
                genres = uiState.genres,
                selectedGenre = uiState.selectedGenre,
                onGenreSelected = viewModel::onGenreChanged
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredShows, key = { it.id }) { show ->
                        ContentCard(
                            title = show.name,
                            posterUrl = show.posterUrl,
                            year = show.year,
                            rating = show.imdbRating,
                            onClick = { onShowClick(show.id) }
                        )
                    }
                }
            }
        }
    }
}
