package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PushupMode
import com.example.ui.theme.ColorPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val USE_FRONT_CAMERA = booleanPreferencesKey("use_front_camera")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val SELECTED_MODE = stringPreferencesKey("selected_mode")
    }

    val dailyGoalFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DAILY_GOAL] ?: 50
    }

    val useFrontCameraFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USE_FRONT_CAMERA] ?: true
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
        try {
            ThemeMode.valueOf(raw)
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    val colorPaletteFlow: Flow<ColorPalette> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.COLOR_PALETTE] ?: ColorPalette.SOLAR_BLAZE.name
        ColorPalette.fromId(raw)
    }

    val selectedModeFlow: Flow<PushupMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.SELECTED_MODE] ?: PushupMode.BEGINNER.name
        PushupMode.fromId(raw)
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_GOAL] = goal.coerceIn(5, 500)
        }
    }

    suspend fun setUseFrontCamera(useFront: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_FRONT_CAMERA] = useFront
        }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setColorPalette(palette: ColorPalette) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLOR_PALETTE] = palette.name
        }
    }

    suspend fun setSelectedMode(mode: PushupMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODE] = mode.name
        }
    }

    suspend fun clearPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
