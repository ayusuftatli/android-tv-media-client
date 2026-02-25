package tv.ororo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape

enum class SortOption(val label: String) {
    TITLE("Title"),
    ADDED("Added"),
    YEAR("Year"),
    RATING("Rating")
}

@Composable
fun SortFilterBar(
    currentSort: SortOption,
    selectedGenre: String?,
    genres: List<String>,
    onSortSelected: (SortOption) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Sort:", color = Color.Gray, fontSize = 14.sp)
        SortOption.entries.forEach { option ->
            ChipButton(
                label = option.label,
                isSelected = currentSort == option,
                onClick = { onSortSelected(option) }
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text("Genre:", color = Color.Gray, fontSize = 14.sp)
        ChipButton(
            label = "All",
            isSelected = selectedGenre == null,
            onClick = { onGenreSelected(null) }
        )
    }
}

@Composable
fun GenreRow(
    genres: List<String>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChipButton(
            label = "All",
            isSelected = selectedGenre == null,
            onClick = { onGenreSelected(null) }
        )
        genres.take(10).forEach { genre ->
            ChipButton(
                label = genre,
                isSelected = selectedGenre == genre,
                onClick = { onGenreSelected(genre) }
            )
        }
    }
}

@Composable
private fun ChipButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFF6C63FF) else Color(0xFF16213e),
            focusedContainerColor = Color(0xFF6C63FF)
        ),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(16.dp)
        )
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
