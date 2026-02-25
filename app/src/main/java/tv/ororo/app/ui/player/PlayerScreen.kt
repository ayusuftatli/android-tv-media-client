package tv.ororo.app.ui.player

import android.net.Uri
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    contentType: String,
    contentId: Int,
    onBack: () -> Unit,
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
                var subtitleMenuExpanded by remember { mutableStateOf(false) }

                BackHandler {
                    val playerView = playerViewRef
                    if (playerView != null && shouldHandleBackAsHideControls(playerView.isControllerFullyVisible)) {
                        playerView.hideController()
                        playerView.requestFocus()
                    } else {
                        onBack()
                    }
                }

                LaunchedEffect(uiState.streamUrl, uiState.selectedSubtitleLang, uiState.subtitlesEnabled) {
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

                    val trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !uiState.subtitlesEnabled)
                        .setPreferredTextLanguage(uiState.selectedSubtitleLang)
                        .build()
                    exoPlayer.trackSelectionParameters = trackSelectionParameters

                    val mediaItem = MediaItem.Builder()
                        .setUri(uiState.streamUrl)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()

                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }

                LaunchedEffect(exoPlayer, uiState.streamUrl) {
                    while (true) {
                        delay(5_000)
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
                            if (playbackState == Player.STATE_ENDED) {
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
                                player = exoPlayer
                                useController = true
                                isFocusable = true
                                isFocusableInTouchMode = true
                                controllerAutoShow = true
                                controllerHideOnTouch = false
                                keepScreenOn = true
                                post { requestFocus() }
                                setOnKeyListener { view, keyCode, event ->
                                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                                    handlePlayerKeyDown(
                                        keyCode = keyCode,
                                        exoPlayer = exoPlayer,
                                        playerView = view as PlayerView,
                                        onBack = onBack
                                    )
                                }
                            }
                        },
                        update = { playerView ->
                            playerViewRef = playerView
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(playerFocusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                val nativeEvent = keyEvent.nativeKeyEvent
                                if (nativeEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                val playerView = playerViewRef ?: return@onPreviewKeyEvent false
                                handlePlayerKeyDown(
                                    keyCode = nativeEvent.keyCode,
                                    exoPlayer = exoPlayer,
                                    playerView = playerView,
                                    onBack = onBack
                                )
                            }
                    )

                    if (uiState.subtitles.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Button(onClick = { subtitleMenuExpanded = true }) {
                                Text(
                                    text = if (uiState.subtitlesEnabled && uiState.selectedSubtitleLang != null) {
                                        "Subtitles: ${uiState.selectedSubtitleLang}"
                                    } else {
                                        "Subtitles: Off"
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = subtitleMenuExpanded,
                                onDismissRequest = { subtitleMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Off") },
                                    onClick = {
                                        viewModel.setSubtitleSelection(null)
                                        subtitleMenuExpanded = false
                                    }
                                )
                                uiState.subtitles.forEach { subtitle ->
                                    DropdownMenuItem(
                                        text = { Text(subtitle.lang) },
                                        onClick = {
                                            viewModel.setSubtitleSelection(subtitle.lang)
                                            subtitleMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
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
    onBack: () -> Unit
): Boolean {
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            exoPlayer.seekBack()
            return true
        }

        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            exoPlayer.seekForward()
            return true
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

    if (keyCode !in controlKeys) return false

    if (!playerView.isControllerFullyVisible) {
        playerView.showController()
        playerView.requestFocus()
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
        return true
    }
    return false
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
