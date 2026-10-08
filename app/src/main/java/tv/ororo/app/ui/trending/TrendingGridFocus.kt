package tv.ororo.app.ui.trending

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.foundation.lazy.grid.TvLazyGridState
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState

internal class TrendingGridFocus(val gridState: TvLazyGridState) {
    var focusedId: Int? = null
    var previousIds: List<Int> = emptyList()
    val requesters = mutableMapOf<Int, FocusRequester>()

}

internal fun Modifier.trendingCardFocus(focus: TrendingGridFocus, id: Int): Modifier = this
    .focusRequester(focus.requesters.getOrPut(id) { FocusRequester() })
    .onFocusChanged { if (it.isFocused) focus.focusedId = id }

@Composable
internal fun rememberTrendingGridFocus(ids: List<Int>): TrendingGridFocus {
    val gridState = rememberTvLazyGridState()
    val focus = remember(gridState) { TrendingGridFocus(gridState) }
    LaunchedEffect(ids) {
        val oldIndex = focus.previousIds.indexOf(focus.focusedId).coerceAtLeast(0)
        val target = trendingFocusTarget(focus.focusedId, oldIndex, ids)
        val needsFocus = focus.focusedId == null || focus.focusedId !in ids
        focus.previousIds = ids
        focus.requesters.keys.retainAll(ids.toSet())
        if (target == null) {
            focus.focusedId = null
            return@LaunchedEffect
        }
        val index = ids.indexOf(target)
        // Stable item keys keep the scroll anchor; only scroll if the focused title moved off screen.
        val movedOffScreen = gridState.layoutInfo.visibleItemsInfo.none { it.key == target }
        if (movedOffScreen) gridState.scrollToItem(index)
        if (needsFocus || movedOffScreen) {
            withFrameNanos { }
            try {
                focus.requesters[target]?.requestFocus()
            } catch (_: IllegalStateException) {
                // The route may have left composition during a refresh.
            }
        }
    }
    return focus
}

internal fun trendingFocusTarget(focusedId: Int?, previousIndex: Int, ids: List<Int>): Int? =
    focusedId?.takeIf { it in ids } ?: ids.getOrNull(previousIndex.coerceIn(0, (ids.size - 1).coerceAtLeast(0)))
