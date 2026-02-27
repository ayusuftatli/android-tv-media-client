package tv.ororo.app.ui.player

import android.net.Uri
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import tv.ororo.app.R

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    contentType: String,
    contentId: Int,
    onBack: () -> Unit,
    onNextEpisode: (Int) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val playerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(contentType, contentId) {
        viewModel.loadContent(contentType, contentId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    color = Color(0xFF6C63FF),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    color = Color(0xFFFF6B6B),
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            uiState.streamUrl != null -> {
                val exoPlayer = remember {
                    ExoPlayer.Builder(context).build()
                }
                var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
                var nextEpisodeButtonRef by remember { mutableStateOf<AppCompatButton?>(null) }
                var stopPlaybackButtonRef by remember { mutableStateOf<AppCompatImageButton?>(null) }
                var showStopPlaybackDialog by remember(contentType, contentId) { mutableStateOf(false) }
                var shouldPersistPlaybackProgress by remember(contentType, contentId) { mutableStateOf(true) }
                val nextEpisode = uiState.nextEpisode
                val hasNextEpisode = contentType == "episode" && nextEpisode != null
                val latestHasNextEpisode by rememberUpdatedState(hasNextEpisode)
                val latestNextEpisode by rememberUpdatedState(nextEpisode)
                val latestOnNextEpisode by rememberUpdatedState(onNextEpisode)
                val latestContentType by rememberUpdatedState(contentType)
                val latestContentId by rememberUpdatedState(contentId)

                fun stopPlaybackAndExit() {
                    shouldPersistPlaybackProgress = false
                    val durationMs = exoPlayer.duration
                    val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    viewModel.onStopPlaybackRequested(
                        contentType = latestContentType,
                        contentId = latestContentId,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onCompleted = onBack
                    )
                    exoPlayer.playWhenReady = false
                    exoPlayer.stop()
                }

                BackHandler {
                    if (showStopPlaybackDialog) {
                        showStopPlaybackDialog = false
                        return@BackHandler
                    }
                    val playerView = playerViewRef
                    if (playerView != null && shouldHandleBackAsHideControls(playerView.isControllerFullyVisible)) {
                        playerView.hideController()
                        playerView.requestFocus()
                    } else {
                        onBack()
                    }
                }

                LaunchedEffect(uiState.selectedSubtitleLang, uiState.subtitlesEnabled) {
                    val trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !uiState.subtitlesEnabled)
                        .setPreferredTextLanguage(uiState.selectedSubtitleLang)
                        .build()
                    exoPlayer.trackSelectionParameters = trackSelectionParameters
                }

                LaunchedEffect(uiState.streamUrl, uiState.subtitles) {
                    val subtitleConfigs = uiState.subtitles.map { subtitle ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                            .setMimeType(inferSubtitleMimeType(subtitle.url))
                            .setLanguage(subtitle.lang)
                            .setLabel(subtitle.lang)
                            .setSelectionFlags(
                                if (uiState.subtitlesEnabled && subtitle.lang == uiState.selectedSubtitleLang) {
                                    C.SELECTION_FLAG_DEFAULT
                                } else {
                                    0
                                }
                            )
                            .build()
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(uiState.streamUrl)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()

                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    if (uiState.resumePositionMs > 0L) {
                        exoPlayer.seekTo(uiState.resumePositionMs)
                    }
                    exoPlayer.playWhenReady = true
                }

                LaunchedEffect(exoPlayer, uiState.streamUrl) {
                    while (true) {
                        delay(5_000)
                        if (!shouldPersistPlaybackProgress) continue
                        val durationMs = exoPlayer.duration
                        if (durationMs > 0L) {
                            viewModel.onPlaybackProgress(
                                contentType = contentType,
                                contentId = contentId,
                                positionMs = exoPlayer.currentPosition,
                                durationMs = durationMs,
                                isEnded = false
                            )
                        }
                    }
                }

                DisposableEffect(exoPlayer) {
                    val listener = object : Player.Listener {
                        override fun onTracksChanged(tracks: Tracks) {
                            val selectedLanguage = extractSelectedTextLanguage(tracks)
                            val subtitlesDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                            viewModel.onSubtitleTrackChanged(selectedLanguage, subtitlesDisabled)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED && shouldPersistPlaybackProgress) {
                                val durationMs = exoPlayer.duration
                                if (durationMs > 0L) {
                                    viewModel.onPlaybackProgress(
                                        contentType = contentType,
                                        contentId = contentId,
                                        positionMs = exoPlayer.currentPosition,
                                        durationMs = durationMs,
                                        isEnded = true
                                    )
                                }
                            }
                        }
                    }

                    exoPlayer.addListener(listener)
                    onDispose {
                        if (shouldPersistPlaybackProgress) {
                            val durationMs = exoPlayer.duration
                            if (durationMs > 0L) {
                                viewModel.onPlaybackProgress(
                                    contentType = contentType,
                                    contentId = contentId,
                                    positionMs = exoPlayer.currentPosition,
                                    durationMs = durationMs,
                                    isEnded = exoPlayer.playbackState == Player.STATE_ENDED
                                )
                            }
                        }
                        exoPlayer.removeListener(listener)
                        exoPlayer.release()
                    }
                }

                LaunchedEffect(Unit) {
                    playerFocusRequester.requestFocus()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                var didApplyInitialControllerFocus = false
                                fun applyInitialControllerFocusIfNeeded() {
                                    if (didApplyInitialControllerFocus || !isControllerFullyVisible) return
                                    focusDefaultControl(this)
                                    updateProgressBarSelectionState(this)
                                    didApplyInitialControllerFocus = true
                                }

                                val stopPlaybackButton = createStopPlaybackButton(ctx)
                                attachStopPlaybackButton(this, stopPlaybackButton, ctx)
                                stopPlaybackButtonRef = stopPlaybackButton
                                stopPlaybackButton.setOnClickListener {
                                    showStopPlaybackDialog = true
                                }

                                val nextEpisodeButton = createNextEpisodeButton(ctx)
                                attachNextEpisodeButton(this, nextEpisodeButton, ctx)
                                nextEpisodeButtonRef = nextEpisodeButton
                                nextEpisodeButton.setOnClickListener {
                                    latestNextEpisode?.let { latestOnNextEpisode(it.id) }
                                }

                                player = exoPlayer
                                useController = true
                                setShowSubtitleButton(true)
                                isFocusable = true
                                isFocusableInTouchMode = true
                                controllerAutoShow = true
                                controllerHideOnTouch = false
                                keepScreenOn = true
                                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                                    if (visibility == View.VISIBLE) {
                                        post { applyInitialControllerFocusIfNeeded() }
                                    }
                                    updateStopPlaybackButtonVisibility(
                                        button = stopPlaybackButton,
                                        isControllerVisible = visibility == View.VISIBLE
                                    )
                                    updateNextEpisodeButtonVisibility(
                                        button = nextEpisodeButton,
                                        isControllerVisible = visibility == View.VISIBLE,
                                        hasNextEpisode = latestHasNextEpisode
                                    )
                                })
                                post {
                                    requestFocus()
                                    applyInitialControllerFocusIfNeeded()
                                    attachStopPlaybackButton(this, stopPlaybackButton, ctx)
                                    attachNextEpisodeButton(this, nextEpisodeButton, ctx)
                                    ensurePlaybackActionFocusLinks(
                                        playerView = this,
                                        stopPlaybackButton = stopPlaybackButton,
                                        nextEpisodeButton = nextEpisodeButton,
                                        hasNextEpisode = latestHasNextEpisode
                                    )
                                    updateStopPlaybackButtonVisibility(
                                        button = stopPlaybackButton,
                                        isControllerVisible = isControllerFullyVisible
                                    )
                                    updateNextEpisodeButtonVisibility(
                                        button = nextEpisodeButton,
                                        isControllerVisible = isControllerFullyVisible,
                                        hasNextEpisode = latestHasNextEpisode
                                    )
                                }
                                setOnKeyListener { view, keyCode, event ->
                                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                                    val handled = handlePlayerKeyDown(
                                        keyCode = keyCode,
                                        exoPlayer = exoPlayer,
                                        playerView = view as PlayerView,
                                        onBack = onBack,
                                        hasNextEpisode = hasNextEpisode,
                                        onNextEpisode = nextEpisode?.let { { onNextEpisode(it.id) } },
                                        onStopRequested = { showStopPlaybackDialog = true }
                                    )
                                    updateProgressBarSelectionState(view as PlayerView)
                                    handled
                                }
                                post {
                                    val progressBar = findViewById<View>(androidx.media3.ui.R.id.exo_progress)
                                    progressBar?.onFocusChangeListener =
                                        View.OnFocusChangeListener { _, _ ->
                                            updateProgressBarSelectionState(this)
                                        }
                                    updateProgressBarSelectionState(this)
                                    ensurePlaybackActionFocusLinks(
                                        playerView = this,
                                        stopPlaybackButton = stopPlaybackButton,
                                        nextEpisodeButton = nextEpisodeButton,
                                        hasNextEpisode = latestHasNextEpisode
                                    )
                                }
                            }
                        },
                        update = { playerView ->
                            playerViewRef = playerView
                            updateProgressBarSelectionState(playerView)
                            stopPlaybackButtonRef?.let { stopButton ->
                                stopButton.setOnClickListener {
                                    showStopPlaybackDialog = true
                                }
                                updateStopPlaybackButtonVisibility(
                                    button = stopButton,
                                    isControllerVisible = playerView.isControllerFullyVisible
                                )
                                attachStopPlaybackButton(playerView, stopButton, playerView.context)
                            }
                            nextEpisodeButtonRef?.let { nextButton ->
                                nextButton.text = buildNextEpisodeButtonText(nextEpisode)
                                nextButton.setOnClickListener {
                                    nextEpisode?.let { onNextEpisode(it.id) }
                                }
                                updateNextEpisodeButtonVisibility(
                                    button = nextButton,
                                    isControllerVisible = playerView.isControllerFullyVisible,
                                    hasNextEpisode = hasNextEpisode
                                )
                                attachNextEpisodeButton(playerView, nextButton, playerView.context)
                                ensurePlaybackActionFocusLinks(
                                    playerView = playerView,
                                    stopPlaybackButton = stopPlaybackButtonRef,
                                    nextEpisodeButton = nextButton,
                                    hasNextEpisode = hasNextEpisode
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(playerFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                val nativeEvent = keyEvent.nativeKeyEvent
                                if (nativeEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                val playerView = playerViewRef ?: return@onPreviewKeyEvent false
                                val handled = handlePlayerKeyDown(
                                    keyCode = nativeEvent.keyCode,
                                    exoPlayer = exoPlayer,
                                    playerView = playerView,
                                    onBack = onBack,
                                    hasNextEpisode = hasNextEpisode,
                                    onNextEpisode = nextEpisode?.let { { onNextEpisode(it.id) } },
                                    onStopRequested = { showStopPlaybackDialog = true }
                                )
                                updateProgressBarSelectionState(playerView)
                                handled
                            }
                    )

                    if (showStopPlaybackDialog) {
                        AlertDialog(
                            onDismissRequest = { showStopPlaybackDialog = false },
                            title = { Text(text = "Stop playback?") },
                            text = {
                                Text(
                                    text = "This will exit player. Watched under 95% will be reset to never watched."
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showStopPlaybackDialog = false
                                        stopPlaybackAndExit()
                                    }
                                ) {
                                    Text("Stop & Exit")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showStopPlaybackDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun handlePlayerKeyDown(
    keyCode: Int,
    exoPlayer: ExoPlayer,
    playerView: PlayerView,
    onBack: () -> Unit,
    hasNextEpisode: Boolean,
    onNextEpisode: (() -> Unit)?,
    onStopRequested: () -> Unit
): Boolean {
    when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> {
            if (hasNextEpisode) {
                onNextEpisode?.invoke()
                return true
            }
        }

        KeyEvent.KEYCODE_MEDIA_STOP -> {
            onStopRequested()
            return true
        }

        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (isProgressBarFocused(playerView)) {
                exoPlayer.seekBack()
                return true
            }
        }

        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (isProgressBarFocused(playerView)) {
                exoPlayer.seekForward()
                return true
            }
        }

        KeyEvent.KEYCODE_BACK -> {
            if (shouldHandleBackAsHideControls(playerView.isControllerFullyVisible)) {
                playerView.hideController()
                playerView.requestFocus()
                return true
            }
            onBack()
            return true
        }
    }

    val shouldTogglePlayPause =
        keyCode in playPauseToggleKeys ||
            (
                keyCode in playPauseWhenControllerHiddenKeys &&
                    (!playerView.isControllerFullyVisible || isProgressBarFocused(playerView))
            )

    if (shouldTogglePlayPause) {
        val wasControllerVisible = playerView.isControllerFullyVisible
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        if (!wasControllerVisible) {
            playerView.showController()
            playerView.requestFocus()
            focusDefaultControl(playerView)
        }
        return true
    }

    if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
        if (!exoPlayer.isPlaying) exoPlayer.play()
        if (!playerView.isControllerFullyVisible) {
            playerView.showController()
        }
        playerView.requestFocus()
        return true
    }

    if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
        if (exoPlayer.isPlaying) exoPlayer.pause()
        if (!playerView.isControllerFullyVisible) {
            playerView.showController()
        }
        playerView.requestFocus()
        return true
    }

    if (keyCode !in controlKeys) return false

    if (!playerView.isControllerFullyVisible) {
        playerView.showController()
        playerView.requestFocus()
        focusDefaultControl(playerView)
        return true
    }
    return false
}

private fun focusDefaultControl(playerView: PlayerView) {
    val progressBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
    if (progressBar?.requestFocus() == true) return
    playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
}

private fun isProgressBarFocused(playerView: PlayerView): Boolean {
    val progressBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress) ?: return false
    val focusedView = playerView.findFocus() ?: return false
    return isDescendantOrSame(focusedView, progressBar)
}

