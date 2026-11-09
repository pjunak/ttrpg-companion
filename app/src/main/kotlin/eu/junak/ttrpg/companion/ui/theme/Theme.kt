package eu.junak.ttrpg.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.junak.ttrpg.companion.core.model.SiteTheme

/**
 * The website's palettes (`frontend/src/styles/tokens.css` in the host):
 * Classic is gold on dark parchment, Moonlit is pale blue on night. Each has
 * a light counterpart for readers who use a light system theme.
 */
private object Classic {
    val dark = darkColorScheme(
        primary = Color(0xFFC8A040),
        onPrimary = Color(0xFF2B1A00),
        primaryContainer = Color(0xFF3A2C10),
        onPrimaryContainer = Color(0xFFF0D58C),
        secondary = Color(0xFFD4C49A),
        onSecondary = Color(0xFF2B1A00),
        secondaryContainer = Color(0xFF332812),
        onSecondaryContainer = Color(0xFFEDE0BE),
        tertiary = Color(0xFFE08A7A),
        tertiaryContainer = Color(0xFF5A1A1A),
        onTertiaryContainer = Color(0xFFFFDAD3),
        background = Color(0xFF110D06),
        onBackground = Color(0xFFF5EDD8),
        surface = Color(0xFF110D06),
        onSurface = Color(0xFFF5EDD8),
        surfaceVariant = Color(0xFF241C0D),
        onSurfaceVariant = Color(0xFFB9A880),
        surfaceContainerLowest = Color(0xFF0E0A05),
        surfaceContainerLow = Color(0xFF181208),
        surfaceContainer = Color(0xFF1C1509),
        surfaceContainerHigh = Color(0xFF241C0D),
        surfaceContainerHighest = Color(0xFF2E2414),
        inverseSurface = Color(0xFFF5EDD8),
        inverseOnSurface = Color(0xFF2B1A00),
        outline = Color(0xFF6E5B35),
        outlineVariant = Color(0xFF3A2E18),
        error = Color(0xFFEF9A9A),
    )
    val light = lightColorScheme(
        primary = Color(0xFF74520D),
        onPrimary = Color(0xFFFFFAF0),
        primaryContainer = Color(0xFFEBD9A8),
        onPrimaryContainer = Color(0xFF2B1A00),
        secondary = Color(0xFF5C3D14),
        onSecondary = Color(0xFFFFFAF0),
        secondaryContainer = Color(0xFFEADFC4),
        onSecondaryContainer = Color(0xFF2B1A00),
        tertiary = Color(0xFF8B2020),
        tertiaryContainer = Color(0xFFF6D6CF),
        onTertiaryContainer = Color(0xFF410002),
        background = Color(0xFFF5EDD8),
        onBackground = Color(0xFF2B1A00),
        surface = Color(0xFFF5EDD8),
        onSurface = Color(0xFF2B1A00),
        surfaceVariant = Color(0xFFEADFC4),
        onSurfaceVariant = Color(0xFF5C4A2C),
        surfaceContainerLowest = Color(0xFFFFFAF0),
        surfaceContainerLow = Color(0xFFF9F2E1),
        surfaceContainer = Color(0xFFF1E8D2),
        surfaceContainerHigh = Color(0xFFEBE0C6),
        surfaceContainerHighest = Color(0xFFE4D8BB),
        outline = Color(0xFF897349),
        outlineVariant = Color(0xFFD5C7A4),
        error = Color(0xFF9B2020),
    )
}

