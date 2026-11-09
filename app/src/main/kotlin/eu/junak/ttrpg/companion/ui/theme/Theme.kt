package eu.junak.ttrpg.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The host's campaign palette: gold on dark parchment, and its light counterpart. */
private object Palette {
    val gold = Color(0xFFC8A040)
    val goldBright = Color(0xFFE2BD5C)
    val goldDeep = Color(0xFF8B6914)
    val ink = Color(0xFF2B1A00)
    val night = Color(0xFF141008)
    val surface = Color(0xFF1C1509)
    val raised = Color(0xFF241C0D)
    val field = Color(0xFF2B2213)
    val text = Color(0xFFECE2CC)
    val textDim = Color(0xFFB9AA88)
    val line = Color(0xFF5A4A2E)
    val danger = Color(0xFFEF9A9A)
    val parchment = Color(0xFFFAF6EC)
    val parchmentRaised = Color(0xFFF1E9D6)
    val inkText = Color(0xFF2A2318)
    val inkDim = Color(0xFF6B5F4B)
}

private val DarkColors = darkColorScheme(
    primary = Palette.gold,
    onPrimary = Palette.ink,
    primaryContainer = Palette.goldDeep,
    onPrimaryContainer = Palette.text,
    secondary = Palette.goldBright,
    onSecondary = Palette.ink,
    background = Palette.night,
    onBackground = Palette.text,
    surface = Palette.night,
    onSurface = Palette.text,
    surfaceVariant = Palette.raised,
    onSurfaceVariant = Palette.textDim,
    surfaceContainerLowest = Palette.night,
    surfaceContainerLow = Palette.surface,
    surfaceContainer = Palette.surface,
    surfaceContainerHigh = Palette.raised,
    surfaceContainerHighest = Palette.field,
    outline = Palette.line,
    outlineVariant = Palette.raised,
    error = Palette.danger,
)

private val LightColors = lightColorScheme(
    primary = Palette.goldDeep,
    onPrimary = Palette.parchment,
    primaryContainer = Palette.gold,
    onPrimaryContainer = Palette.ink,
    secondary = Palette.goldDeep,
    background = Palette.parchment,
    onBackground = Palette.inkText,
    surface = Palette.parchment,
    onSurface = Palette.inkText,
    surfaceVariant = Palette.parchmentRaised,
    onSurfaceVariant = Palette.inkDim,
    surfaceContainer = Palette.parchmentRaised,
    outline = Palette.inkDim,
)

/** Spacing steps shared by every screen. */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val portrait: Dp = 48.dp,
    val portraitLarge: Dp = 160.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

@Composable
fun CompanionTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
