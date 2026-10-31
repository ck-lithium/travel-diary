package app.traveldiary.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.traveldiary.R

/**
 * Colours of a range of dates in the calendar: [strong] for the first and the last day, [soft] for
 * the days in between, each with the colour of the text on it.
 */
@Immutable
data class RangePalette(val strong: Color, val onStrong: Color, val soft: Color, val onSoft: Color)

/** The tints the user can pick in Settings for the calendar; [GREEN] until they choose. */
enum class RangeColor(@StringRes val label: Int, val light: RangePalette, val dark: RangePalette) {
    GREEN(
        R.string.range_color_green,
        RangePalette(Color(0xFF2E7D32), Color(0xFFFFFFFF), Color(0xFFC8E6C9), Color(0xFF0B2E0D)),
        RangePalette(Color(0xFF81C784), Color(0xFF0B2E0D), Color(0xFF1E4620), Color(0xFFD7F2D8)),
    ),
    BLUE(
        R.string.range_color_blue,
        RangePalette(Color(0xFF1565C0), Color(0xFFFFFFFF), Color(0xFFBBDEFB), Color(0xFF0A2540)),
        RangePalette(Color(0xFF90CAF9), Color(0xFF0A2540), Color(0xFF173A5E), Color(0xFFDCEBFA)),
    ),
    TEAL(
        R.string.range_color_teal,
        RangePalette(Color(0xFF00796B), Color(0xFFFFFFFF), Color(0xFFB2DFDB), Color(0xFF00251F)),
        RangePalette(Color(0xFF80CBC4), Color(0xFF00251F), Color(0xFF12403B), Color(0xFFD5F0ED)),
    ),
    ORANGE(
        R.string.range_color_orange,
        RangePalette(Color(0xFFE65100), Color(0xFFFFFFFF), Color(0xFFFFE0B2), Color(0xFF3E1A00)),
        RangePalette(Color(0xFFFFB74D), Color(0xFF3E1A00), Color(0xFF5A3300), Color(0xFFFFEBD1)),
    ),
    PURPLE(
        R.string.range_color_purple,
        RangePalette(Color(0xFF6A1B9A), Color(0xFFFFFFFF), Color(0xFFE1BEE7), Color(0xFF2A0A3D)),
        RangePalette(Color(0xFFCE93D8), Color(0xFF2A0A3D), Color(0xFF4A2257), Color(0xFFF5E1F8)),
    ),
    PINK(
        R.string.range_color_pink,
        RangePalette(Color(0xFFC2185B), Color(0xFFFFFFFF), Color(0xFFF8BBD0), Color(0xFF3E0A1E)),
        RangePalette(Color(0xFFF48FB1), Color(0xFF3E0A1E), Color(0xFF5C1F35), Color(0xFFFCE4EC)),
    ),
    ;

    fun palette(darkTheme: Boolean): RangePalette = if (darkTheme) dark else light

    companion object {
        val Default = GREEN
    }
}

/** The palette of the tint picked in Settings, for the current light or dark theme. */
val LocalRangePalette = staticCompositionLocalOf { RangeColor.Default.light }
