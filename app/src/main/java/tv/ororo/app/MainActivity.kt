package tv.ororo.app

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tv.ororo.app.data.auth.AuthEvent
import tv.ororo.app.data.auth.AuthEventBus
import tv.ororo.app.data.repository.OroroRepository
import tv.ororo.app.data.repository.SessionRepository
import tv.ororo.app.ui.navigation.AppNavHost
import tv.ororo.app.ui.navigation.Screen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var ororoRepository: OroroRepository

    @Inject
    lateinit var authEventBus: AuthEventBus

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d("MainActivity", "dispatchKeyEvent: action=${event.action}, keyCode=${event.keyCode}, " +
            "keyCodeName=${KeyEvent.keyCodeToString(event.keyCode)}")
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var startDestination by remember { mutableStateOf<String?>(null) }

            // Validate persisted session on app start
            LaunchedEffect(Unit) {
                val loggedIn = sessionRepository.isLoggedIn.first()
                if (loggedIn) {
                    try {
                        // Lightweight API call to verify credentials are still valid
                        ororoRepository.getMovies(forceRefresh = true)
                        startDestination = Screen.Home.route
                    } catch (_: Exception) {
                        // Credentials invalid or network error – send to login
                        sessionRepository.clearSession()
                        ororoRepository.clearCache()
                        startDestination = Screen.Login.route
                    }
                } else {
                    startDestination = Screen.Login.route
                }
            }

            // Observe global auth events (401 from any screen)
            LaunchedEffect(navController) {
                authEventBus.events.onEach {
                    when (it) {
                        AuthEvent.SessionExpired -> {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }.launchIn(this)
            }

            if (startDestination != null) {
                AppNavHost(
                    navController = navController,
                    startDestination = startDestination!!
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading...")
                }
            }
        }
    }
}
