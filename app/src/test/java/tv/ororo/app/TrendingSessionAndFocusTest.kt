package tv.ororo.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tv.ororo.app.data.repository.SessionRepository
import tv.ororo.app.ui.trending.trendingFocusTarget

class TrendingSessionAndFocusTest {
    @Test fun `snapshot scope survives process recreation and changes on every login`() = runTest {
        val preferences = MemoryPreferences()
        val session = SessionRepository(preferences)
        assertNull(session.getCacheScope())
        session.saveCredentials("account", "password")
        val first = session.getCacheScope()
        assertNotNull(first)
        assertEquals(first, SessionRepository(preferences).getCacheScope())
        session.clearSession()
        assertNull(session.getCacheScope())
        session.saveCredentials("account", "password")
        assertNotEquals(first, session.getCacheScope())
    }

    @Test fun `focus follows title identity across ranking changes with nearest index fallback`() {
        assertEquals(2, trendingFocusTarget(2, 1, listOf(3, 1, 2)))
        assertEquals(3, trendingFocusTarget(2, 1, listOf(1, 3, 4)))
        assertEquals(1, trendingFocusTarget(2, 9, listOf(1)))
        assertEquals(4, trendingFocusTarget(null, 0, listOf(4, 5)))
        assertNull(trendingFocusTarget(2, 0, emptyList()))
    }
}
