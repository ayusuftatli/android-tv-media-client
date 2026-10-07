package tv.ororo.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import tv.ororo.app.data.repository.WatchProgressRepository

@OptIn(ExperimentalCoroutinesApi::class)
class WatchProgressSharingTest {
    @Test fun `unchanged progress avoids writes but completion and seeks are saved`() = runTest {
        val preferences = MemoryPreferences()
        val repository = WatchProgressRepository(preferences, Json, backgroundScope, StandardTestDispatcher(testScheduler))
        repository.saveProgress("movie:1", 5_000, 100_000)
        val first = repository.getWatchState("movie:1")
        repeat(3) { repository.saveProgress("movie:1", 5_000, 100_000) }
        assertEquals(1, preferences.changes)
        assertEquals(first, repository.getWatchState("movie:1"))

        repository.saveProgress("movie:1", 5_000, 100_000, isEnded = true)
        assertTrue(repository.getWatchState("movie:1")!!.completed)
        repository.saveProgress("movie:1", 2_000, 100_000)
        assertEquals(3, preferences.changes)
        assertFalse(repository.getWatchState("movie:1")!!.completed)
        assertEquals(2_000L, repository.getWatchState("movie:1")!!.positionMs)
    }

    @Test fun `watch history has one upstream subscription and stops after observers leave`() = runTest {
        val preferences = MemoryPreferences()
        val repository = WatchProgressRepository(preferences, Json, backgroundScope, StandardTestDispatcher(testScheduler))
        val first = backgroundScope.launch { repository.watchStatesFlow().collect() }
        val second = backgroundScope.launch { repository.watchStatesFlow().collect() }
        runCurrent()
        assertEquals(1, preferences.activeCollectors)

        first.cancel()
        second.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, preferences.activeCollectors)
    }

    @Test fun `watched badges do not emit for ordinary progress updates`() = runTest {
        val repository = WatchProgressRepository(MemoryPreferences(), Json, backgroundScope, StandardTestDispatcher(testScheduler))
        val emissions = mutableListOf<Set<Int>>()
        backgroundScope.launch { repository.watchedMovieIdsFlow().collect { emissions += it } }
        runCurrent()
        repository.saveProgress("movie:1", 1_000, 100_000)
        runCurrent()
        repository.saveProgress("movie:1", 2_000, 100_000)
        runCurrent()
        assertEquals(listOf(emptySet<Int>()), emissions)

        repository.saveProgress("movie:1", 100_000, 100_000, true)
        runCurrent()
        assertEquals(listOf(emptySet<Int>(), setOf(1)), emissions)
    }
}
