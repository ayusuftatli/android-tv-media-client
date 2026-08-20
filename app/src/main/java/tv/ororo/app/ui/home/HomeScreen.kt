package tv.ororo.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import tv.ororo.app.R
import tv.ororo.app.ui.components.ContentCard
import tv.ororo.app.ui.components.TvActionButton
import tv.ororo.app.ui.theme.OroroColors
import tv.ororo.app.ui.theme.OroroDimens
import tv.ororo.app.ui.theme.OroroFocusDefaults
import tv.ororo.app.ui.theme.OroroShapes

@Composable
fun HomeScreen(
    onMoviesClick: () -> Unit,
    onShowsClick: () -> Unit,
    onSavedClick: () -> Unit,
    onTrendingMoviesClick: () -> Unit,
    onTrendingShowsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onContinueWatchingClick: (String, Int) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showClearHistoryConfirmation by remember { mutableStateOf(false) }
    var showClearCacheConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            searchFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OroroColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HomeHeader(
                onSettingsClick = { showSettings = true }
            )
            PrimarySearchCard(
                onClick = onSearchClick,
                modifier = Modifier.focusRequester(searchFocusRequester)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoadingContinueWatching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = OroroColors.Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (uiState.continueWatching.isNotEmpty()) {
                ContinueWatchingRow(
                    items = uiState.continueWatching,
                    onContinueWatchingClick = onContinueWatchingClick
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            BrowseRow(
                onMoviesClick = onMoviesClick,
                onShowsClick = onShowsClick,
                onSavedClick = onSavedClick,
                onTrendingMoviesClick = onTrendingMoviesClick,
                onTrendingShowsClick = onTrendingShowsClick
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSettings) {
        SettingsDialog(
            onClearWatchHistory = {
                showSettings = false
                showClearHistoryConfirmation = true
            },
            onClearCache = {
                showSettings = false
                showClearCacheConfirmation = true
            },
            onAbout = {
                showSettings = false
                showAbout = true
            },
            onLogout = {
                showSettings = false
                scope.launch {
                    viewModel.logout()
                    onLogout()
                }
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showAbout) {
        AboutAndAttributionDialog(onDismiss = { showAbout = false })
    }

    if (showClearHistoryConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirmation = false },
            title = { Text("Clear watch history?") },
            text = {
                Text("This removes watched status and resume positions. Your saved movies and shows will not be removed.")
            },
            confirmButton = {
                TvActionButton(
                    text = "Clear history",
                    icon = Icons.Default.History,
                    primary = true,
                    onClick = {
                        viewModel.clearWatchHistory()
                        showClearHistoryConfirmation = false
                    }
                )
            },
            dismissButton = {
                TvActionButton(
                    text = "Cancel",
                    onClick = { showClearHistoryConfirmation = false }
                )
            },
            containerColor = OroroColors.SurfaceRaised,
            titleContentColor = OroroColors.TextPrimary,
            textContentColor = OroroColors.TextSecondary
        )
    }

    if (showClearCacheConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmation = false },
            title = { Text("Clear cache?") },
            text = {
                Text("This removes cached posters and temporary catalog data. Your watch history, saved titles, and login will not be removed.")
            },
            confirmButton = {
                TvActionButton(
                    text = "Clear cache",
                    icon = Icons.Default.Cached,
                    primary = true,
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheConfirmation = false
                    }
                )
            },
            dismissButton = {
                TvActionButton(
                    text = "Cancel",
                    onClick = { showClearCacheConfirmation = false }
                )
            },
            containerColor = OroroColors.SurfaceRaised,
            titleContentColor = OroroColors.TextPrimary,
            textContentColor = OroroColors.TextSecondary
        )
    }
}

@Composable
private fun HomeHeader(
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OroroDimens.HomeHorizontalPadding,
                vertical = 22.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ororo_logo),
            contentDescription = "Ororo TV logo",
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Ororo TV",
            fontSize = 24.sp,
            color = OroroColors.TextPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        TvActionButton(
            text = "Settings",
            icon = Icons.Default.Settings,
            onClick = onSettingsClick
        )
    }
}

