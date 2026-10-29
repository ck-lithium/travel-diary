package app.traveldiary.data.settings

import android.content.Context
import androidx.core.content.edit
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

    private companion object {
        const val KEY_FLAG_MAP = "flag_map"
    }
}
