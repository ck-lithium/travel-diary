package app.traveldiary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E6C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA3F1EE),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6362),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E6),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFFB4452F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD3),
    onTertiaryContainer = Color(0xFF3E0500),
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBFA),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5F4),
    surfaceContainer = Color(0xFFE8EFEE),
    surfaceContainerHigh = Color(0xFFE3EAE8),
    surfaceContainerHighest = Color(0xFFDDE4E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D5D2),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF00504E),
    onPrimaryContainer = Color(0xFFA3F1EE),
    secondary = Color(0xFFB0CCCA),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4A),
    onSecondaryContainer = Color(0xFFCCE8E6),
    tertiary = Color(0xFFFFB4A5),
    onTertiary = Color(0xFF611306),
    tertiaryContainer = Color(0xFF8E2E1B),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3635),
)

@Immutable
data class MapColors(
    val ocean: Color,
    val land: Color,
    val border: Color,
    val visited: Color,
    val selection: Color,
    val selectionBorder: Color,
    val pin: Color,
    val pinBorder: Color,
    val gravityLine: Color,
    val gravityLinePast: Color,
    val gravityPin: Color,
    val gravityPinHalo: Color,
)

private val LightMapColors = MapColors(
    ocean = Color(0xFFCFE6EA),
    land = Color(0xFFFBFAF6),
    border = Color(0xFFB9C4C3),
    visited = Color(0xFF1E8C88),
    selection = Color(0x400F6E6C),
    selectionBorder = Color(0xFF00201F),
    pin = Color(0xFFE0603F),
    pinBorder = Color(0xFFFFFFFF),
    gravityLine = Color(0x4D0F6E6C),
    gravityLinePast = Color(0xFF0F6E6C),
    gravityPin = Color(0xFFB4452F),
    gravityPinHalo = Color(0x33B4452F),
)

private val DarkMapColors = MapColors(
    ocean = Color(0xFF0B1A1D),
    land = Color(0xFF2B3534),
    border = Color(0xFF465251),
    visited = Color(0xFF3BA7A2),
    selection = Color(0x4D86D5D2),
    selectionBorder = Color(0xFFE6F2F1),
    pin = Color(0xFFFF8A6B),
    pinBorder = Color(0xFF0E1514),
    gravityLine = Color(0x4D86D5D2),
    gravityLinePast = Color(0xFF86D5D2),
    gravityPin = Color(0xFFFFB4A5),
    gravityPinHalo = Color(0x33FFB4A5),
)

val LocalMapColors = staticCompositionLocalOf { LightMapColors }

@Composable
fun TravelDiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    rangeColor: RangeColor = RangeColor.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMapColors provides if (darkTheme) DarkMapColors else LightMapColors,
        LocalRangePalette provides rangeColor.palette(darkTheme),
    ) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
