package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalEdexPalette

@Composable
fun SciFiKeyboard(
  onKeyPress: (String) -> Unit,
  onBackspace: () -> Unit,
  onEnter: () -> Unit,
  onTabComplete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  var isShifted by remember { mutableStateOf(false) }
  var isSymbols by remember { mutableStateOf(false) }

  val rows =
    if (isSymbols) {
      listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
        listOf(".", ",", "?", "!", "'", "|", "~", "_", "\\", "*"),
      )
    } else {
      val base =
        listOf(
          listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
          listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "-"),
          listOf("z", "x", "c", "v", "b", "n", "m", ".", "/", "_"),
        )
      if (isShifted) base.map { r -> r.map { it.uppercase() } } else base
    }

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = palette.surface.copy(alpha = 0.96f),
    border = BorderStroke(1.dp, palette.border),
    shape = CutCornerShape(topStart = 8.dp, topEnd = 8.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      rows.forEach { rowKeys ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          rowKeys.forEach { keyLabel ->
            KeyCap(
              label = keyLabel,
              onClick = { onKeyPress(keyLabel) },
              modifier = Modifier.weight(1f).testTag("kbd_key_$keyLabel"),
            )
          }
        }
      }

      // Action Bottom Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        KeyCap(
          label = if (isSymbols) "ABC" else "123",
          highlighted = isSymbols,
          onClick = { isSymbols = !isSymbols },
          modifier = Modifier.weight(1.2f).testTag("kbd_symbols"),
        )
        KeyCap(
          label = "SHIFT",
          highlighted = isShifted,
          onClick = { isShifted = !isShifted },
          modifier = Modifier.weight(1.3f).testTag("kbd_shift"),
        )
        KeyCap(
          label = "TAB",
          onClick = onTabComplete,
          modifier = Modifier.weight(1.1f).testTag("kbd_tab"),
        )
        KeyCap(
          label = "SPACE",
          onClick = { onKeyPress(" ") },
          modifier = Modifier.weight(2.4f).testTag("kbd_space"),
        )
        KeyCap(
          label = "BKSP",
          onClick = onBackspace,
          modifier = Modifier.weight(1.3f).testTag("kbd_bksp"),
        )
        KeyCap(
          label = "EXEC",
          highlighted = true,
          onClick = onEnter,
          modifier = Modifier.weight(1.5f).testTag("kbd_enter"),
        )
      }
    }
  }
}

@Composable
private fun KeyCap(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
) {
  val palette = LocalEdexPalette.current
  Surface(
    modifier = modifier.height(40.dp).clickable(onClick = onClick),
    color = if (highlighted) palette.primaryDim else palette.background.copy(alpha = 0.85f),
    border = BorderStroke(1.dp, if (highlighted) palette.primaryBright else palette.border),
    shape = CutCornerShape(4.dp),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (highlighted) palette.primaryBright else palette.textPrimary,
        maxLines = 1,
      )
    }
  }
}
