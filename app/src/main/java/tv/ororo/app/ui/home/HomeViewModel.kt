package tv.ororo.app.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SessionRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val ororoRepository: OroroRepository
) : ViewModel() {

    suspend fun logout() {
        sessionRepository.clearSession()
        ororoRepository.clearCache()
    }
}