private fun isDescendantOrSame(view: View, potentialAncestor: View): Boolean {
    var current: View? = view
    while (current != null) {
        if (current === potentialAncestor) return true
        current = current.parent as? View
    }
    return false
}

private fun updateProgressBarSelectionState(playerView: PlayerView) {
    val progressBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress) ?: return
    val isSelected = isProgressBarFocused(playerView)

    progressBar.alpha = if (isSelected) 1.0f else 0.75f

    val timeBar = progressBar as? DefaultTimeBar ?: return
    if (isSelected) {
        timeBar.setPlayedColor(0xFF22C55E.toInt())
        timeBar.setScrubberColor(0xFF22C55E.toInt())
    } else {
        timeBar.setPlayedColor(0xFFFFFFFF.toInt())
        timeBar.setScrubberColor(0xFFFFFFFF.toInt())
    }
}

internal fun shouldHandleBackAsHideControls(isControllerVisible: Boolean): Boolean {
    return isControllerVisible
}

internal fun inferSubtitleMimeType(url: String): String {
    val normalized = url.substringBefore('?').lowercase()
    return when {
        normalized.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        normalized.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        else -> MimeTypes.TEXT_UNKNOWN
    }
}

private fun extractSelectedTextLanguage(tracks: Tracks): String? {
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (i in 0 until group.length) {
            if (group.isTrackSelected(i)) {
                return group.getTrackFormat(i).language
            }
        }
    }
    return null
}

