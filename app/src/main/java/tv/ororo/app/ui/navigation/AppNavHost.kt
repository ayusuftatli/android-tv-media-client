package tv.ororo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import tv.ororo.app.ui.home.HomeScreen
import tv.ororo.app.ui.login.LoginScreen
import tv.ororo.app.ui.movies.MovieBrowseScreen
import tv.ororo.app.ui.movies.MovieDetailScreen
import tv.ororo.app.ui.player.PlayerScreen
import tv.ororo.app.ui.saved.SavedScreen
import tv.ororo.app.ui.search.SearchScreen
import tv.ororo.app.ui.shows.ShowBrowseScreen
import tv.ororo.app.ui.shows.ShowDetailScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onMoviesClick = { navController.navigate(Screen.MovieBrowse.route) },
                onShowsClick = { navController.navigate(Screen.ShowBrowse.route) },
                onSavedClick = { navController.navigate(Screen.Saved.route) },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Saved.route) {
            SavedScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onShowClick = { showId ->
                    navController.navigate(Screen.ShowDetail.createRoute(showId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MovieBrowse.route) {
            MovieBrowseScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ShowBrowse.route) {
            ShowBrowseScreen(
                onShowClick = { showId ->
                    navController.navigate(Screen.ShowDetail.createRoute(showId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MovieDetail.route,
            arguments = listOf(navArgument("movieId") { type = NavType.IntType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: return@composable
            MovieDetailScreen(
                movieId = movieId,
                onPlayClick = { navController.navigate(Screen.Player.createRoute("movie", movieId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ShowDetail.route,
            arguments = listOf(navArgument("showId") { type = NavType.IntType })
        ) { backStackEntry ->
            val showId = backStackEntry.arguments?.getInt("showId") ?: return@composable
            ShowDetailScreen(
                showId = showId,
                onEpisodeClick = { episodeId ->
                    navController.navigate(Screen.Player.createRoute("episode", episodeId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onShowClick = { showId ->
                    navController.navigate(Screen.ShowDetail.createRoute(showId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: return@composable
            val id = backStackEntry.arguments?.getInt("id") ?: return@composable
            PlayerScreen(
                contentType = type,
                contentId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
