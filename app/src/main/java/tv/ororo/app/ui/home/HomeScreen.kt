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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cached
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import kotlinx.coroutines.launch
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
    onSearchClick: () -> Unit,
    onContinueWatchingClick: (String, Int) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val moviesFocusRequester = remember { FocusRequester() }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    var initialFocusApplied by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoadingContinueWatching, uiState.continueWatching.size) {
        if (!uiState.isLoadingContinueWatching && !initialFocusApplied) {
            try {
                if (uiState.continueWatching.isNotEmpty()) {
                    continueWatchingFocusRequester.requestFocus()
                } else {
                    moviesFocusRequester.requestFocus()
                }
                initialFocusApplied = true
            } catch (_: Exception) {
            }
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
                onSearchClick = onSearchClick,
                onSettingsClick = { showSettings = true }
            )

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
                    onContinueWatchingClick = onContinueWatchingClick,
                    firstItemFocusRequester = continueWatchingFocusRequester
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            BrowseRow(
                onMoviesClick = onMoviesClick,
                onShowsClick = onShowsClick,
                onSavedClick = onSavedClick,
                moviesFocusRequester = moviesFocusRequester
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSettings) {
        SettingsDialog(
            onClearData = {
                showSettings = false
                showClearConfirmation = true
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

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear watch history & cache?") },
            text = {
                Text("Your saved movies and shows will not be removed.")
            },
            confirmButton = {
                TvActionButton(
                    text = "Clear data",
                    icon = Icons.Default.Cached,
                    primary = true,
                    onClick = {
                        viewModel.clearLocalData()
                        showClearConfirmation = false
                    }
                )
            },
            dismissButton = {
                TvActionButton(
                    text = "Cancel",
                    onClick = { showClearConfirmation = false }
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
    onSearchClick: () -> Unit,
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
            text = "Search",
            icon = Icons.Default.Search,
            onClick = onSearchClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        TvActionButton(
            text = "Settings",
            icon = Icons.Default.Settings,
            onClick = onSettingsClick
        )
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onContinueWatchingClick: (String, Int) -> Unit,
    firstItemFocusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Continue Watching")
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = OroroDimens.HomeHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                Column(modifier = Modifier.width(160.dp)) {
                    ContentCard(
                        title = item.title,
                        posterUrl = item.posterUrl,
                        year = item.year,
                        rating = item.rating,
                        progressPercent = item.progressPercent,
                        onClick = { onContinueWatchingClick(item.contentType, item.contentId) },
                        modifier = cardModifier
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
    moviesFocusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Browse")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.padding(horizontal = OroroDimens.HomeHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeCard(
                title = "Movies",
                icon = Icons.Default.Movie,
                onClick = onMoviesClick,
                modifier = Modifier.focusRequester(moviesFocusRequester)
            )
            HomeCard(
                title = "TV Shows",
                icon = Icons.Default.Tv,
                onClick = onShowsClick
            )
            HomeCard(
                title = "Saved",
                icon = Icons.Default.Bookmark,
                onClick = onSavedClick
            )
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
    onClearData: () -> Unit,
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
                    text = "Clear watch history & cache",
                    icon = Icons.Default.Cached,
                    onClick = onClearData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstOptionFocusRequester)
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
