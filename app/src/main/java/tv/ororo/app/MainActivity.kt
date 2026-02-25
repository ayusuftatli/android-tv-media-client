package tv.ororo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import tv.ororo.app.data.repository.SessionRepository
import tv.ororo.app.ui.navigation.AppNavHost
import tv.ororo.app.ui.navigation.Screen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var startDestination by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                val loggedIn = sessionRepository.isLoggedIn.first()
                startDestination = if (loggedIn) Screen.Home.route else Screen.Login.route
            }

            if (startDestination != null) {
                AppNavHost(
                    navController = navController,
                    startDestination = startDestination!!
                )
            }
        }
    }
}
