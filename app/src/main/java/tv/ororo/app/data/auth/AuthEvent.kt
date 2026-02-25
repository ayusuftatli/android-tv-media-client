package tv.ororo.app.data.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton event bus that ViewModels use to signal a forced-logout (401).
 * MainActivity observes [events] and navigates to the Login screen.
 */
@Singleton
class AuthEventBus @Inject constructor() {

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    suspend fun send(event: AuthEvent) {
        _events.send(event)
    }
}

sealed interface AuthEvent {
    data object SessionExpired : AuthEvent
}
