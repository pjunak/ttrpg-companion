package eu.junak.ttrpg.companion

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.junak.ttrpg.companion.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Device-wide app preferences from Settings. */
@Singleton
class AppPreferences @Inject constructor(@param:ApplicationContext context: Context) {

    private val preferences = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString(KEY_THEME_MODE, null) } ?: ThemeMode.SYSTEM,
    )

    /** Light, dark, or the system's choice; the palette itself follows each site. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    private companion object {
        const val KEY_THEME_MODE = "themeMode"
    }
}
