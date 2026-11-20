package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.EdexUserPreferences
import com.example.model.HudProfile
import com.example.ui.theme.EdexPalettes
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.LocalEdexPalette
import com.example.ui.theme.TelemetryThemeId

@Composable
fun SettingsDialog(
  preferences: EdexUserPreferences,
  isTelemetryServiceRunning: Boolean,
  onSelectTheme: (EdexThemeId) -> Unit,
  onSelectTelemetryTheme: (TelemetryThemeId) -> Unit,
  onApplyHudProfile: (HudProfile) -> Unit = {},
  onSaveCurrentHudProfile: (String) -> Unit = {},
  onDeleteHudProfile: (HudProfile) -> Unit = {},
  onToggleSound: () -> Unit,
  onToggleScanlines: () -> Unit,
  onToggleKeyboard: () -> Unit,
  onToggleTelemetryService: () -> Unit,
  onToggleGlitchShader: () -> Unit,
  onChangeGlitchThreshold: (Float) -> Unit,
  onReplayBoot: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalEdexPalette.current

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 24.dp),
      color = palette.surface,
      border = BorderStroke(1.dp, palette.primaryBright),
      shape = CutCornerShape(10.dp),
    ) {
      Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = palette.primaryBright,
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(R.string.cd_close_dialog),
              tint = palette.primary,
            )
          }
        }

        HudProfilesSection(
          profiles = preferences.hudProfiles,
          activeProfileId = preferences.activeHudProfileId,
          onApplyProfile = onApplyHudProfile,
          onSaveCurrentProfile = onSaveCurrentHudProfile,
          onDeleteProfile = onDeleteHudProfile,
        )

        Text(
          text = stringResource(R.string.settings_theme_label),
          style = MaterialTheme.typography.labelMedium,
          color = palette.primary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          EdexThemeId.entries.forEach { themeId ->
            val preview = EdexPalettes.byId(themeId)
            val selected = preferences.themeId == themeId
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .testTag("theme_option_${themeId.name.lowercase()}")
                  .clickable { onSelectTheme(themeId) },
              color = if (selected) palette.primaryDim.copy(alpha = 0.55f) else palette.background,
              border = BorderStroke(1.dp, if (selected) preview.primaryBright else palette.border),
              shape = CutCornerShape(6.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(22.dp)
                      .background(preview.background, CutCornerShape(4.dp))
                      .border(2.dp, preview.primaryBright, CutCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = themeId.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) preview.primaryBright else palette.textPrimary,
                  )
                  Text(
                    text = themeId.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                  )
                }
                if (selected) {
                  Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = preview.primaryBright,
                  )
                }
              }
            }
          }
        }

        Text(
          text = stringResource(R.string.settings_telemetry_theme_label),
          style = MaterialTheme.typography.labelMedium,
          color = palette.primary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          TelemetryThemeId.entries.forEach { themeId ->
            val preview = EdexPalettes.forTelemetryTheme(themeId, preferences.themeId)
            val selected = preferences.telemetryThemeId == themeId
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .testTag("telemetry_theme_option_${themeId.name.lowercase()}")
                  .clickable { onSelectTelemetryTheme(themeId) },
              color = if (selected) preview.primaryDim.copy(alpha = 0.55f) else palette.background,
              border = BorderStroke(1.dp, if (selected) preview.primaryBright else palette.border),
              shape = CutCornerShape(6.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Box(
                    modifier =
                      Modifier.size(16.dp)
                        .background(preview.background, CutCornerShape(3.dp))
                        .border(1.5.dp, preview.primaryBright, CutCornerShape(3.dp))
                  )
                  Box(
                    modifier =
                      Modifier.size(16.dp)
                        .background(preview.secondary, CutCornerShape(3.dp))
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = themeId.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) preview.primaryBright else palette.textPrimary,
                  )
                  Text(
                    text = themeId.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                  )
                }
                if (selected) {
                  Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = preview.primaryBright,
                  )
                }
              }
            }
          }
        }

        SettingSwitchRow(
          label = stringResource(R.string.settings_audio_label),
          checked = preferences.soundEnabled,
          onToggle = onToggleSound,
          testTag = "switch_sound",
        )
        SettingSwitchRow(
          label = stringResource(R.string.settings_scanlines_label),
          checked = preferences.scanlinesEnabled,
          onToggle = onToggleScanlines,
          testTag = "switch_scanlines",
        )
        SettingSwitchRow(
          label = stringResource(R.string.settings_keyboard_label),
          checked = preferences.showKeyboard,
          onToggle = onToggleKeyboard,
          testTag = "switch_keyboard",
        )
        SettingSwitchRow(
          label = stringResource(R.string.settings_daemon_label),
          checked = isTelemetryServiceRunning,
          onToggle = onToggleTelemetryService,
          testTag = "switch_telemetry_service",
        )
        SettingSwitchRow(
          label = stringResource(R.string.settings_glitch_label),
          checked = preferences.glitchShaderEnabled,
          onToggle = onToggleGlitchShader,
          testTag = "switch_glitch_shader",
        )
        var sliderThreshold by
          remember(preferences.glitchLoadThreshold) {
            mutableFloatStateOf(preferences.glitchLoadThreshold)
          }
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(palette.background.copy(alpha = 0.7f), CutCornerShape(6.dp))
              .border(1.dp, palette.gridLine, CutCornerShape(6.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.settings_glitch_threshold_label),
              style = MaterialTheme.typography.labelMedium,
              color = palette.textPrimary,
            )
            Text(
              text = "${sliderThreshold.toInt()}%",
              style = MaterialTheme.typography.labelMedium,
              color = palette.primaryBright,
            )
          }
          Slider(
            value = sliderThreshold,
            onValueChange = { sliderThreshold = it },
            onValueChangeFinished = { onChangeGlitchThreshold(sliderThreshold) },
            valueRange = 20f..95f,
            steps = 14,
            enabled = preferences.glitchShaderEnabled,
            modifier = Modifier.testTag("slider_glitch_threshold"),
            colors =
              SliderDefaults.colors(
                thumbColor = palette.primaryBright,
                activeTrackColor = palette.primaryBright,
                inactiveTrackColor = palette.surfaceElevated,
              ),
          )
        }

        OutlinedButton(
          onClick = onReplayBoot,
          border = BorderStroke(1.dp, palette.primary),
          shape = CutCornerShape(6.dp),
          modifier =
            Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("replay_boot_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.RestartAlt,
            contentDescription = null,
            tint = palette.primary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.settings_boot_label),
            color = palette.primary,
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun SettingSwitchRow(
  label: String,
  checked: Boolean,
  onToggle: () -> Unit,
  testTag: String,
) {
  val palette = LocalEdexPalette.current
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(palette.background.copy(alpha = 0.7f), CutCornerShape(6.dp))
        .border(1.dp, palette.gridLine, CutCornerShape(6.dp))
        .clickable(onClick = onToggle)
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = palette.textPrimary,
      modifier = Modifier.weight(1f),
    )
    Switch(
      checked = checked,
      onCheckedChange = { onToggle() },
      modifier = Modifier.testTag(testTag),
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = palette.background,
          checkedTrackColor = palette.primaryBright,
          uncheckedThumbColor = palette.textMuted,
          uncheckedTrackColor = palette.surfaceElevated,
        ),
    )
  }
}
