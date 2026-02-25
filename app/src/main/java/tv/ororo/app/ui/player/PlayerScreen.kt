package tv.ororo.app.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
