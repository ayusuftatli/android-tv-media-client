package tv.ororo.app.ui.player

import androidx.media3.common.Player

internal enum class PlaybackStall {
    NOT_ADVANCING,
    BUFFERING
}

/** Uses elapsed time and position, since STATE_READY/isPlaying can stay true during a stall. */
internal class PlaybackStallMonitor {
    private var lastPositionMs: Long? = null
    private var waitingSinceMs: Long? = null
    private var lastState: Int? = null

    fun reset() {
        lastPositionMs = null
        waitingSinceMs = null
        lastState = null
    }

    fun sample(
        nowMs: Long,
        positionMs: Long,
        playbackState: Int,
        playWhenReady: Boolean,
        suppressionReason: Int,
        hasError: Boolean
    ): PlaybackStall? {
        if (!playWhenReady || hasError || suppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE ||
            (playbackState != Player.STATE_READY && playbackState != Player.STATE_BUFFERING)
        ) {
            reset()
            return null
        }

        if (lastPositionMs != positionMs || lastState != playbackState) {
            waitingSinceMs = nowMs
        }
        lastPositionMs = positionMs
        lastState = playbackState
        val waitingMs = nowMs - (waitingSinceMs ?: nowMs)
        return when {
            playbackState == Player.STATE_BUFFERING && waitingMs >= 30_000L -> PlaybackStall.BUFFERING
            playbackState == Player.STATE_READY && waitingMs >= 15_000L -> PlaybackStall.NOT_ADVANCING
            else -> null
        }
    }
}

/** Exception messages and stack traces may contain signed stream URLs or request headers. */
internal fun playbackErrorCauses(error: Throwable): String =
    generateSequence(error) { it.cause }.take(8).joinToString(" -> ") { it.javaClass.simpleName }
