package com.example.data

import androidx.annotation.Keep
import com.example.model.EdexUserPreferences
import com.example.model.HudProfile
import com.example.model.MacroAccentRole
import com.example.model.MacroDeck
import com.example.model.ShellMacro
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.TelemetryThemeId
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudUserHudState(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "OPERATOR",
  val themeId: String = EdexThemeId.TRON.name,
  val telemetryThemeId: String = TelemetryThemeId.SYNC_HUD.name,
  val soundEnabled: Boolean = true,
  val scanlinesEnabled: Boolean = true,
  val showKeyboard: Boolean = false,
  val glitchShaderEnabled: Boolean = true,
  val glitchLoadThreshold: Double = 75.0,
  val activeMacroDeckId: String = "recon",
  val activeHudProfileId: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedThemeId(): EdexThemeId =
    EdexThemeId.entries.firstOrNull { it.name == themeId } ?: EdexThemeId.TRON

  fun resolvedTelemetryThemeId(): TelemetryThemeId =
    TelemetryThemeId.entries.firstOrNull { it.name == telemetryThemeId } ?: TelemetryThemeId.SYNC_HUD
}

@Keep
@IgnoreExtraProperties
data class CloudHudProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val themeId: String = EdexThemeId.TRON.name,
  val telemetryThemeId: String = TelemetryThemeId.SYNC_HUD.name,
  val glitchShaderEnabled: Boolean = true,
  val glitchLoadThreshold: Double = 75.0,
  val scanlinesEnabled: Boolean = true,
  val soundEnabled: Boolean = true,
  val activeDeckId: String = "recon",
  val builtIn: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toDomain(): HudProfile? {
    val safeName = name.trim()
    if (id.isBlank() || safeName.isEmpty()) return null
    val resolvedTheme =
      EdexThemeId.entries.firstOrNull { it.name == themeId } ?: EdexThemeId.TRON
    val resolvedTelemetry =
      TelemetryThemeId.entries.firstOrNull { it.name == telemetryThemeId }
        ?: TelemetryThemeId.SYNC_HUD
    return HudProfile(
      id = id,
      name = safeName,
      themeId = resolvedTheme,
      telemetryThemeId = resolvedTelemetry,
      glitchShaderEnabled = glitchShaderEnabled,
      glitchLoadThreshold = glitchLoadThreshold.toFloat().coerceIn(20f, 95f),
      scanlinesEnabled = scanlinesEnabled,
      soundEnabled = soundEnabled,
      activeDeckId = activeDeckId.ifBlank { "recon" },
      isBuiltIn = builtIn,
    )
  }
}

@Keep
@IgnoreExtraProperties
data class CloudMacroDeck(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val sortOrder: Long = 0L,
  val macros: List<Map<String, Any?>> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toDomain(): MacroDeck? {
    val safeName = name.trim()
    if (id.isBlank() || safeName.isEmpty()) return null
    val parsedMacros =
      macros.mapNotNull { entry ->
        val mId = (entry["id"] as? String)?.trim().orEmpty()
        val label = (entry["label"] as? String)?.trim().orEmpty()
        val cmd = (entry["commandTemplate"] as? String)?.trim().orEmpty()
        if (mId.isEmpty() || label.isEmpty() || cmd.isEmpty()) return@mapNotNull null
        val reqConfirm = (entry["requiresConfirm"] as? Boolean) ?: false
        val roleRaw = (entry["accentRole"] as? String).orEmpty()
        val role =
          MacroAccentRole.entries.firstOrNull { it.name == roleRaw } ?: MacroAccentRole.PRIMARY
        ShellMacro(
          id = mId,
          label = label,
          commandTemplate = cmd,
          requiresConfirm = reqConfirm,
          accentRole = role,
        )
      }
    return MacroDeck(id = id, name = safeName, macros = parsedMacros)
  }
}

internal fun sanitizeFirestoreId(raw: String, fallbackPrefix: String): String {
  val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
  return if (cleaned.isNotEmpty()) cleaned else "${fallbackPrefix}_${System.currentTimeMillis()}"
}

internal fun buildUserHudCreateMap(
  userId: String,
  displayName: String,
  prefs: EdexUserPreferences,
): Map<String, Any> {
  val safeName = displayName.trim().ifEmpty { "OPERATOR" }.take(80)
  val raw =
    mutableMapOf<String, Any?>(
      "userId" to userId,
      "displayName" to safeName,
      "themeId" to prefs.themeId.name,
      "telemetryThemeId" to prefs.telemetryThemeId.name,
      "soundEnabled" to prefs.soundEnabled,
      "scanlinesEnabled" to prefs.scanlinesEnabled,
      "showKeyboard" to prefs.showKeyboard,
      "glitchShaderEnabled" to prefs.glitchShaderEnabled,
      "glitchLoadThreshold" to prefs.glitchLoadThreshold.coerceIn(20f, 95f).toDouble(),
      "activeMacroDeckId" to prefs.activeMacroDeckId.take(64).ifBlank { "recon" },
      "activeHudProfileId" to prefs.activeHudProfileId?.take(64)?.ifBlank { null },
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
  @Suppress("UNCHECKED_CAST")
  return raw.filterValues { it != null } as Map<String, Any>
}

internal fun HudProfile.toFirestoreCreateMap(userId: String): Map<String, Any> =
  mapOf(
    "userId" to userId,
    "name" to name.trim().ifEmpty { "PROFILE" }.take(50),
    "themeId" to themeId.name,
    "telemetryThemeId" to telemetryThemeId.name,
    "glitchShaderEnabled" to glitchShaderEnabled,
    "glitchLoadThreshold" to glitchLoadThreshold.coerceIn(20f, 95f).toDouble(),
    "scanlinesEnabled" to scanlinesEnabled,
    "soundEnabled" to soundEnabled,
    "activeDeckId" to activeDeckId.trim().ifEmpty { "recon" }.take(64),
    "builtIn" to isBuiltIn,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

internal fun ShellMacro.toFirestoreMap(): Map<String, Any> =
  mapOf(
    "id" to sanitizeFirestoreId(id, "m").take(64),
    "label" to label.trim().ifEmpty { "cmd" }.take(40),
    "commandTemplate" to commandTemplate.trim().ifEmpty { "help" }.take(300),
    "requiresConfirm" to requiresConfirm,
    "accentRole" to accentRole.name,
  )

internal fun MacroDeck.toFirestoreCreateMap(userId: String, sortOrder: Int): Map<String, Any> =
  mapOf(
    "userId" to userId,
    "name" to name.trim().ifEmpty { "DECK" }.take(40),
    "sortOrder" to sortOrder.coerceIn(0, 100),
    "macros" to macros.take(30).map { it.toFirestoreMap() },
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
