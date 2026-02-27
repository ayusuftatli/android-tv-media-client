package tv.ororo.app.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Saved : Screen("saved")
    data object MovieBrowse : Screen("movies")
    data object ShowBrowse : Screen("shows")
    data object MovieDetail : Screen("movie/{movieId}") {
        fun createRoute(movieId: Int) = "movie/$movieId"
    }
    data object ShowDetail : Screen("show/{showId}") {
        fun createRoute(showId: Int) = "show/$showId"
    }
    data object Search : Screen("search")
    data object Player : Screen("player/{type}/{id}") {
        fun createRoute(type: String, id: Int) = "player/$type/$id"
    }
}
