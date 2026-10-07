package tv.ororo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.ororo.app.data.api.HttpStatusException
import tv.ororo.app.data.auth.AuthEvent
import tv.ororo.app.data.auth.AuthEventBus
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SessionRepository

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val ororoRepository: OroroRepository,
    private val authEventBus: AuthEventBus
) : ViewModel() {
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    init {
        viewModelScope.launch {
            val restoredCredentials = sessionRepository.getCredentials()
            _isLoggedIn.value = restoredCredentials != null
            if (restoredCredentials != null) {
                try {
                    // Warm the catalog after making Home available. Other callers reuse this load.
                    ororoRepository.getMovies()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // A network outage must not discard a saved session.
                    if (error is HttpStatusException && error.code == 401 &&
                        sessionRepository.getCredentials() == restoredCredentials
                    ) {
                        sessionRepository.clearSession()
                        ororoRepository.clearCache()
                        authEventBus.send(AuthEvent.SessionExpired)
                    }
                }
            }
        }
    }
}
