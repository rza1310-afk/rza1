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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HudProfile
import com.example.ui.theme.EdexPalettes
import com.example.ui.theme.LocalEdexPalette

@Composable
fun HudProfilesSection(
  profiles: List<HudProfile>,
  activeProfileId: String?,
  onApplyProfile: (HudProfile) -> Unit,
  onSaveCurrentProfile: (String) -> Unit,
  onDeleteProfile: (HudProfile) -> Unit,
) {
  val palette = LocalEdexPalette.current
  var newProfileName by remember { mutableStateOf("") }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = stringResource(R.string.settings_profiles_label),
      style = MaterialTheme.typography.labelMedium,
      color = palette.primary,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = newProfileName,
        onValueChange = { newProfileName = it },
        singleLine = true,
        label = { Text("Profile name") },
        textStyle = MaterialTheme.typography.bodySmall.copy(color = palette.textPrimary),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.primaryBright,
            unfocusedBorderColor = palette.border,
            focusedLabelColor = palette.primary,
            unfocusedLabelColor = palette.textMuted,
          ),
        modifier = Modifier.weight(1f).testTag("hud_profile_name_input"),
      )
      Button(
        onClick = {
          val trimmed = newProfileName.trim()
          if (trimmed.isNotEmpty()) {
            onSaveCurrentProfile(trimmed)
            newProfileName = ""
          }
        },
        enabled = newProfileName.isNotBlank(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = palette.primaryBright,
            contentColor = palette.background,
          ),
        shape = CutCornerShape(6.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("save_hud_profile_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.BookmarkAdd,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = stringResource(R.string.profile_save_button), style = MaterialTheme.typography.labelSmall)
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      profiles.forEach { profile ->
        val selected = profile.id == activeProfileId
        val hudPreview = EdexPalettes.byId(profile.themeId)
        val telPreview = EdexPalettes.forTelemetryTheme(profile.telemetryThemeId, profile.themeId)
        Surface(
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("hud_profile_option_${profile.id}")
              .clickable { onApplyProfile(profile) },
          color = if (selected) hudPreview.primaryDim.copy(alpha = 0.55f) else palette.background,
          border = BorderStroke(1.dp, if (selected) hudPreview.primaryBright else palette.border),
          shape = CutCornerShape(6.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
              Box(
                modifier =
                  Modifier.size(12.dp)
                    .background(hudPreview.primaryBright, CutCornerShape(2.dp))
                    .border(1.dp, hudPreview.border, CutCornerShape(2.dp))
              )
              Box(
                modifier =
                  Modifier.size(12.dp).background(telPreview.secondary, CutCornerShape(2.dp))
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = profile.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) hudPreview.primaryBright else palette.textPrimary,
              )
              Text(
                text =
                  "${profile.themeId.displayName} • ${profile.telemetryThemeId.shortLabel} • DECK ${profile.activeDeckId.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
              )
            }
            if (!profile.isBuiltIn) {
              IconButton(
                onClick = { onDeleteProfile(profile) },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("delete_hud_profile_${profile.id}"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Delete,
                  contentDescription = "Delete profile ${profile.name}",
                  tint = palette.danger,
                  modifier = Modifier.size(16.dp),
                )
              }
            } else if (selected) {
              Text(
                text = "ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = hudPreview.primaryBright,
              )
            }
          }
        }
      }
    }
  }
}