private object Moonlit {
    val dark = darkColorScheme(
        primary = Color(0xFFADD0D8),
        onPrimary = Color(0xFF071018),
        primaryContainer = Color(0xFF1E3A44),
        onPrimaryContainer = Color(0xFFD3EBF0),
        secondary = Color(0xFFBAC9CC),
        onSecondary = Color(0xFF071018),
        secondaryContainer = Color(0xFF1A2C34),
        onSecondaryContainer = Color(0xFFDCE8EA),
        tertiary = Color(0xFFE6B3C8),
        tertiaryContainer = Color(0xFF4A2338),
        onTertiaryContainer = Color(0xFFFFD8E6),
        background = Color(0xFF09131B),
        onBackground = Color(0xFFE2EAEB),
        surface = Color(0xFF09131B),
        onSurface = Color(0xFFE2EAEB),
        surfaceVariant = Color(0xFF18303A),
        onSurfaceVariant = Color(0xFF9CB0B5),
        surfaceContainerLowest = Color(0xFF071018),
        surfaceContainerLow = Color(0xFF0D1921),
        surfaceContainer = Color(0xFF101C24),
        surfaceContainerHigh = Color(0xFF16262F),
        surfaceContainerHighest = Color(0xFF1E323C),
        inverseSurface = Color(0xFFE2EAEB),
        inverseOnSurface = Color(0xFF071018),
        outline = Color(0xFF4F7480),
        outlineVariant = Color(0xFF213843),
        error = Color(0xFFEF9A9A),
    )
    val light = lightColorScheme(
        primary = Color(0xFF2F6372),
        onPrimary = Color(0xFFF6FBFC),
        primaryContainer = Color(0xFFC6E2E9),
        onPrimaryContainer = Color(0xFF071F27),
        secondary = Color(0xFF3E5A63),
        onSecondary = Color(0xFFF6FBFC),
        secondaryContainer = Color(0xFFD4E3E7),
        onSecondaryContainer = Color(0xFF0B1F26),
        tertiary = Color(0xFF7A3A57),
        tertiaryContainer = Color(0xFFF4D7E3),
        onTertiaryContainer = Color(0xFF33061A),
        background = Color(0xFFEEF3F4),
        onBackground = Color(0xFF0F2630),
        surface = Color(0xFFEEF3F4),
        onSurface = Color(0xFF0F2630),
        surfaceVariant = Color(0xFFDCE6E8),
        onSurfaceVariant = Color(0xFF3F5960),
        surfaceContainerLowest = Color(0xFFFAFCFD),
        surfaceContainerLow = Color(0xFFF0F5F6),
        surfaceContainer = Color(0xFFE7EEF0),
        surfaceContainerHigh = Color(0xFFE0E9EB),
        surfaceContainerHighest = Color(0xFFD8E2E5),
        outline = Color(0xFF5B8794),
        outlineVariant = Color(0xFFC2D2D6),
        error = Color(0xFF9B2020),
    )
}

/** Spacing and size steps shared by every screen. */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val portraitSmall: Dp = 36.dp,
    val portrait: Dp = 48.dp,
    val portraitMedium: Dp = 64.dp,
    val portraitLarge: Dp = 168.dp,
    val tileHeight: Dp = 88.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

private val CompanionShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Whether the app follows the system's light or dark setting or keeps one of them. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

fun colorScheme(theme: SiteTheme, dark: Boolean): ColorScheme = when (theme) {
    SiteTheme.CLASSIC -> if (dark) Classic.dark else Classic.light
    SiteTheme.MOONLIT -> if (dark) Moonlit.dark else Moonlit.light
}

/**
 * The app's theme. Screens of a site pass its [siteTheme]; the rest use Classic.
 * [mode] comes from Settings → Appearance through [LocalThemeMode].
 */
@Composable
fun CompanionTheme(
    siteTheme: SiteTheme = SiteTheme.CLASSIC,
    mode: ThemeMode = LocalThemeMode.current,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalThemeMode provides mode) {
        MaterialTheme(
            colorScheme = colorScheme(siteTheme, dark),
            typography = CompanionTypography,
            shapes = CompanionShapes,
            content = content,
        )
    }
}

/** Gives record articles the reading typography while keeping the current colours. */
@Composable
fun ArticleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = ArticleTypography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/** An option colour from the DM's settings, or [fallback] when none is set. */
fun optionColor(color: Long?, fallback: Color): Color = color?.let { Color(it) } ?: fallback
