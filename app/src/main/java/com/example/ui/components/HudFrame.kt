package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalEdexPalette

@Composable
fun EdexHudPanel(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  headerActions: @Composable RowScope.() -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  val palette = LocalEdexPalette.current
  val cornerLength = 10.dp

  Box(
    modifier =
      modifier
        .background(
          brush =
            Brush.verticalGradient(
              colors = listOf(palette.surfaceElevated.copy(alpha = 0.92f), palette.surface.copy(alpha = 0.96f))
            ),
          shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        )
        .border(
          width = 1.dp,
          color = palette.border,
          shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        )
        .drawBehind {
          val stroke = 2.dp.toPx()
          val len = cornerLength.toPx()
          val accent = palette.primaryBright
          // Top-right corner bracket
          drawLine(accent, Offset(size.width - len, 0f), Offset(size.width, 0f), stroke)
          drawLine(accent, Offset(size.width, 0f), Offset(size.width, len), stroke)
          // Bottom-left corner bracket
          drawLine(accent, Offset(0f, size.height - len), Offset(0f, size.height), stroke)
          drawLine(accent, Offset(0f, size.height), Offset(len, size.height), stroke)
        }
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier.size(width = 4.dp, height = 14.dp).background(palette.primaryBright)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = subtitle,
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = headerActions)
      }
      Spacer(modifier = Modifier.height(6.dp))
      Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.gridLine))
      Spacer(modifier = Modifier.height(8.dp))
      content()
    }
  }
}

@Composable
fun EdexTopHeaderBar(
  timeFormatted: String,
  cpuPercent: Float,
  ramUsedMb: Long,
  ramTotalMb: Long,
  soundEnabled: Boolean,
  showKeyboard: Boolean,
  onToggleSound: () -> Unit,
  onToggleKeyboard: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  val ramPct = ((ramUsedMb.toFloat() / ramTotalMb.coerceAtLeast(1L)) * 100f).toInt()

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = palette.surface.copy(alpha = 0.95f),
    border = BorderStroke(1.dp, palette.border),
    shape = CutCornerShape(bottomEnd = 10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          modifier =
            Modifier.background(palette.primaryBright.copy(alpha = 0.16f), CutCornerShape(4.dp))
              .border(1.dp, palette.primaryBright, CutCornerShape(4.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "eDEX-UI",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = palette.primaryBright,
            maxLines = 1,
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = timeFormatted,
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary,
            maxLines = 1,
          )
          Text(
            text = "CPU ${cpuPercent.toInt()}% • RAM $ramPct%",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onToggleKeyboard,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_keyboard_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Keyboard,
            contentDescription = stringResource(R.string.cd_keyboard_toggle),
            tint = if (showKeyboard) palette.primaryBright else palette.textMuted,
          )
        }
        IconButton(
          onClick = onToggleSound,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_sound_button"),
        ) {
          Icon(
            imageVector =
              if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp
              else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = stringResource(R.string.cd_sound_toggle),
            tint = if (soundEnabled) palette.primary else palette.textMuted,
          )
        }
        IconButton(
          onClick = onOpenSettings,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("open_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.cd_settings),
            tint = palette.primary,
          )
        }
      }
    }
  }
}

@Composable
fun CrtScanlineOverlay(modifier: Modifier = Modifier) {
  val palette = LocalEdexPalette.current
  Canvas(modifier = modifier.fillMaxSize()) {
    val step = 4.dp.toPx()
    var y = 0f
    while (y < size.height) {
      drawLine(
        color = Color.Black.copy(alpha = 0.16f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
      )
      y += step
    }
    // Subtle corner glow vignette
    drawRect(
      brush =
        Brush.radialGradient(
          colors = listOf(Color.Transparent, palette.background.copy(alpha = 0.25f)),
          center = Offset(size.width / 2f, size.height / 2f),
          radius = maxOf(size.width, size.height) * 0.72f,
        )
    )
  }
}
