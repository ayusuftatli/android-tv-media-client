package tv.ororo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import android.util.Log
import kotlinx.coroutines.launch
import tv.ororo.app.R

@Composable
fun HomeScreen(
    onMoviesClick: () -> Unit,
    onShowsClick: () -> Unit,
    onSavedClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }

    Log.d("HomeScreen", "HomeScreen composed")

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(id = R.drawable.ororo_logo),
                contentDescription = "Ororo TV logo",
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ororo TV",
                fontSize = 32.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                HomeCard(
                    title = "Search",
                    icon = Icons.Default.Search,
                    onClick = onSearchClick,
                    focusRequester = searchFocusRequester,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "Movies",
                    icon = Icons.Default.Movie,
                    onClick = onMoviesClick,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "Saved",
                    icon = Icons.Default.Bookmark,
                    onClick = onSavedClick,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "TV Shows",
                    icon = Icons.Default.Tv,
                    onClick = onShowsClick,
                    modifier = Modifier.weight(1f)
                )
                HomeCard(
                    title = "Logout",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = {
                        scope.launch {
                            viewModel.logout()
                            onLogout()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val cardModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    Surface(
        onClick = onClick,
        modifier = cardModifier.height(180.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF16213e),
            focusedContainerColor = Color(0xFF6C63FF)
        ),
        shape = ClickableSurfaceDefaults.shape(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }
}