@Composable
private fun PrimarySearchCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = OroroShapes.Medium

    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = OroroDimens.HomeHorizontalPadding)
            .fillMaxWidth()
            .height(112.dp)
            .zIndex(if (isFocused) 1f else 0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OroroColors.Surface,
            focusedContainerColor = OroroColors.Surface,
            pressedContainerColor = OroroColors.Surface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = OroroFocusDefaults.scale(),
        border = OroroFocusDefaults.border(shape),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = OroroColors.FocusRing,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    text = "Search",
                    color = OroroColors.TextPrimary,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Find any movie or TV show",
                    color = OroroColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onContinueWatchingClick: (String, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Continue Watching")
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = OroroDimens.HomeHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = items,
                key = { item -> "${item.contentType}:${item.contentId}" }
            ) { item ->
                Column(modifier = Modifier.width(160.dp)) {
                    ContentCard(
                        title = item.title,
                        posterUrl = item.posterUrl,
                        year = item.year,
                        rating = item.rating,
                        progressPercent = item.progressPercent,
                        onClick = { onContinueWatchingClick(item.contentType, item.contentId) }
                    )
                    if (!item.subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.subtitle,
                            color = OroroColors.TextSecondary,
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
private fun BrowseRow(
    onMoviesClick: () -> Unit,
    onShowsClick: () -> Unit,
    onSavedClick: () -> Unit,
    onTrendingMoviesClick: () -> Unit,
    onTrendingShowsClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Browse")
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = OroroDimens.HomeHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeCard(
                    title = "Movies",
                    icon = Icons.Default.Movie,
                    onClick = onMoviesClick
                )
            }
            item {
                HomeCard(
                    title = "TV Shows",
                    icon = Icons.Default.Tv,
                    onClick = onShowsClick
                )
            }
            item {
                HomeCard(
                    title = "Saved",
                    icon = Icons.Default.Bookmark,
                    onClick = onSavedClick
                )
            }
            item {
                HomeCard(
                    title = "Trending Movies",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    onClick = onTrendingMoviesClick
                )
            }
            item {
                HomeCard(
                    title = "Trending TV",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    onClick = onTrendingShowsClick
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = OroroColors.TextPrimary,
        fontSize = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OroroDimens.HomeHorizontalPadding)
    )
}

@Composable
private fun HomeCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = OroroShapes.Medium

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(220.dp)
            .height(100.dp)
            .zIndex(if (isFocused) 1f else 0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OroroColors.Surface,
            focusedContainerColor = OroroColors.Surface,
            pressedContainerColor = OroroColors.Surface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = OroroFocusDefaults.scale(),
        border = OroroFocusDefaults.border(shape),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OroroColors.TextPrimary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = OroroColors.TextPrimary,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    onClearWatchHistory: () -> Unit,
    onClearCache: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstOptionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstOptionFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(
                    text = "Clear watch history",
                    icon = Icons.Default.History,
                    onClick = onClearWatchHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstOptionFocusRequester)
                )
                TvActionButton(
                    text = "Clear cache",
                    icon = Icons.Default.Cached,
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth()
                )
                TvActionButton(
                    text = "About & data attribution",
                    icon = Icons.Default.Info,
                    onClick = onAbout,
                    modifier = Modifier.fillMaxWidth()
                )
                TvActionButton(
                    text = "Sign out",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OroroColors.Accent)
            }
        },
        containerColor = OroroColors.SurfaceRaised,
        titleContentColor = OroroColors.TextPrimary,
        textContentColor = OroroColors.TextSecondary
    )
}

@Composable
private fun AboutAndAttributionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About & data attribution") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AsyncImage(
                    model = R.raw.tmdb_logo,
                    contentDescription = "The Movie Database logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 120.dp, height = 86.dp)
                )
                Text(
                    text = "Weekly trending data is provided by TMDB.",
                    color = OroroColors.TextPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    color = OroroColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OroroColors.Accent)
            }
        },
        containerColor = OroroColors.SurfaceRaised,
        titleContentColor = OroroColors.TextPrimary,
        textContentColor = OroroColors.TextSecondary
    )
}
