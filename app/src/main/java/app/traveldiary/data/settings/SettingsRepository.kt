package app.traveldiary.data.settings

import android.content.Context
import androidx.core.content.edit
import app.traveldiary.ui.theme.RangeColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's preferences, kept in shared preferences. */
class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _flagMap = MutableStateFlow(preferences.getBoolean(KEY_FLAG_MAP, false))

    /** Paint visited countries with their flag instead of a single colour. */
    val flagMap: StateFlow<Boolean> = _flagMap.asStateFlow()

    fun setFlagMap(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_FLAG_MAP, enabled) }
        _flagMap.value = enabled
    }

    private val _rangeColor = MutableStateFlow(
        RangeColor.entries.firstOrNull { it.name == preferences.getString(KEY_RANGE_COLOR, null) } ?: RangeColor.Default,
    )

    /** Tint of the days picked in the calendar. */
    val rangeColor: StateFlow<RangeColor> = _rangeColor.asStateFlow()

    fun setRangeColor(color: RangeColor) {
        preferences.edit { putString(KEY_RANGE_COLOR, color.name) }
        _rangeColor.value = color
    }

    /** True once the app has asked where the user lives, whatever the answer: it only asks once. */
    val homeAsked: Boolean get() = preferences.getBoolean(KEY_HOME_ASKED, false)

    fun markHomeAsked() = preferences.edit { putBoolean(KEY_HOME_ASKED, true) }

    private companion object {
        const val KEY_FLAG_MAP = "flag_map"
        const val KEY_HOME_ASKED = "home_asked"
        const val KEY_RANGE_COLOR = "range_color"
    }
}
