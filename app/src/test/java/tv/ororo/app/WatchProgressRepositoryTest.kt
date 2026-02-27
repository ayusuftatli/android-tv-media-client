package tv.ororo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.ororo.app.data.repository.WatchProgressRepository

class WatchProgressRepositoryTest {

    @Test
    fun `content keys are prefixed by content type`() {
        assertEquals("movie:42", WatchProgressRepository.contentKey("movie", 42))
        assertEquals("episode:12", WatchProgressRepository.contentKey("episode", 12))
    }

    @Test
    fun `completion threshold marks watched at 95 percent`() {
        assertTrue(
            WatchProgressRepository.isCompleted(
                positionMs = 95_000,
                durationMs = 100_000,
                isEnded = false
            )
        )
        assertFalse(
            WatchProgressRepository.isCompleted(
                positionMs = 94_000,
                durationMs = 100_000,
                isEnded = false
            )
        )
    }

    @Test
    fun `playback end marks watched regardless of ratio`() {
        assertTrue(
            WatchProgressRepository.isCompleted(
                positionMs = 1_000,
                durationMs = 100_000,
                isEnded = true
            )
        )
    }

    @Test
    fun `content key parser extracts type and id`() {
        assertEquals("movie" to 42, WatchProgressRepository.parseContentKey("movie:42"))
        assertEquals("episode" to 9, WatchProgressRepository.parseContentKey("episode:9"))
    }

    @Test
    fun `content key parser returns null for invalid keys`() {
        assertEquals(null, WatchProgressRepository.parseContentKey("movie"))
        assertEquals(null, WatchProgressRepository.parseContentKey("movie:abc"))
        assertEquals(null, WatchProgressRepository.parseContentKey(":12"))
    }
}
