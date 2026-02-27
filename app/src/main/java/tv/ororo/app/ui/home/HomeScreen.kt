package tv.ororo.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import kotlinx.coroutines.launch
import tv.ororo.app.R
import tv.ororo.app.ui.components.ContentCard

@Composable
fun HomeScreen(
    onMoviesClick: () -> Unit,
    onShowsClick: () -> Unit,
    onSavedClick: () -> Unit,
    onSearchClick: () -> Unit,
    onContinueWatchingClick: (String, Int) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    var initialFocusApplied by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoadingContinueWatching, uiState.continueWatching.isNotEmpty()) {
        if (initialFocusApplied || uiState.isLoadingContinueWatching) return@LaunchedEffect
        val initialFocusTarget =
            if (uiState.continueWatching.isNotEmpty()) continueWatchingFocusRequester else searchFocusRequester
        try {
            initialFocusTarget.requestFocus()
            initialFocusApplied = true
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Image(
                painter = painterResource(id = R.drawable.ororo_logo),
                contentDescription = "Ororo TV logo",
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Ororo TV",
                fontSize = 32.sp,
                color = Color.White
            )

            if (uiState.isLoadingContinueWatching) {
                Spacer(modifier = Modifier.height(28.dp))
                CircularProgressIndicator(color = Color(0xFF6C63FF))
            }

            if (!uiState.isLoadingContinueWatching && uiState.continueWatching.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                ContinueWatchingRow(
                    items = uiState.continueWatching,
                    onContinueWatchingClick = onContinueWatchingClick,
                    firstItemFocusRequester = continueWatchingFocusRequester
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                HomeCard(
                    title = "Search",
                    icon = Icons.Default.Search,
                    onClick = onSearchClick,
                    focusRequester = searchFocusRequester,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "Movies",
                    icon = Icons.Default.Movie,
                    onClick = onMoviesClick,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "Saved",
                    icon = Icons.Default.Bookmark,
                    onClick = onSavedClick,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "TV Shows",
                    icon = Icons.Default.Tv,
                    onClick = onShowsClick,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "Clear Cache",
                    icon = Icons.Default.Cached,
                    onClick = { viewModel.clearCache() },
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "Logout",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = {
                        scope.launch {
                            viewModel.logout()
                            onLogout()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onContinueWatchingClick: (String, Int) -> Unit,
    firstItemFocusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Continue Watching",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 80.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> "${item.contentType}:${item.contentId}" }
            ) { index, item ->
                val cardModifier = if (index == 0) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                }
                Column {
                    ContentCard(
                        title = item.title,
                        posterUrl = item.posterUrl,
                        year = item.year,
                        rating = item.rating,
                        onClick = { onContinueWatchingClick(item.contentType, item.contentId) },
                        modifier = cardModifier
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${item.progressPercent}% watched",
                        color = Color(0xFF77DD77),
                        fontSize = 11.sp
                    )
                    if (!item.subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            color = Color(0xFFB0B0B0),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val cardModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    Surface(
        onClick = onClick,
        modifier = cardModifier.height(180.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF16213e),
            focusedContainerColor = Color(0xFF6C63FF)
        ),
        shape = ClickableSurfaceDefaults.shape(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }
}
