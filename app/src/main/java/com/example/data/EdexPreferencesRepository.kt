package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.EdexDefaults
import com.example.model.EdexUserPreferences
import com.example.model.HudProfile
import com.example.model.MacroAccentRole
import com.example.model.MacroDeck
import com.example.model.ShellMacro
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.TelemetryThemeId
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

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
    val ACTIVE_MACRO_DECK_ID = stringPreferencesKey("active_macro_deck_id")
    val MACRO_DECKS_JSON = stringPreferencesKey("macro_decks_json")
    val ACTIVE_HUD_PROFILE_ID = stringPreferencesKey("active_hud_profile_id")
    val HUD_PROFILES_JSON = stringPreferencesKey("hud_profiles_json")
  }

  val preferences: Flow<EdexUserPreferences> =
    context.edexDataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs ->
        val themeName = prefs[Keys.THEME_ID] ?: EdexThemeId.TRON.name
        val themeId =
          EdexThemeId.entries.firstOrNull { it.name == themeName } ?: EdexThemeId.TRON
        val telThemeName = prefs[Keys.TELEMETRY_THEME_ID] ?: TelemetryThemeId.SYNC_HUD.name
        val telemetryThemeId =
          TelemetryThemeId.entries.firstOrNull { it.name == telThemeName }
            ?: TelemetryThemeId.SYNC_HUD
        val macroDecks =
          decodeMacroDecks(prefs[Keys.MACRO_DECKS_JSON]) ?: EdexDefaults.defaultMacroDecks()
        val rawDeckId = prefs[Keys.ACTIVE_MACRO_DECK_ID] ?: macroDecks.firstOrNull()?.id ?: "recon"
        val activeDeckId =
          if (macroDecks.any { it.id == rawDeckId }) rawDeckId
          else macroDecks.firstOrNull()?.id ?: "recon"
        val hudProfiles =
          decodeHudProfiles(prefs[Keys.HUD_PROFILES_JSON]) ?: EdexDefaults.defaultHudProfiles()
        val rawProfileId = prefs[Keys.ACTIVE_HUD_PROFILE_ID]
        val activeProfileId =
          rawProfileId?.takeIf { id -> hudProfiles.any { it.id == id } }

        EdexUserPreferences(
          themeId = themeId,
          telemetryThemeId = telemetryThemeId,
          soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
          scanlinesEnabled = prefs[Keys.SCANLINES_ENABLED] ?: true,
          showKeyboard = prefs[Keys.SHOW_KEYBOARD] ?: false,
          backgroundMonitorEnabled = prefs[Keys.BACKGROUND_MONITOR] ?: true,
          glitchShaderEnabled = prefs[Keys.GLITCH_SHADER_ENABLED] ?: true,
          glitchLoadThreshold = prefs[Keys.GLITCH_LOAD_THRESHOLD] ?: 75f,
          activeMacroDeckId = activeDeckId,
          macroDecks = macroDecks,
          activeHudProfileId = activeProfileId,
          hudProfiles = hudProfiles,
        )
      }

  suspend fun setTheme(themeId: EdexThemeId) {
    context.edexDataStore.edit {
      it[Keys.THEME_ID] = themeId.name
      it.remove(Keys.ACTIVE_HUD_PROFILE_ID)
    }
  }

  suspend fun setTelemetryTheme(themeId: TelemetryThemeId) {
    context.edexDataStore.edit {
      it[Keys.TELEMETRY_THEME_ID] = themeId.name
      it.remove(Keys.ACTIVE_HUD_PROFILE_ID)
    }
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

  suspend fun setActiveMacroDeck(deckId: String) {
    context.edexDataStore.edit { it[Keys.ACTIVE_MACRO_DECK_ID] = deckId }
  }

  suspend fun saveMacroDecks(decks: List<MacroDeck>, activeDeckId: String? = null) {
    context.edexDataStore.edit { prefs ->
      prefs[Keys.MACRO_DECKS_JSON] = encodeMacroDecks(decks)
      if (!activeDeckId.isNullOrBlank()) {
        prefs[Keys.ACTIVE_MACRO_DECK_ID] = activeDeckId
      }
    }
  }

  suspend fun saveHudProfiles(profiles: List<HudProfile>, activeProfileId: String? = null) {
    context.edexDataStore.edit { prefs ->
      prefs[Keys.HUD_PROFILES_JSON] = encodeHudProfiles(profiles)
      if (!activeProfileId.isNullOrBlank()) {
        prefs[Keys.ACTIVE_HUD_PROFILE_ID] = activeProfileId
      } else {
        val currentActive = prefs[Keys.ACTIVE_HUD_PROFILE_ID]
        if (currentActive != null && profiles.none { it.id == currentActive }) {
          prefs.remove(Keys.ACTIVE_HUD_PROFILE_ID)
        }
      }
    }
  }

  suspend fun applyHudProfile(profile: HudProfile) {
    context.edexDataStore.edit { prefs ->
      prefs[Keys.THEME_ID] = profile.themeId.name
      prefs[Keys.TELEMETRY_THEME_ID] = profile.telemetryThemeId.name
      prefs[Keys.GLITCH_SHADER_ENABLED] = profile.glitchShaderEnabled
      prefs[Keys.GLITCH_LOAD_THRESHOLD] = profile.glitchLoadThreshold.coerceIn(20f, 95f)
      prefs[Keys.SCANLINES_ENABLED] = profile.scanlinesEnabled
      prefs[Keys.SOUND_ENABLED] = profile.soundEnabled
      prefs[Keys.ACTIVE_MACRO_DECK_ID] = profile.activeDeckId.ifBlank { "recon" }
      prefs[Keys.ACTIVE_HUD_PROFILE_ID] = profile.id
    }
  }

  suspend fun applyCloudUserHudState(cloudState: CloudUserHudState) {
    context.edexDataStore.edit { prefs ->
      prefs[Keys.THEME_ID] = cloudState.resolvedThemeId().name
      prefs[Keys.TELEMETRY_THEME_ID] = cloudState.resolvedTelemetryThemeId().name
      prefs[Keys.SOUND_ENABLED] = cloudState.soundEnabled
      prefs[Keys.SCANLINES_ENABLED] = cloudState.scanlinesEnabled
      prefs[Keys.SHOW_KEYBOARD] = cloudState.showKeyboard
      prefs[Keys.GLITCH_SHADER_ENABLED] = cloudState.glitchShaderEnabled
      prefs[Keys.GLITCH_LOAD_THRESHOLD] =
        cloudState.glitchLoadThreshold.toFloat().coerceIn(20f, 95f)
      prefs[Keys.ACTIVE_MACRO_DECK_ID] = cloudState.activeMacroDeckId.ifBlank { "recon" }
      val profileId = cloudState.activeHudProfileId
      if (profileId.isNullOrBlank()) {
        prefs.remove(Keys.ACTIVE_HUD_PROFILE_ID)
      } else {
        prefs[Keys.ACTIVE_HUD_PROFILE_ID] = profileId
      }
    }
  }

  companion object {
    internal fun encodeMacroDecks(decks: List<MacroDeck>): String {
      val root = JSONArray()
      decks.forEach { deck ->
        val obj = JSONObject()
        obj.put("id", deck.id)
        obj.put("name", deck.name)
        val macrosArr = JSONArray()
        deck.macros.forEach { macro ->
          val mObj = JSONObject()
          mObj.put("id", macro.id)
          mObj.put("label", macro.label)
          mObj.put("commandTemplate", macro.commandTemplate)
          mObj.put("requiresConfirm", macro.requiresConfirm)
          mObj.put("accentRole", macro.accentRole.name)
          macrosArr.put(mObj)
        }
        obj.put("macros", macrosArr)
        root.put(obj)
      }
      return root.toString()
    }

    internal fun decodeMacroDecks(raw: String?): List<MacroDeck>? {
      if (raw.isNullOrBlank()) return null
      return runCatching {
        val arr = JSONArray(raw)
        val out = mutableListOf<MacroDeck>()
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val id = obj.optString("id").trim()
          val name = obj.optString("name").trim()
          if (id.isEmpty() || name.isEmpty()) continue
          val macrosArr = obj.optJSONArray("macros") ?: JSONArray()
          val macros = mutableListOf<ShellMacro>()
          for (j in 0 until macrosArr.length()) {
            val mObj = macrosArr.optJSONObject(j) ?: continue
            val mId = mObj.optString("id").trim()
            val mLabel = mObj.optString("label").trim()
            val mCmd = mObj.optString("commandTemplate").trim()
            if (mId.isEmpty() || mLabel.isEmpty() || mCmd.isEmpty()) continue
            val reqConfirm = mObj.optBoolean("requiresConfirm", false)
            val roleName = mObj.optString("accentRole", MacroAccentRole.PRIMARY.name)
            val role =
              MacroAccentRole.entries.firstOrNull { it.name == roleName } ?: MacroAccentRole.PRIMARY
            macros += ShellMacro(mId, mLabel, mCmd, reqConfirm, role)
          }
          out += MacroDeck(id = id, name = name, macros = macros)
        }
        out.takeIf { it.isNotEmpty() }
      }.getOrNull()
    }

    internal fun encodeHudProfiles(profiles: List<HudProfile>): String {
      val root = JSONArray()
      profiles.forEach { profile ->
        val obj = JSONObject()
        obj.put("id", profile.id)
        obj.put("name", profile.name)
        obj.put("themeId", profile.themeId.name)
        obj.put("telemetryThemeId", profile.telemetryThemeId.name)
        obj.put("glitchShaderEnabled", profile.glitchShaderEnabled)
        obj.put("glitchLoadThreshold", profile.glitchLoadThreshold.toDouble())
        obj.put("scanlinesEnabled", profile.scanlinesEnabled)
        obj.put("soundEnabled", profile.soundEnabled)
        obj.put("activeDeckId", profile.activeDeckId)
        obj.put("isBuiltIn", profile.isBuiltIn)
        root.put(obj)
      }
      return root.toString()
    }

    internal fun decodeHudProfiles(raw: String?): List<HudProfile>? {
      if (raw.isNullOrBlank()) return null
      return runCatching {
        val arr = JSONArray(raw)
        val out = mutableListOf<HudProfile>()
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val id = obj.optString("id").trim()
          val name = obj.optString("name").trim()
          if (id.isEmpty() || name.isEmpty()) continue
          val themeName = obj.optString("themeId", EdexThemeId.TRON.name)
          val telName = obj.optString("telemetryThemeId", TelemetryThemeId.SYNC_HUD.name)
          val themeId = EdexThemeId.entries.firstOrNull { it.name == themeName } ?: EdexThemeId.TRON
          val telId =
            TelemetryThemeId.entries.firstOrNull { it.name == telName } ?: TelemetryThemeId.SYNC_HUD
          out +=
            HudProfile(
              id = id,
              name = name,
              themeId = themeId,
              telemetryThemeId = telId,
              glitchShaderEnabled = obj.optBoolean("glitchShaderEnabled", true),
              glitchLoadThreshold =
                obj.optDouble("glitchLoadThreshold", 75.0).toFloat().coerceIn(20f, 95f),
              scanlinesEnabled = obj.optBoolean("scanlinesEnabled", true),
              soundEnabled = obj.optBoolean("soundEnabled", true),
              activeDeckId = obj.optString("activeDeckId", "recon").ifBlank { "recon" },
              isBuiltIn = obj.optBoolean("isBuiltIn", false),
            )
        }
        out.takeIf { it.isNotEmpty() }
      }.getOrNull()
    }
  }
}
