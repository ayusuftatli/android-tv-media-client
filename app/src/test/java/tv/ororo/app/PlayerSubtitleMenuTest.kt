package tv.ororo.app

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.ororo.app.ui.player.buildSubtitleTrackSelectionParameters
import tv.ororo.app.ui.player.isSubtitleSelectionOff

class PlayerSubtitleMenuTest {

    @Test
    fun `enabled subtitles leave preferred language unset so none can deselect defaults`() {
        val updated = buildSubtitleTrackSelectionParameters(
            current = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT,
            subtitlesEnabled = true
        )

        assertTrue(updated.preferredTextLanguages.isEmpty())
        assertFalse(updated.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertEquals(0, updated.ignoredTextSelectionFlags)
    }

    @Test
    fun `disabled subtitles disable text tracks and retain only forced flags`() {
        val updated = buildSubtitleTrackSelectionParameters(
            current = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT,
            subtitlesEnabled = false
        )

        assertTrue(updated.preferredTextLanguages.isEmpty())
        assertTrue(updated.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertEquals(C.SELECTION_FLAG_FORCED.inv(), updated.ignoredTextSelectionFlags)
    }

    @Test
    fun `none selection is off when subtitle tracks remain available`() {
        assertTrue(
            isSubtitleSelectionOff(
                selectedLanguage = null,
                isTextTrackDisabled = false,
                hasSelectableTextTracks = true
            )
        )
    }

    @Test
    fun `selected subtitle language remains enabled`() {
        assertFalse(
            isSubtitleSelectionOff(
                selectedLanguage = "en",
                isTextTrackDisabled = false,
                hasSelectableTextTracks = true
            )
        )
    }

    @Test
    fun `missing subtitle tracks do not look like an explicit none selection`() {
        assertFalse(
            isSubtitleSelectionOff(
                selectedLanguage = null,
                isTextTrackDisabled = false,
                hasSelectableTextTracks = false
            )
        )
    }

    @Test
    fun `disabled text renderer is off without available subtitle tracks`() {
        assertTrue(
            isSubtitleSelectionOff(
                selectedLanguage = null,
                isTextTrackDisabled = true,
                hasSelectableTextTracks = false
            )
        )
    }
}