private val controlKeys = setOf(
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_SPACE,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE
)

private val playPauseToggleKeys = setOf(
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
)

private val playPauseWhenControllerHiddenKeys = setOf(
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_SPACE
)

private const val STOP_PLAYBACK_BUTTON_TAG = "stop_playback_button"
private const val NEXT_EPISODE_BUTTON_TAG = "next_episode_button"

private fun createStopPlaybackButton(context: android.content.Context): AppCompatImageButton {
    return AppCompatImageButton(context).apply {
        tag = STOP_PLAYBACK_BUTTON_TAG
        id = View.generateViewId()
        setImageResource(R.drawable.ic_stop_square_24)
        contentDescription = "Stop playback"
        isFocusable = true
        isFocusableInTouchMode = true
        setOnFocusChangeListener { v, hasFocus ->
            val button = v as AppCompatImageButton
            button.imageTintList = ColorStateList.valueOf(
                if (hasFocus) 0xFF22C55E.toInt() else 0xFFFFFFFF.toInt()
            )
        }
        visibility = View.GONE
    }
}

private fun attachStopPlaybackButton(
    playerView: PlayerView,
    stopPlaybackButton: AppCompatImageButton,
    context: android.content.Context
) {
    applyStopPlaybackButtonStyleFromPlaybackControls(playerView, stopPlaybackButton, context)
    stopPlaybackButton.isEnabled = true
    stopPlaybackButton.isClickable = true

    val timeGroup = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_time) as? ViewGroup
    if (timeGroup != null) {
        if (stopPlaybackButton.parent !== timeGroup) {
            (stopPlaybackButton.parent as? ViewGroup)?.removeView(stopPlaybackButton)
            val params: ViewGroup.LayoutParams = if (timeGroup is LinearLayout) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(context, 10)
                    gravity = Gravity.CENTER_VERTICAL
                }
            } else {
                ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(context, 10)
                }
            }
            timeGroup.addView(stopPlaybackButton, params)
        }
        return
    }

    if (stopPlaybackButton.parent == null) {
        playerView.addView(
            stopPlaybackButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                marginEnd = dpToPx(context, 120)
                bottomMargin = dpToPx(context, 72)
            }
        )
    }
}

