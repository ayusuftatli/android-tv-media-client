package tv.ororo.app.ui.player

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.delay
import tv.ororo.app.BuildConfig

private const val PLAYBACK_LOG_TAG = "OroroPlayback"

private data class PlaybackIssue(val message: String, val details: String, val fatal: Boolean)

/** Keeps failures in Compose state so they cannot be lost before PlayerView is attached. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlaybackDiagnostics(player: ExoPlayer, contentType: String, contentId: Int, onExit: () -> Unit) {
    val monitor = remember(player, contentType, contentId) { PlaybackStallMonitor() }
    val diagnostics = remember(player, contentType, contentId) { PlaybackDiagnosticDetails() }
    var issue by remember(player, contentType, contentId) { mutableStateOf<PlaybackIssue?>(null) }

    fun details(): String = "Content: $contentType/$contentId\n" + diagnostics.describe(player)

    fun reportError(error: PlaybackException) {
        val report = details() + "\nCause: ${playbackErrorCauses(error)}"
        Log.e(PLAYBACK_LOG_TAG, "Playback failed: ${error.errorCodeName}\n$report")
        issue = PlaybackIssue("Playback failed (${error.errorCodeName}). Try restarting playback.", report, fatal = true)
    }

    DisposableEffect(player, contentType, contentId) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = reportError(error)

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                if (error == null && issue?.fatal == true) issue = null
            }

            override fun onTracksChanged(tracks: Tracks) {
                diagnostics.tracks = describePlaybackTracks(tracks)
                Log.i(PLAYBACK_LOG_TAG, "content=$contentType/$contentId\n${diagnostics.tracks}")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Seeking changes position without proving that playback has recovered.
                // Start a fresh grace period, including when seeking to the same position.
                monitor.reset()
                if (issue?.fatal == false) issue = null
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                ) {
                    monitor.reset()
                }
                if (BuildConfig.DEBUG) Log.d(PLAYBACK_LOG_TAG, "content=$contentType/$contentId ${playbackStateSummary(player)}")
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                diagnostics.audioDecoder = decoderName
                Log.i(PLAYBACK_LOG_TAG, "content=$contentType/$contentId audioDecoder=$decoderName")
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                diagnostics.videoDecoder = decoderName
                Log.i(PLAYBACK_LOG_TAG, "content=$contentType/$contentId videoDecoder=$decoderName")
            }

            private fun recordRendererError(component: String, error: Exception) {
                diagnostics.rendererError = "$component: ${playbackErrorCauses(error)}"
                Log.w(PLAYBACK_LOG_TAG, "content=$contentType/$contentId ${diagnostics.rendererError}")
            }

            // These callbacks also cover recoverable failures that do not call onPlayerError.
            override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) =
                recordRendererError("Audio output", audioSinkError)

            override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) =
                recordRendererError("Audio decoder", audioCodecError)

            override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) =
                recordRendererError("Video decoder", videoCodecError)
        }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        player.playerError?.let(::reportError)
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
        }
    }

    LaunchedEffect(player, contentType, contentId) {
        while (true) {
            val stall = monitor.sample(
                nowMs = SystemClock.elapsedRealtime(),
                positionMs = player.currentPosition,
                playbackState = player.playbackState,
                playWhenReady = player.playWhenReady,
                suppressionReason = player.playbackSuppressionReason,
                hasError = player.playerError != null
            )
            if (stall != null && issue == null) {
                val message = when (stall) {
                    PlaybackStall.BUFFERING -> "The stream has been buffering for 30 seconds without advancing. Try restarting playback."
                    PlaybackStall.NOT_ADVANCING -> "Playback has not advanced for 15 seconds. The cause is not yet known. Try restarting playback."
                }
                val report = details()
                Log.w(PLAYBACK_LOG_TAG, "Playback stalled: $stall\n$report")
                issue = PlaybackIssue(message, report, fatal = false)
            } else if (stall == null && issue?.fatal == false) {
                // Clear on progress, pause/suppression, or a fresh preparation/seek grace period.
                issue = null
            }
            delay(1_000)
        }
    }

    issue?.let { currentIssue ->
        val retryFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { retryFocus.requestFocus() }
        AlertDialog(
            onDismissRequest = onExit,
            title = { Text(if (currentIssue.fatal) "Playback failed" else "Playback stalled") },
            text = {
                Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text(currentIssue.message)
                    Text("\n${currentIssue.details}", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.focusRequester(retryFocus),
                    onClick = {
                        issue = null
                        monitor.reset()
                        diagnostics.rendererError = null
                        // stop/prepare rebuilds the renderers without replacing the media item,
                        // so the position and subtitle/track choices survive a retry.
                        player.stop()
                        player.prepare()
                        player.play()
                    }
                ) { Text("Retry playback") }
            },
            dismissButton = { TextButton(onClick = onExit) { Text("Exit player") } }
        )
    }
}

private class PlaybackDiagnosticDetails {
    var tracks: String = "Audio/video tracks not available yet."
    var audioDecoder: String? = null
    var videoDecoder: String? = null
    var rendererError: String? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    fun describe(player: ExoPlayer): String = buildString {
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        if (player.videoFormat != null || player.audioFormat != null) {
            appendLine("Video input: ${describePlaybackFormat(player.videoFormat)}")
            appendLine("Audio input: ${describePlaybackFormat(player.audioFormat)}")
        } else {
            appendLine(tracks)
        }
        appendLine("Video decoder: ${videoDecoder ?: "not reported"}")
        appendLine("Audio decoder: ${audioDecoder ?: "not reported (may use passthrough)"}")
        append(playbackStateSummary(player))
        rendererError?.let { append("\nLast renderer error: $it") }
    }
}

private fun describePlaybackFormat(format: Format?): String {
    if (format == null) return "not reported"
    val dimensions = if (format.width > 0) "${format.width}x${format.height}" else "${format.channelCount}ch ${format.sampleRate}Hz"
    return "${format.sampleMimeType} ${format.codecs ?: ""} $dimensions"
}

private fun playbackStateSummary(player: Player): String {
    val state = when (player.playbackState) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "unknown"
    }
    return "state=$state playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying} " +
        "suppression=${player.playbackSuppressionReason} position=${player.currentPosition}ms " +
        "buffered=${player.totalBufferedDuration}ms error=${player.playerError?.errorCodeName ?: "none"}"
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun describePlaybackTracks(tracks: Tracks): String =
    listOf(C.TRACK_TYPE_VIDEO to "Video", C.TRACK_TYPE_AUDIO to "Audio").joinToString("\n") { (type, label) ->
        val groups = tracks.groups.filter { it.type == type }
        val selected = groups.flatMap { group ->
            (0 until group.length).filter { group.isTrackSelected(it) }.map { group to it }
        }
        val reported = selected.ifEmpty {
            groups.flatMap { group -> (0 until group.length).map { group to it } }.take(2)
        }
        if (reported.isEmpty()) "$label: no track" else reported.joinToString("\n") { (group, index) ->
            "$label: ${describePlaybackFormat(group.getTrackFormat(index))} " +
                "selected=${group.isTrackSelected(index)} supported=${group.isTrackSupported(index)}"
        }
    }
