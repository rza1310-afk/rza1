package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.MacroAccentRole
import com.example.model.MacroDeck
import com.example.model.ShellMacro
import com.example.ui.theme.LocalEdexPalette

@Composable
fun MacroDeckBar(
  decks: List<MacroDeck>,
  activeDeckId: String,
  onSelectDeck: (String) -> Unit,
  onRunMacro: (ShellMacro) -> Unit,
  onAddMacro: () -> Unit,
  onEditMacro: (ShellMacro) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  val activeDeck = decks.firstOrNull { it.id == activeDeckId } ?: decks.firstOrNull()

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Deck Selector + "+ MACRO" button
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      decks.forEach { deck ->
        val selected = deck.id == activeDeck?.id
        val shape = CutCornerShape(4.dp)
        Surface(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(shape)
              .testTag("macro_deck_${deck.id}")
              .clickable { onSelectDeck(deck.id) },
          color = if (selected) palette.primaryDim.copy(alpha = 0.72f) else palette.background.copy(alpha = 0.6f),
          border = BorderStroke(1.dp, if (selected) palette.primaryBright else palette.gridLine),
          shape = shape,
        ) {
          Text(
            text = deck.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.primaryBright else palette.textMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
          )
        }
      }
      val addShape = CutCornerShape(4.dp)
      Surface(
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(addShape)
            .testTag("macro_add_button")
            .clickable(onClick = onAddMacro),
        color = palette.primaryBright.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, palette.primaryBright),
        shape = addShape,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.macro_add_button),
            tint = palette.primaryBright,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "MACRO",
            style = MaterialTheme.typography.labelSmall,
            color = palette.primaryBright,
            maxLines = 1,
          )
        }
      }
    }

    // Programmable Macro Chips
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      activeDeck?.macros.orEmpty().forEach { macro ->
        val accent =
          when (macro.accentRole) {
            MacroAccentRole.PRIMARY -> palette.primary
            MacroAccentRole.SUCCESS -> palette.success
            MacroAccentRole.WARNING -> palette.warning
            MacroAccentRole.DANGER -> palette.danger
          }
        val shape = CutCornerShape(4.dp)
        Surface(
          color = palette.surfaceElevated,
          border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
          shape = shape,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clip(shape)
                  .testTag("macro_chip_${macro.id}")
                  .clickable { onRunMacro(macro) }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = macro.label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Box(
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("macro_edit_${macro.id}")
                  .clickable { onEditMacro(macro) },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit ${macro.label}",
                tint = palette.textMuted,
                modifier = Modifier.size(12.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MacroEditorDialog(
  initialMacro: ShellMacro?,
  deckName: String,
  onSave: (label: String, commandTemplate: String, requiresConfirm: Boolean, accentRole: MacroAccentRole) -> Unit,
  onDelete: (() -> Unit)?,
  onDismiss: () -> Unit,
) {
  val palette = LocalEdexPalette.current
  var label by remember(initialMacro?.id) { mutableStateOf(initialMacro?.label.orEmpty()) }
  var command by remember(initialMacro?.id) { mutableStateOf(initialMacro?.commandTemplate.orEmpty()) }
  var requiresConfirm by remember(initialMacro?.id) {
    mutableStateOf(initialMacro?.requiresConfirm ?: false)
  }
  var accentRole by remember(initialMacro?.id) {
    mutableStateOf(initialMacro?.accentRole ?: MacroAccentRole.PRIMARY)
  }

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = if (initialMacro == null) "NEW MACRO // $deckName" else "EDIT MACRO // $deckName",
          style = MaterialTheme.typography.titleMedium,
          color = palette.primaryBright,
        )

        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          singleLine = true,
          label = { Text("Macro Label (e.g. ping node)") },
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = palette.primaryBright,
              unfocusedBorderColor = palette.border,
              focusedLabelColor = palette.primary,
              unfocusedLabelColor = palette.textMuted,
            ),
          modifier = Modifier.fillMaxWidth().testTag("macro_label_input"),
        )

        OutlinedTextField(
          value = command,
          onValueChange = { command = it },
          singleLine = true,
          label = { Text("Command Template (supports {selected_ip}, {cwd}, {iface})") },
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = palette.primaryBright,
              unfocusedBorderColor = palette.border,
              focusedLabelColor = palette.primary,
              unfocusedLabelColor = palette.textMuted,
            ),
          modifier = Modifier.fillMaxWidth().testTag("macro_command_input"),
        )

        // Token helper chips
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf("{selected_ip}", "{cwd}", "{iface}").forEach { token ->
            Surface(
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .clickable { command = if (command.isBlank()) token else "$command $token" },
              color = palette.background,
              border = BorderStroke(1.dp, palette.gridLine),
              shape = CutCornerShape(4.dp),
            ) {
              Text(
                text = "+ $token",
                style = MaterialTheme.typography.labelSmall,
                color = palette.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }

        Text(
          text = "CHIP COLOR ROLE",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textMuted,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MacroAccentRole.entries.forEach { role ->
            val selected = accentRole == role
            val color =
              when (role) {
                MacroAccentRole.PRIMARY -> palette.primaryBright
                MacroAccentRole.SUCCESS -> palette.success
                MacroAccentRole.WARNING -> palette.warning
                MacroAccentRole.DANGER -> palette.danger
              }
            Surface(
              modifier =
                Modifier.minimumInteractiveComponentSize().clickable { accentRole = role },
              color = if (selected) color.copy(alpha = 0.22f) else palette.background,
              border = BorderStroke(1.dp, if (selected) color else palette.gridLine),
              shape = CutCornerShape(4.dp),
            ) {
              Text(
                text = role.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              )
            }
          }
        }

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(palette.background.copy(alpha = 0.7f), CutCornerShape(6.dp))
              .border(1.dp, palette.gridLine, CutCornerShape(6.dp))
              .clickable { requiresConfirm = !requiresConfirm }
              .padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Require Confirmation Before Execution",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = requiresConfirm,
            onCheckedChange = { requiresConfirm = it },
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = palette.background,
                checkedTrackColor = palette.primaryBright,
              ),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (onDelete != null) {
            OutlinedButton(
              onClick = onDelete,
              border = BorderStroke(1.dp, palette.danger),
              shape = CutCornerShape(6.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("macro_delete_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete Macro",
                tint = palette.danger,
                modifier = Modifier.size(16.dp),
              )
            }
          } else {
            Spacer(modifier = Modifier.width(1.dp))
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = onDismiss,
              border = BorderStroke(1.dp, palette.border),
              shape = CutCornerShape(6.dp),
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Text("CANCEL", color = palette.textMuted)
            }
            val valid = label.isNotBlank() && command.isNotBlank()
            Button(
              onClick = { onSave(label.trim(), command.trim(), requiresConfirm, accentRole) },
              enabled = valid,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = palette.primaryBright,
                  contentColor = palette.background,
                ),
              shape = CutCornerShape(6.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("macro_save_button"),
            ) {
              Text("SAVE")
            }
          }
        }
      }
    }
  }
}

@Composable
fun MacroConfirmDialog(
  macro: ShellMacro,
  resolvedCommand: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalEdexPalette.current
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = palette.surface,
      border = BorderStroke(1.dp, palette.warning),
      shape = CutCornerShape(10.dp),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = palette.warning,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.macro_confirm_title),
            style = MaterialTheme.typography.titleSmall,
            color = palette.warning,
          )
        }
        Text(
          text = "Macro '${macro.label}' will execute:",
          style = MaterialTheme.typography.bodySmall,
          color = palette.textMuted,
        )
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(palette.background, CutCornerShape(4.dp))
              .border(1.dp, palette.gridLine, CutCornerShape(4.dp))
              .padding(10.dp)
        ) {
          Text(
            text = "$ $resolvedCommand",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.primaryBright,
          )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(1.dp, palette.border),
            shape = CutCornerShape(6.dp),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text("ABORT", color = palette.textMuted)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onConfirm,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = palette.warning,
                contentColor = palette.background,
              ),
            shape = CutCornerShape(6.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("macro_confirm_execute"),
          ) {
            Text("EXECUTE")
          }
        }
      }
    }
  }
}
