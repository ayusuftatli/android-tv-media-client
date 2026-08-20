package tv.ororo.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import tv.ororo.app.ui.theme.OroroColors
import tv.ororo.app.ui.theme.OroroFocusDefaults

@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    selected: Boolean = false,
    containerColor: Color? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val background = containerColor ?: when {
        primary || selected -> OroroColors.Accent
        else -> OroroColors.SurfaceRaised
    }

    Surface(
        onClick = onClick,
        modifier = modifier.zIndex(if (isFocused) 1f else 0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background,
            pressedContainerColor = background,
            contentColor = OroroColors.TextPrimary,
            focusedContentColor = OroroColors.TextPrimary,
            pressedContentColor = OroroColors.TextPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = OroroFocusDefaults.scale(),
        border = OroroFocusDefaults.border(shape),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = OroroColors.TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = OroroColors.TextPrimary,
                fontSize = 14.sp
            )
        }
    }
}
