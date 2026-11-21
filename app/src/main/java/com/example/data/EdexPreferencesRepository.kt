package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.EdexUserPreferences
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.TelemetryThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.edexDataStore: DataStore<Preferences> by preferencesDataStore(name = "edex_prefs")

class EdexPreferencesRepository(private val context: Context) {
  private object Keys {
    val THEME_ID = stringPreferencesKey("theme_id")
    val TELEMETRY_THEME_ID = stringPreferencesKey("telemetry_theme_id")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val SCANLINES_ENABLED = booleanPreferencesKey("scanlines_enabled")
    val SHOW_KEYBOARD = booleanPreferencesKey("show_keyboard")
    val BACKGROUND_MONITOR = booleanPreferencesKey("background_monitor")
    val GLITCH_SHADER_ENABLED = booleanPreferencesKey("glitch_shader_enabled")
    val GLITCH_LOAD_THRESHOLD = floatPreferencesKey("glitch_load_threshold")
  }

  val preferences: Flow<EdexUserPreferences> =
    context.edexDataStore.data.map { prefs ->
      val themeName = prefs[Keys.THEME_ID] ?: EdexThemeId.TRON.name
      val themeId =
        EdexThemeId.entries.firstOrNull { it.name == themeName } ?: EdexThemeId.TRON
      val telThemeName = prefs[Keys.TELEMETRY_THEME_ID] ?: TelemetryThemeId.SYNC_HUD.name
      val telemetryThemeId =
        TelemetryThemeId.entries.firstOrNull { it.name == telThemeName }
          ?: TelemetryThemeId.SYNC_HUD
      EdexUserPreferences(
        themeId = themeId,
        telemetryThemeId = telemetryThemeId,
        soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
        scanlinesEnabled = prefs[Keys.SCANLINES_ENABLED] ?: true,
        showKeyboard = prefs[Keys.SHOW_KEYBOARD] ?: false,
        backgroundMonitorEnabled = prefs[Keys.BACKGROUND_MONITOR] ?: true,
        glitchShaderEnabled = prefs[Keys.GLITCH_SHADER_ENABLED] ?: true,
        glitchLoadThreshold = prefs[Keys.GLITCH_LOAD_THRESHOLD] ?: 75f,
      )
    }

  suspend fun setTheme(themeId: EdexThemeId) {
    context.edexDataStore.edit { it[Keys.THEME_ID] = themeId.name }
  }

  suspend fun setTelemetryTheme(themeId: TelemetryThemeId) {
    context.edexDataStore.edit { it[Keys.TELEMETRY_THEME_ID] = themeId.name }
  }

  suspend fun setSoundEnabled(enabled: Boolean) {
    context.edexDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
  }

  suspend fun setScanlinesEnabled(enabled: Boolean) {
    context.edexDataStore.edit { it[Keys.SCANLINES_ENABLED] = enabled }
  }

  suspend fun setShowKeyboard(show: Boolean) {
    context.edexDataStore.edit { it[Keys.SHOW_KEYBOARD] = show }
  }

  suspend fun setBackgroundMonitorEnabled(enabled: Boolean) {
    context.edexDataStore.edit { it[Keys.BACKGROUND_MONITOR] = enabled }
  }

  suspend fun setGlitchShaderEnabled(enabled: Boolean) {
    context.edexDataStore.edit { it[Keys.GLITCH_SHADER_ENABLED] = enabled }
  }

  suspend fun setGlitchLoadThreshold(threshold: Float) {
    context.edexDataStore.edit { it[Keys.GLITCH_LOAD_THRESHOLD] = threshold.coerceIn(20f, 95f) }
  }
}
