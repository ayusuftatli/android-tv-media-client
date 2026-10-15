package tv.ororo.app

import androidx.media3.common.TrackSelectionParameters
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.ororo.app.ui.player.buildSubtitleTrackSelectionParameters
import tv.ororo.app.ui.player.preferStereoAudio

class PlayerAudioSelectionTest {

    @Test
    fun `audio selection is capped at stereo so broken surround renditions are not preferred`() {
        val updated = preferStereoAudio(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)

        assertEquals(2, updated.maxAudioChannelCount)
    }

    @Test
    fun `subtitle changes keep the stereo audio preference`() {
        val updated = buildSubtitleTrackSelectionParameters(
            current = preferStereoAudio(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT),
            subtitlesEnabled = false
        )

        assertEquals(2, updated.maxAudioChannelCount)
    }
}
