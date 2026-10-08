package tv.ororo.app

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import tv.ororo.app.data.repository.WatchProgressRepository
import tv.ororo.app.data.repository.WatchState

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueWatchingRetentionTest {
    @Test
    fun `existing history is pruned to the latest five resume entries`() = runTest {
        val preferences = MemoryPreferences()
        preferences.edit { prefs ->
            (1..8).forEach { id ->
                val key = "movie:$id"
                prefs[stringPreferencesKey("watch_state_$key")] = Json.encodeToString(
                    WatchState(key, 5_000, 100_000, completed = id == 8, updatedAt = id.toLong())
                )
            }
            prefs[stringPreferencesKey("unrelated")] = "keep"
        }
        val repository = WatchProgressRepository(preferences, Json, backgroundScope, StandardTestDispatcher(testScheduler))

        assertEquals(listOf("movie:7", "movie:6", "movie:5", "movie:4", "movie:3"),
            repository.inProgressWatchStatesFlow().first().map { it.contentKey })
        assertNull(repository.getWatchState("movie:1"))
        assertNull(repository.getWatchState("movie:2"))
        assertTrue(repository.getWatchState("movie:8")!!.completed)
        assertEquals("keep", preferences.data.first()[stringPreferencesKey("unrelated")])
    }

    @Test
    fun `saving progress evicts the oldest entry and revisiting keeps a title recent`() = runTest {
        val preferences = MemoryPreferences()
        preferences.edit { prefs ->
            (1..5).forEach { id ->
                val key = "episode:$id"
                prefs[stringPreferencesKey("watch_state_$key")] = Json.encodeToString(
                    WatchState(key, 5_000, 100_000, completed = false, updatedAt = id.toLong())
                )
            }
        }
        val repository = WatchProgressRepository(preferences, Json, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.saveProgress("episode:1", 10_000, 100_000)
        repository.saveProgress("movie:6", 5_000, 100_000)

        assertNull(repository.getWatchState("episode:2"))
        assertEquals(10_000L, repository.getWatchState("episode:1")!!.positionMs)
        assertNotNull(repository.getWatchState("movie:6"))
        repository.saveProgress("movie:6", 100_000, 100_000, isEnded = true)
        assertEquals(4, repository.inProgressWatchStatesFlow().first().size)
        assertNull(repository.getWatchState("episode:2"))
    }
}
