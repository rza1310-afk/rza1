package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import kotlinx.coroutines.flow.Flow
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
    context.edexDataStore.data.map { prefs ->
      val themeName = prefs[Keys.THEME_ID] ?: EdexThemeId.TRON.name
      val themeId =
        EdexThemeId.entries.firstOrNull { it.name == themeName } ?: EdexThemeId.TRON
      val telThemeName = prefs[Keys.TELEMETRY_THEME_ID] ?: TelemetryThemeId.SYNC_HUD.name
      val telemetryThemeId =
        TelemetryThemeId.entries.firstOrNull { it.name == telThemeName }
          ?: TelemetryThemeId.SYNC_HUD
      val decks = decodeMacroDecks(prefs[Keys.MACRO_DECKS_JSON])
      val rawDeckId = prefs[Keys.ACTIVE_MACRO_DECK_ID]
      val activeDeckId =
        if (rawDeckId != null && decks.any { it.id == rawDeckId }) rawDeckId
        else decks.firstOrNull()?.id ?: "recon"
      val profiles = decodeHudProfiles(prefs[Keys.HUD_PROFILES_JSON])
      val rawProfileId = prefs[Keys.ACTIVE_HUD_PROFILE_ID]
      val activeProfileId =
        rawProfileId?.takeIf { id -> profiles.any { it.id == id } }

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
        macroDecks = decks,
        activeHudProfileId = activeProfileId,
        hudProfiles = profiles,
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
    val safeDecks = decks.ifEmpty { EdexDefaults.defaultMacroDecks() }
    val encoded = encodeMacroDecks(safeDecks)
    context.edexDataStore.edit {
      it[Keys.MACRO_DECKS_JSON] = encoded
      if (activeDeckId != null) {
        it[Keys.ACTIVE_MACRO_DECK_ID] = activeDeckId
      }
    }
  }

  suspend fun applyHudProfile(profile: HudProfile) {
    context.edexDataStore.edit {
      it[Keys.THEME_ID] = profile.themeId.name
      it[Keys.TELEMETRY_THEME_ID] = profile.telemetryThemeId.name
      it[Keys.GLITCH_SHADER_ENABLED] = profile.glitchShaderEnabled
      it[Keys.GLITCH_LOAD_THRESHOLD] = profile.glitchLoadThreshold.coerceIn(20f, 95f)
      it[Keys.SCANLINES_ENABLED] = profile.scanlinesEnabled
      it[Keys.SOUND_ENABLED] = profile.soundEnabled
      it[Keys.ACTIVE_MACRO_DECK_ID] = profile.activeDeckId
      it[Keys.ACTIVE_HUD_PROFILE_ID] = profile.id
    }
  }

  suspend fun saveHudProfiles(profiles: List<HudProfile>, activeProfileId: String? = null) {
    val safeProfiles = profiles.ifEmpty { EdexDefaults.defaultHudProfiles() }
    val encoded = encodeHudProfiles(safeProfiles)
    context.edexDataStore.edit {
      it[Keys.HUD_PROFILES_JSON] = encoded
      if (activeProfileId != null) {
        it[Keys.ACTIVE_HUD_PROFILE_ID] = activeProfileId
      }
    }
  }

  companion object {
    fun encodeMacroDecks(decks: List<MacroDeck>): String {
      val arr = JSONArray()
      decks.forEach { deck ->
        val deckObj =
          JSONObject().apply {
            put("id", deck.id)
            put("name", deck.name)
            val macrosArr = JSONArray()
            deck.macros.forEach { macro ->
              macrosArr.put(
                JSONObject().apply {
                  put("id", macro.id)
                  put("label", macro.label)
                  put("commandTemplate", macro.commandTemplate)
                  put("requiresConfirm", macro.requiresConfirm)
                  put("accentRole", macro.accentRole.name)
                }
              )
            }
            put("macros", macrosArr)
          }
        arr.put(deckObj)
      }
      return arr.toString()
    }

    fun decodeMacroDecks(raw: String?): List<MacroDeck> {
      if (raw.isNullOrBlank()) return EdexDefaults.defaultMacroDecks()
      return runCatching {
        val arr = JSONArray(raw)
        val parsed =
          buildList {
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              val id = obj.optString("id").ifBlank { "deck_$i" }
              val name = obj.optString("name").ifBlank { "DECK $i" }
              val macrosArr = obj.optJSONArray("macros") ?: JSONArray()
              val macros =
                buildList {
                  for (j in 0 until macrosArr.length()) {
                    val mObj = macrosArr.optJSONObject(j) ?: continue
                    val label = mObj.optString("label").trim()
                    val cmd = mObj.optString("commandTemplate").trim()
                    if (label.isEmpty() || cmd.isEmpty()) continue
                    val roleName = mObj.optString("accentRole")
                    val role =
                      MacroAccentRole.entries.firstOrNull { it.name == roleName }
                        ?: MacroAccentRole.PRIMARY
                    add(
                      ShellMacro(
                        id = mObj.optString("id").ifBlank { "macro_${i}_$j" },
                        label = label,
                        commandTemplate = cmd,
                        requiresConfirm = mObj.optBoolean("requiresConfirm", false),
                        accentRole = role,
                      )
                    )
                  }
                }
              add(MacroDeck(id = id, name = name, macros = macros))
            }
          }
        parsed.ifEmpty { EdexDefaults.defaultMacroDecks() }
      }.getOrElse { EdexDefaults.defaultMacroDecks() }
    }

    fun encodeHudProfiles(profiles: List<HudProfile>): String {
      val arr = JSONArray()
      profiles.forEach { p ->
        arr.put(
          JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("themeId", p.themeId.name)
            put("telemetryThemeId", p.telemetryThemeId.name)
            put("glitchShaderEnabled", p.glitchShaderEnabled)
            put("glitchLoadThreshold", p.glitchLoadThreshold.toDouble())
            put("scanlinesEnabled", p.scanlinesEnabled)
            put("soundEnabled", p.soundEnabled)
            put("activeDeckId", p.activeDeckId)
            put("isBuiltIn", p.isBuiltIn)
          }
        )
      }
      return arr.toString()
    }

    fun decodeHudProfiles(raw: String?): List<HudProfile> {
      if (raw.isNullOrBlank()) return EdexDefaults.defaultHudProfiles()
      return runCatching {
        val arr = JSONArray(raw)
        val parsed =
          buildList {
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              val id = obj.optString("id").trim()
              val name = obj.optString("name").trim()
              if (id.isEmpty() || name.isEmpty()) continue
              val theme =
                EdexThemeId.entries.firstOrNull { it.name == obj.optString("themeId") }
                  ?: EdexThemeId.TRON
              val telTheme =
                TelemetryThemeId.entries.firstOrNull {
                  it.name == obj.optString("telemetryThemeId")
                } ?: TelemetryThemeId.SYNC_HUD
              add(
                HudProfile(
                  id = id,
                  name = name,
                  themeId = theme,
                  telemetryThemeId = telTheme,
                  glitchShaderEnabled = obj.optBoolean("glitchShaderEnabled", true),
                  glitchLoadThreshold =
                    obj.optDouble("glitchLoadThreshold", 75.0).toFloat().coerceIn(20f, 95f),
                  scanlinesEnabled = obj.optBoolean("scanlinesEnabled", true),
                  soundEnabled = obj.optBoolean("soundEnabled", true),
                  activeDeckId = obj.optString("activeDeckId").ifBlank { "recon" },
                  isBuiltIn = obj.optBoolean("isBuiltIn", false),
                )
              )
            }
          }
        parsed.ifEmpty { EdexDefaults.defaultHudProfiles() }
      }.getOrElse { EdexDefaults.defaultHudProfiles() }
    }
  }
}
