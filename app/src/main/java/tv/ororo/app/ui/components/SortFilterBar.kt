package tv.ororo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import tv.ororo.app.ui.theme.OroroColors

enum class SortOption(val label: String) {
    TITLE("Title"),
    ADDED("Added"),
    YEAR("Year"),
    RATING("Rating")
}

private enum class FilterFocusTarget { SORT, GENRE }

@Composable
fun SortFilterBar(
    currentSort: SortOption,
    selectedGenre: String?,
    genres: List<String>,
    onSortSelected: (SortOption) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortDialog by remember { mutableStateOf(false) }
    var showGenreDialog by remember { mutableStateOf(false) }
    var restoreFocusTo by remember { mutableStateOf<FilterFocusTarget?>(null) }
    val sortFocusRequester = remember { FocusRequester() }
    val genreFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showSortDialog, showGenreDialog, restoreFocusTo) {
        if (!showSortDialog && !showGenreDialog) {
            try {
                when (restoreFocusTo) {
                    FilterFocusTarget.SORT -> sortFocusRequester.requestFocus()
                    FilterFocusTarget.GENRE -> genreFocusRequester.requestFocus()
                    null -> Unit
                }
            } catch (_: IllegalStateException) {
            }
            restoreFocusTo = null
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TvActionButton(
            text = "Sort: ${currentSort.label}",
            onClick = { showSortDialog = true },
            modifier = Modifier.focusRequester(sortFocusRequester)
        )
        TvActionButton(
            text = "Genre: ${selectedGenre ?: "All"}",
            onClick = { showGenreDialog = true },
            modifier = Modifier.focusRequester(genreFocusRequester)
        )
    }

    if (showSortDialog) {
        ChoiceDialog(
            title = "Sort titles",
            labels = SortOption.entries.map { it.label },
            selectedIndex = SortOption.entries.indexOf(currentSort),
            onSelected = { index ->
                onSortSelected(SortOption.entries[index])
                showSortDialog = false
                restoreFocusTo = FilterFocusTarget.SORT
            },
            onDismiss = {
                showSortDialog = false
                restoreFocusTo = FilterFocusTarget.SORT
            }
        )
    }

    if (showGenreDialog) {
        val genreLabels = listOf("All") + genres
        val selectedIndex = selectedGenre
            ?.let(genres::indexOf)
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        ChoiceDialog(
            title = "Choose genre",
            labels = genreLabels,
            selectedIndex = selectedIndex,
            onSelected = { index ->
                onGenreSelected(if (index == 0) null else genres[index - 1])
                showGenreDialog = false
                restoreFocusTo = FilterFocusTarget.GENRE
            },
            onDismiss = {
                showGenreDialog = false
                restoreFocusTo = FilterFocusTarget.GENRE
            }
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(Unit) {
        try {
            initialFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(labels) { index, label ->
                    TvActionButton(
                        text = label,
                        onClick = { onSelected(index) },
                        selected = index == selectedIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == selectedIndex) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
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
