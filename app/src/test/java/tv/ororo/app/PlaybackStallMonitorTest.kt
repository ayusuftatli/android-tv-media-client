package tv.ororo.app

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import tv.ororo.app.ui.player.PlaybackStall
import tv.ororo.app.ui.player.PlaybackStallMonitor
import tv.ororo.app.ui.player.playbackErrorCauses

class PlaybackStallMonitorTest {
    @Test
    fun `ready player with frozen position reports a stall without a player error`() {
        val monitor = PlaybackStallMonitor()
        assertNull(monitor.sampleAt(0))
        assertNull(monitor.sampleAt(14_999))
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(15_000))
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(20_000))
    }

    @Test
    fun `buffering allows a longer grace period and identifies waiting for data`() {
        val monitor = PlaybackStallMonitor()
        assertNull(monitor.sampleAt(0, state = Player.STATE_BUFFERING))
        assertNull(monitor.sampleAt(29_999, state = Player.STATE_BUFFERING))
        assertEquals(PlaybackStall.BUFFERING, monitor.sampleAt(30_000, state = Player.STATE_BUFFERING))
    }

    @Test
    fun `playback progress clears stall and restarts timeout`() {
        val monitor = PlaybackStallMonitor()
        monitor.sampleAt(0)
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(15_000))
        assertNull(monitor.sampleAt(16_000, position = 1_100))
        assertNull(monitor.sampleAt(30_999, position = 1_100))
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(31_000, position = 1_100))
    }

    @Test
    fun `even slow continuous playback does not trigger a stall`() {
        val monitor = PlaybackStallMonitor()
        for (second in 0L..60L) {
            assertNull(monitor.sampleAt(second * 1_000, position = second * 100))
        }
    }

    @Test
    fun `paused playback resets elapsed time before resuming`() {
        val monitor = PlaybackStallMonitor()
        monitor.sampleAt(0)
        assertNull(monitor.sampleAt(14_000, playWhenReady = false))
        assertNull(monitor.sampleAt(60_000, playWhenReady = false))
        assertNull(monitor.sampleAt(61_000))
        assertNull(monitor.sampleAt(75_999))
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(76_000))
    }

    @Test
    fun `suppressed playback is not treated as a codec stall`() {
        val monitor = PlaybackStallMonitor()
        monitor.sampleAt(0)
        assertNull(monitor.sampleAt(30_000, suppression = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS))
        assertNull(monitor.sampleAt(60_000))
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(75_000))
    }

    @Test
    fun `idle ended and fatal error states do not show additional stall warnings`() {
        for (state in listOf(Player.STATE_IDLE, Player.STATE_ENDED)) {
            val monitor = PlaybackStallMonitor()
            monitor.sampleAt(0)
            assertNull(monitor.sampleAt(30_000, state = state))
        }
        val monitor = PlaybackStallMonitor()
        monitor.sampleAt(0)
        assertNull(monitor.sampleAt(30_000, hasError = true))
        assertNull(monitor.sampleAt(60_000))
    }

    @Test
    fun `seeking forward or backward starts a new grace period`() {
        for (position in listOf(0L, 60_000L)) {
            val monitor = PlaybackStallMonitor()
            monitor.sampleAt(0)
            assertNull(monitor.sampleAt(14_000, position = position))
            assertNull(monitor.sampleAt(28_999, position = position))
            assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(29_000, position = position))
        }
    }

    @Test
    fun `retry or seek to same position can explicitly reset the monitor`() {
        val monitor = PlaybackStallMonitor()
        monitor.sampleAt(0)
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(15_000))
        monitor.reset()
        assertNull(monitor.sampleAt(30_000))
        assertNull(monitor.sampleAt(44_999))
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(45_000))
    }

    @Test
    fun `buffering to ready starts a fresh position progress timeout`() {
        val monitor = PlaybackStallMonitor()
        monitor.sampleAt(0, state = Player.STATE_BUFFERING)
        assertNull(monitor.sampleAt(29_000))
        assertNull(monitor.sampleAt(43_999))
        assertEquals(PlaybackStall.NOT_ADVANCING, monitor.sampleAt(44_000))
    }

    @Test
    fun `error diagnostics retain cause types without leaking signed URLs or credentials`() {
        val error = IllegalStateException(
            "Request https://example.test/stream?token=secret Authorization: password",
            IllegalArgumentException("https://example.test/audio?token=secret")
        )
        val report = playbackErrorCauses(error)
        assertEquals("IllegalStateException -> IllegalArgumentException", report)
        assertFalse(report.contains("secret"))
        assertFalse(report.contains("password"))
        assertFalse(report.contains("https://"))
    }

    private fun PlaybackStallMonitor.sampleAt(
        nowMs: Long,
        position: Long = 1_000,
        state: Int = Player.STATE_READY,
        playWhenReady: Boolean = true,
        suppression: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        hasError: Boolean = false
    ) = sample(nowMs, position, state, playWhenReady, suppression, hasError)
}
