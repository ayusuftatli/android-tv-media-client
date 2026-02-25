package tv.ororo.app.ui.player

import android.net.Uri
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

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

                // Build media item with subtitles
                LaunchedEffect(uiState.streamUrl, uiState.selectedSubtitleLang) {
                    val subtitleConfigs = uiState.subtitles.map { subtitle ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage(subtitle.lang)
                            .setLabel(subtitle.lang)
                            .setSelectionFlags(
                                if (subtitle.lang == uiState.selectedSubtitleLang) C.SELECTION_FLAG_DEFAULT
                                else 0
                            )
                            .build()
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(uiState.streamUrl)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()

                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }

                DisposableEffect(Unit) {
                    onDispose {
                        exoPlayer.release()
                    }
                }

                LaunchedEffect(Unit) {
                    playerFocusRequester.requestFocus()
                }

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
                            post {
                                requestFocus()
                            }
                            setOnKeyListener { view, keyCode, event ->
                                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        exoPlayer.seekBack()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        exoPlayer.seekForward()
                                        true
                                    }
                                    else -> if (keyCode in controlKeys) {
                                        if (!(view as PlayerView).isControllerFullyVisible) {
                                            view.showController()
                                            view.requestFocus()
                                            view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
                                        }
                                        false
                                    } else {
                                        false
                                    }
                                }
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
                            val keyCode = nativeEvent.keyCode

                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    exoPlayer.seekBack()
                                    return@onPreviewKeyEvent true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    exoPlayer.seekForward()
                                    return@onPreviewKeyEvent true
                                }
                            }

                            if (keyCode !in controlKeys) return@onPreviewKeyEvent false

                            val playerView = playerViewRef ?: return@onPreviewKeyEvent false
                            if (!playerView.isControllerFullyVisible) {
                                playerView.showController()
                                playerView.requestFocus()
                                playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
                                return@onPreviewKeyEvent true
                            }
                            false
                        }
                )
            }
        }
    }
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
