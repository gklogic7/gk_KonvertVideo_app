package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class StudioExtendedColors(
    val success: Color = EmeraldSuccess,
    val successContainer: Color = EmeraldContainer,
    val onSuccessContainer: Color = EmeraldOnContainer,
    val amber: Color = AmberSignal,
    val amberContainer: Color = AmberContainer,
    val onAmberContainer: Color = AmberOnContainer,
    val surfaceElevated: Color = ObsidianSurfaceElevated,
    val hairlineBorder: Color = ObsidianOutline,
    val hairlineBorderBright: Color = ObsidianOutlineBright
)

val LocalStudioColors = staticCompositionLocalOf { StudioExtendedColors() }

private val ObsidianDarkColorScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = AmberSignal,
    onSecondary = AmberOnSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = EmeraldSuccess,
    onTertiary = CyanOnPrimary,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOnContainer,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineBright,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CoralOnErrorContainer
)

val KonvertShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KonvertVideoTheme(
    content: @Composable () -> Unit
) {
    val studioColors = StudioExtendedColors()
    CompositionLocalProvider(LocalStudioColors provides studioColors) {
        MaterialTheme(
            colorScheme = ObsidianDarkColorScheme,
            typography = KonvertTypography,
            shapes = KonvertShapes,
            content = content
        )
    }
}
