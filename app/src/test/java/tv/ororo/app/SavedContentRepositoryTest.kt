package tv.ororo.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.ororo.app.data.repository.SavedContentRepository

class SavedContentRepositoryTest {

    @Test
    fun `content keys are normalized and prefixed`() {
        assertEquals("movie:42", SavedContentRepository.contentKey("MOVIE", 42))
        assertEquals("show:7", SavedContentRepository.contentKey("show", 7))
    }

    @Test
    fun `saved ids are parsed by content type`() {
        val keys = setOf("movie:1", "movie:5", "show:9", "movie:bad")
        assertEquals(
            setOf(1, 5),
            SavedContentRepository.savedIdsForType(keys, SavedContentRepository.TYPE_MOVIE)
        )
        assertEquals(
            setOf(9),
            SavedContentRepository.savedIdsForType(keys, SavedContentRepository.TYPE_SHOW)
        )
    }
}
