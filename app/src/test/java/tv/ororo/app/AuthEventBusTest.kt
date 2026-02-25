package tv.ororo.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tv.ororo.app.data.auth.AuthEvent
import tv.ororo.app.data.auth.AuthEventBus

class AuthEventBusTest {

    @Test
    fun `emitted SessionExpired event is received`() = runTest {
        val bus = AuthEventBus()
        var received: AuthEvent? = null

        val job = launch {
            bus.events.collect { received = it }
        }

        bus.send(AuthEvent.SessionExpired)
        // Allow coroutine to process
        testScheduler.advanceUntilIdle()

        assertEquals(AuthEvent.SessionExpired, received)
        job.cancel()
    }

    @Test
    fun `multiple events are received in order`() = runTest {
        val bus = AuthEventBus()
        val events = mutableListOf<AuthEvent>()

        val job = launch {
            bus.events.collect { events.add(it) }
        }

        bus.send(AuthEvent.SessionExpired)
        bus.send(AuthEvent.SessionExpired)
        testScheduler.advanceUntilIdle()

        assertEquals(2, events.size)
        job.cancel()
    }
}
