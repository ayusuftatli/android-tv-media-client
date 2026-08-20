package tv.ororo.app.ui.trending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import tv.ororo.app.ui.components.ContentCard
import tv.ororo.app.ui.components.TvActionButton
import tv.ororo.app.ui.theme.OroroColors

@Composable
fun TrendingShowsScreen(
    onShowClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: TrendingShowsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isLoading, uiState.shows, uiState.error) {
        try {
            when {
                uiState.error != null -> retryFocusRequester.requestFocus()
                !uiState.isLoading && uiState.shows.isNotEmpty() -> gridFocusRequester.requestFocus()
            }
        } catch (_: IllegalStateException) {
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OroroColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Weekly Trending TV Series",
                    color = OroroColors.TextPrimary,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trendingShowsSummary(uiState),
                    color = OroroColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (uiState.isStale) {
                Text(
                    text = "Cached results",
                    color = OroroColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = OroroColors.Accent)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (uiState.totalLookups > 0) {
                                "Matching ${uiState.completedLookups} of ${uiState.totalLookups} series…"
                            } else {
                                "Loading TMDB's weekly TV chart…"
                            },
                            color = OroroColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error.orEmpty(),
                            color = OroroColors.Error,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TvActionButton(
                            text = "Retry",
                            primary = true,
                            onClick = viewModel::retry,
                            modifier = Modifier.focusRequester(retryFocusRequester)
                        )
                    }
                }
            }
            uiState.shows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "None of TMDB's current top TV series are available on Ororo.",
                        color = OroroColors.TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }
            else -> {
                TvLazyVerticalGrid(
                    columns = TvGridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.focusRequester(gridFocusRequester)
                ) {
                    items(uiState.shows, key = { it.show.id }) { item ->
                        ContentCard(
                            title = item.show.name,
                            posterUrl = item.show.posterUrl,
                            year = item.show.year,
                            rating = item.show.imdbRating,
                            badgeText = "#${item.rank}",
                            onClick = { onShowClick(item.show.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun trendingShowsSummary(state: TrendingShowsUiState): String = when {
    state.isLoading && state.totalLookups == 0 -> "TMDB weekly top 100 • available on Ororo"
    state.isLoading -> "Checking which TMDB TV series are available on Ororo"
    state.rankedShowCount > 0 ->
        "${state.shows.size} of TMDB's top ${state.rankedShowCount} available on Ororo"
    else -> "TMDB weekly top 100 • available on Ororo"
}
