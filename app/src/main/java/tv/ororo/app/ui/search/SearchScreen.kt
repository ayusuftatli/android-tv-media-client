package tv.ororo.app.ui.search

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
fun SearchScreen(
    onMovieClick: (Int) -> Unit,
    onShowClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        try {
            searchFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    val dismissKeyboard: () -> Unit = {
        if (!focusManager.moveFocus(FocusDirection.Down)) {
            focusManager.clearFocus(force = true)
        }
        keyboardController?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OroroColors.Background)
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChanged,
            placeholder = { Text("Search movies and shows...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OroroColors.TextPrimary,
                unfocusedTextColor = OroroColors.TextPrimary,
                focusedBorderColor = OroroColors.Accent,
                unfocusedBorderColor = OroroColors.TextMuted,
                focusedPlaceholderColor = OroroColors.TextMuted,
                unfocusedPlaceholderColor = OroroColors.TextMuted,
                cursorColor = OroroColors.TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .focusRequester(searchFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    val nativeEvent = keyEvent.nativeKeyEvent
                    if (nativeEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    if (!isSearchSubmitKey(nativeEvent.keyCode)) return@onPreviewKeyEvent false
                    dismissKeyboard()
                    true
                }
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = OroroColors.Accent)
            }
            return
        }

        if (uiState.error != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.error!!,
                    color = OroroColors.Error,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TvActionButton(
                    text = "Retry",
                    primary = true,
                    onClick = viewModel::retry
                )
            }
            return
        }

        val totalResults = uiState.movieResults.size + uiState.showResults.size

        if (uiState.query.length >= 2) {
            Text(
                text = "$totalResults results",
                color = OroroColors.TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        if (uiState.query.length < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enter at least 2 characters to search.",
                    color = OroroColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
            return
        }

        if (totalResults == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No movies or shows found.",
                    color = OroroColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
            return
        }

        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.movieResults, key = { "movie_${it.id}" }) { movie ->
                ContentCard(
                    title = movie.name,
                    posterUrl = movie.posterUrl,
                    year = movie.year,
                    rating = movie.imdbRating,
                    isWatched = uiState.watchedMovieIds.contains(movie.id),
                    onClick = { onMovieClick(movie.id) }
                )
            }
            items(uiState.showResults, key = { "show_${it.id}" }) { show ->
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

internal fun isSearchSubmitKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == KeyEvent.KEYCODE_SEARCH
}
