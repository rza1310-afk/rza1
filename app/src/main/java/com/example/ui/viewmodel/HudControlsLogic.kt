package com.example.ui.viewmodel

import com.example.model.EdexUserPreferences
import com.example.model.HudProfile
import com.example.model.MacroAccentRole
import com.example.model.MacroDeck
import com.example.model.NetworkConnectionNode
import com.example.model.ShellMacro

data class MacroEditorState(
  val deckId: String,
  val deckName: String,
  val existingMacro: ShellMacro? = null,
)

data class PendingMacroConfirm(
  val macro: ShellMacro,
  val resolvedCommand: String,
)

internal fun extractHostFromEndpoint(endpoint: String): String {
  val trimmed = endpoint.trim()
  if (trimmed.isEmpty()) return "1.1.1.1"
  if (trimmed.startsWith("[")) {
    val closing = trimmed.indexOf(']')
    if (closing > 1) return trimmed.substring(1, closing)
  }
  val colonCount = trimmed.count { it == ':' }
  return if (colonCount == 1) trimmed.substringBefore(':') else trimmed
}

internal fun resolveMacroTokens(
  template: String,
  selectedNode: NetworkConnectionNode?,
  gatewayIp: String,
  cwd: String,
  interfaceName: String,
): String {
  val selectedIp =
    when {
      selectedNode != null -> extractHostFromEndpoint(selectedNode.endpoint)
      gatewayIp.isNotBlank() && gatewayIp != "--" -> gatewayIp
      else -> "1.1.1.1"
    }
  val safeCwd = cwd.ifBlank { "/" }
  val safeIface = interfaceName.ifBlank { "lo" }
  return template
    .replace("{selected_ip}", selectedIp)
    .replace("{cwd}", safeCwd)
    .replace("{iface}", safeIface)
    .trim()
}

internal fun upsertMacroInDecks(
  decks: List<MacroDeck>,
  deckId: String,
  existingMacroId: String?,
  label: String,
  commandTemplate: String,
  requiresConfirm: Boolean,
  accentRole: MacroAccentRole,
  newIdGenerator: () -> String,
): List<MacroDeck> {
  val cleanLabel = label.trim()
  val cleanCmd = commandTemplate.trim()
  if (cleanLabel.isEmpty() || cleanCmd.isEmpty()) return decks
  return decks.map { deck ->
    if (deck.id != deckId) {
      deck
    } else if (existingMacroId != null) {
      deck.copy(
        macros =
          deck.macros.map { macro ->
            if (macro.id == existingMacroId) {
              macro.copy(
                label = cleanLabel,
                commandTemplate = cleanCmd,
                requiresConfirm = requiresConfirm,
                accentRole = accentRole,
              )
            } else {
              macro
            }
          }
      )
    } else {
      val created =
        ShellMacro(
          id = newIdGenerator(),
          label = cleanLabel,
          commandTemplate = cleanCmd,
          requiresConfirm = requiresConfirm,
          accentRole = accentRole,
        )
      deck.copy(macros = deck.macros + created)
    }
  }
}

internal fun deleteMacroFromDecks(
  decks: List<MacroDeck>,
  deckId: String,
  macroId: String,
): List<MacroDeck> =
  decks.map { deck ->
    if (deck.id == deckId) deck.copy(macros = deck.macros.filterNot { it.id == macroId })
    else deck
  }

internal fun createHudProfileSnapshot(
  id: String,
  name: String,
  prefs: EdexUserPreferences,
): HudProfile =
  HudProfile(
    id = id,
    name = name.trim().uppercase(),
    themeId = prefs.themeId,
    telemetryThemeId = prefs.telemetryThemeId,
    glitchShaderEnabled = prefs.glitchShaderEnabled,
    glitchLoadThreshold = prefs.glitchLoadThreshold,
    scanlinesEnabled = prefs.scanlinesEnabled,
    soundEnabled = prefs.soundEnabled,
    activeDeckId = prefs.activeMacroDeckId,
    isBuiltIn = false,
  )