private fun applyStopPlaybackButtonStyleFromPlaybackControls(
    playerView: PlayerView,
    stopPlaybackButton: AppCompatImageButton,
    context: android.content.Context
) {
    val styleSource = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)

    if (styleSource != null) {
        stopPlaybackButton.background = styleSource.background?.constantState?.newDrawable()?.mutate()
        stopPlaybackButton.minimumWidth = styleSource.minimumWidth
        stopPlaybackButton.minimumHeight = styleSource.minimumHeight
        stopPlaybackButton.setPadding(
            styleSource.paddingLeft,
            styleSource.paddingTop,
            styleSource.paddingRight,
            styleSource.paddingBottom
        )
        stopPlaybackButton.imageTintList = ColorStateList.valueOf(
            if (stopPlaybackButton.hasFocus()) 0xFF22C55E.toInt() else 0xFFFFFFFF.toInt()
        )
        return
    }

    stopPlaybackButton.setBackgroundColor(0x66000000)
    stopPlaybackButton.setPadding(
        dpToPx(context, 8),
        dpToPx(context, 8),
        dpToPx(context, 8),
        dpToPx(context, 8)
    )
    stopPlaybackButton.imageTintList = ColorStateList.valueOf(
        if (stopPlaybackButton.hasFocus()) 0xFF22C55E.toInt() else 0xFFFFFFFF.toInt()
    )
}

