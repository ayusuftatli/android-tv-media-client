package tv.ororo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tv.ororo.app.data.auth.AuthEvent
import tv.ororo.app.data.auth.AuthEventBus
import tv.ororo.app.ui.navigation.AppNavHost
import tv.ororo.app.ui.navigation.Screen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var authEventBus: AuthEventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
            val startDestination = isLoggedIn?.let { if (it) Screen.Home.route else Screen.Login.route }

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
                    startDestination = startDestination
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
