package tv.ororo.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale

object OroroColors {
    val Background = Color(0xFF1A1A2E)
    val Surface = Color(0xFF16213E)
    val SurfaceRaised = Color(0xFF25253D)
    val Accent = Color(0xFF6C63FF)
    val FocusRing = Color(0xFFE2DFFF)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFB0B0B0)
    val TextMuted = Color(0xFF85859A)
    val Success = Color(0xFF77DD77)
    val SuccessStrong = Color(0xFF2E7D32)
    val Error = Color(0xFFFF6B6B)
    val Rating = Color(0xFFFFD700)
    val PosterPlaceholder = Color(0xFF24243B)
    val ProgressTrack = Color(0x80383850)
}

object OroroShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(18.dp)
}

object OroroDimens {
    val ScreenPadding = 24.dp
    val HomeHorizontalPadding = 60.dp
    val FocusBorderWidth = 2.dp
}

object OroroFocusDefaults {
    fun scale(): ClickableSurfaceScale = ClickableSurfaceDefaults.scale(
        focusedScale = 1.05f,
        pressedScale = 1.02f
    )

    @Composable
    fun border(shape: Shape): ClickableSurfaceBorder {
        val focusBorder = Border(
            border = BorderStroke(OroroDimens.FocusBorderWidth, OroroColors.FocusRing),
            shape = shape
        )
        return ClickableSurfaceDefaults.border(
            focusedBorder = focusBorder,
            pressedBorder = focusBorder
        )
    }
}