private fun updateStopPlaybackButtonVisibility(
    button: View,
    isControllerVisible: Boolean
) {
    button.visibility = if (isControllerVisible) View.VISIBLE else View.GONE
}

private fun createNextEpisodeButton(context: android.content.Context): AppCompatButton {
    return AppCompatButton(context).apply {
        tag = NEXT_EPISODE_BUTTON_TAG
        id = View.generateViewId()
        text = "Next Episode"
        isAllCaps = false
        isFocusable = true
        isFocusableInTouchMode = true
        setOnFocusChangeListener { v, hasFocus ->
            val button = v as AppCompatButton
            button.setTextColor(if (hasFocus) 0xFF22C55E.toInt() else 0xFFFFFFFF.toInt())
        }
        visibility = View.GONE
    }
}

private fun attachNextEpisodeButton(
    playerView: PlayerView,
    nextEpisodeButton: AppCompatButton,
    context: android.content.Context
) {
    applyNextEpisodeButtonStyleFromPlaybackControls(playerView, nextEpisodeButton, context)
    nextEpisodeButton.isEnabled = true
    nextEpisodeButton.isClickable = true

    val timeGroup = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_time) as? ViewGroup
    if (timeGroup != null) {
        if (nextEpisodeButton.parent !== timeGroup) {
            (nextEpisodeButton.parent as? ViewGroup)?.removeView(nextEpisodeButton)
            val params: ViewGroup.LayoutParams = if (timeGroup is LinearLayout) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(context, 10)
                    gravity = Gravity.CENTER_VERTICAL
                }
            } else {
                ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(context, 10)
                }
            }
            timeGroup.addView(nextEpisodeButton, params)
        }
        return
    }

    if (nextEpisodeButton.parent == null) {
        playerView.addView(
            nextEpisodeButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                marginEnd = dpToPx(context, 24)
                bottomMargin = dpToPx(context, 72)
            }
        )
    }
}

