package tv.ororo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.ororo.app.data.domain.model.Subtitle
import tv.ororo.app.ui.player.selectPreferredSubtitleLanguage

class PlayerSubtitleSelectionTest {

    @Test
    fun `preferred subtitle language is selected when available`() {
        val subtitles = listOf(
            Subtitle(lang = "en", url = "https://example.com/sub.en.srt"),
            Subtitle(lang = "es", url = "https://example.com/sub.es.srt")
        )

        val selected = selectPreferredSubtitleLanguage(subtitles, "es", subtitlesEnabled = true)

        assertEquals("es", selected)
    }

    @Test
    fun `first subtitle is fallback when preferred is missing`() {
        val subtitles = listOf(
            Subtitle(lang = "de", url = "https://example.com/sub.de.vtt"),
            Subtitle(lang = "fr", url = "https://example.com/sub.fr.vtt")
        )

        val selected = selectPreferredSubtitleLanguage(subtitles, "en", subtitlesEnabled = true)

        assertEquals("de", selected)
    }

    @Test
    fun `no subtitle selected when subtitles are disabled`() {
        val subtitles = listOf(
            Subtitle(lang = "en", url = "https://example.com/sub.en.srt")
        )

        val selected = selectPreferredSubtitleLanguage(subtitles, "en", subtitlesEnabled = false)

        assertNull(selected)
    }
}