private fun applyNextEpisodeButtonStyleFromPlaybackControls(
    playerView: PlayerView,
    nextEpisodeButton: AppCompatButton,
    context: android.content.Context
) {
    val styleSource = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_subtitle)
        ?: playerView.findViewById(androidx.media3.ui.R.id.exo_settings)

    if (styleSource is android.widget.TextView) {
        nextEpisodeButton.background = styleSource.background?.constantState?.newDrawable()?.mutate()
        nextEpisodeButton.setTextColor(
            if (nextEpisodeButton.hasFocus()) 0xFF22C55E.toInt() else styleSource.currentTextColor
        )
        nextEpisodeButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, styleSource.textSize)
        nextEpisodeButton.typeface = styleSource.typeface
        nextEpisodeButton.setPadding(
            styleSource.paddingLeft,
            styleSource.paddingTop,
            styleSource.paddingRight,
            styleSource.paddingBottom
        )
        return
    }

    nextEpisodeButton.setTextColor(0xFFFFFFFF.toInt())
    nextEpisodeButton.setBackgroundColor(0x66000000)
    nextEpisodeButton.setPadding(
        dpToPx(context, 12),
        dpToPx(context, 6),
        dpToPx(context, 12),
        dpToPx(context, 6)
    )
}

private fun updateNextEpisodeButtonVisibility(
    button: View,
    isControllerVisible: Boolean,
    hasNextEpisode: Boolean
) {
    button.visibility = if (isControllerVisible && hasNextEpisode) View.VISIBLE else View.GONE
}

private fun ensurePlaybackActionFocusLinks(
    playerView: PlayerView,
    stopPlaybackButton: View?,
    nextEpisodeButton: View,
    hasNextEpisode: Boolean
) {
    val progressBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
    val duration = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_duration)
    val playPause = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
    val stopButton = stopPlaybackButton ?: return

    if (!hasNextEpisode) {
        progressBar?.nextFocusDownId = stopButton.id
        duration?.nextFocusRightId = stopButton.id
        playPause?.nextFocusUpId = View.NO_ID
        stopButton.nextFocusUpId = androidx.media3.ui.R.id.exo_progress
        stopButton.nextFocusDownId = androidx.media3.ui.R.id.exo_progress
        stopButton.nextFocusLeftId = androidx.media3.ui.R.id.exo_duration
        stopButton.nextFocusRightId = View.NO_ID
        nextEpisodeButton.nextFocusUpId = View.NO_ID
        nextEpisodeButton.nextFocusDownId = View.NO_ID
        nextEpisodeButton.nextFocusLeftId = View.NO_ID
        nextEpisodeButton.nextFocusRightId = View.NO_ID
        return
    }

    progressBar?.nextFocusDownId = stopButton.id
    duration?.nextFocusRightId = stopButton.id
    playPause?.nextFocusUpId = View.NO_ID
    stopButton.nextFocusUpId = androidx.media3.ui.R.id.exo_progress
    stopButton.nextFocusDownId = androidx.media3.ui.R.id.exo_progress
    stopButton.nextFocusLeftId = androidx.media3.ui.R.id.exo_duration
    stopButton.nextFocusRightId = nextEpisodeButton.id
    nextEpisodeButton.nextFocusUpId = androidx.media3.ui.R.id.exo_progress
    nextEpisodeButton.nextFocusDownId = androidx.media3.ui.R.id.exo_progress
    nextEpisodeButton.nextFocusLeftId = stopButton.id
    nextEpisodeButton.nextFocusRightId = View.NO_ID
}

private fun buildNextEpisodeButtonText(nextEpisode: NextEpisodeUi?): String {
    if (nextEpisode == null) return "Next Episode"
    return "Next ${nextEpisode.label}"
}

private fun dpToPx(context: android.content.Context, dp: Int): Int {
    return (context.resources.displayMetrics.density * dp).toInt()
}
