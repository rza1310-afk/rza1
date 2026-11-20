package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ProcessEntry
import com.example.model.SystemTelemetry
import com.example.ui.theme.EdexPalettes
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.LocalEdexPalette
import com.example.ui.theme.TelemetryThemeId
import java.util.Locale

@Composable
fun TelemetryThemeSwitcherBar(
  selectedTheme: TelemetryThemeId,
  globalThemeId: EdexThemeId,
  onSelectTheme: (TelemetryThemeId) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  EdexHudPanel(
    title = stringResource(R.string.telemetry_theme_panel_title),
    subtitle = selectedTheme.shortLabel,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TelemetryThemeId.entries.forEach { themeId ->
        val preview = EdexPalettes.forTelemetryTheme(themeId, globalThemeId)
        val selected = selectedTheme == themeId
        Surface(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("telemetry_theme_chip_${themeId.name.lowercase()}")
              .clickable { onSelectTheme(themeId) },
          color =
            if (selected) preview.primaryDim.copy(alpha = 0.72f)
            else palette.background.copy(alpha = 0.7f),
          border =
            BorderStroke(
              width = if (selected) 1.5.dp else 1.dp,
              color = if (selected) preview.primaryBright else palette.gridLine,
            ),
          shape = CutCornerShape(5.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier =
                Modifier.size(9.dp)
                  .background(preview.primaryBright, CircleShape)
                  .border(1.dp, preview.secondary, CircleShape)
            )
            Text(
              text = themeId.shortLabel,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) preview.primaryBright else palette.textPrimary,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
fun TelemetryDaemonPanel(
  isRunning: Boolean,
  sampleCount: Long,
  telemetry: SystemTelemetry,
  loadPercent: Float,
  glitchThreshold: Float,
  glitchActive: Boolean,
  onToggleService: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  EdexHudPanel(
    title = "DAEMON // TELEMETRY FGS",
    subtitle = if (glitchActive) "OVERLOAD ${loadPercent.toInt()}%" else if (isRunning) "ACTIVE" else "PAUSED",
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text =
            when {
              glitchActive -> "LOAD OVERLOAD >= ${glitchThreshold.toInt()}% (SHADER)"
              isRunning -> "FOREGROUND SERVICE ONLINE"
              else -> "FOREGROUND SERVICE IDLE"
            },
          style = MaterialTheme.typography.labelMedium,
          color =
            when {
              glitchActive -> palette.danger
              isRunning -> palette.success
              else -> palette.warning
            },
        )
        Text(
          text =
            String.format(
              Locale.US,
              "SAMPLES #%d • BAT %d%% • CPU %d%%",
              sampleCount,
              telemetry.batteryPercent,
              telemetry.cpuLoadPercent.toInt(),
            ),
          style = MaterialTheme.typography.labelSmall,
          color = palette.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      OutlinedButton(
        onClick = onToggleService,
        shape = CutCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isRunning) palette.danger else palette.primaryBright),
        colors =
          ButtonDefaults.outlinedButtonColors(
            contentColor = if (isRunning) palette.danger else palette.primaryBright
          ),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("telemetry_service_toggle"),
      ) {
        Text(
          text =
            if (isRunning) stringResource(R.string.daemon_stop)
            else stringResource(R.string.daemon_start),
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

@Composable
fun SystemClockPanel(telemetry: SystemTelemetry, modifier: Modifier = Modifier) {
  val palette = LocalEdexPalette.current
  EdexHudPanel(
    title = "SYSTEM // CLOCK",
    subtitle = telemetry.timezone,
    modifier = modifier,
  ) {
    Text(
      text = telemetry.timeFormatted,
      style = MaterialTheme.typography.displayLarge,
      color = palette.primaryBright,
      maxLines = 1,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = telemetry.dateFormatted,
        style = MaterialTheme.typography.labelMedium,
        color = palette.textPrimary,
      )
      Text(
        text = "UP ${telemetry.uptimeFormatted}",
        style = MaterialTheme.typography.labelSmall,
        color = palette.primary,
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
    TelemetryKvRow("HOST", telemetry.deviceModel)
    TelemetryKvRow("OS", telemetry.osVersion)
    TelemetryKvRow("KERNEL", "${telemetry.kernelVersion} • ${telemetry.cpuAbi}")
  }
}

@Composable
fun CpuTelemetryPanel(telemetry: SystemTelemetry, modifier: Modifier = Modifier) {
  val palette = LocalEdexPalette.current
  EdexHudPanel(
    title = "CPU // ${telemetry.cpuHardware}",
    subtitle = "${telemetry.cpuCores} CORES",
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Bottom,
    ) {
      Text(
        text = "${telemetry.cpuLoadPercent.toInt()}%",
        style = MaterialTheme.typography.headlineMedium,
        color = palette.primaryBright,
      )
      val avgMhz =
        telemetry.cpuFreqMhz.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
      Text(
        text = if (avgMhz != null) "AVG $avgMhz MHz" else "ACTIVE",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textMuted,
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    TelemetrySparklineChart(
      values = telemetry.cpuHistory,
      maxValue = 100f,
      lineColor = palette.primaryBright,
      modifier = Modifier.fillMaxWidth().height(54.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    // Per-core bars (2 columns)
    val cores = telemetry.coreLoads.take(8)
    cores.chunked(2).forEachIndexed { rowIdx, pair ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pair.forEachIndexed { colIdx, load ->
          val coreNum = rowIdx * 2 + colIdx
          val freqMhz = telemetry.cpuFreqMhz.getOrElse(coreNum) { 0 }
          Column(modifier = Modifier.weight(1f)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "C$coreNum",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
              )
              Text(
                text = if (freqMhz > 0) "${freqMhz}M" else "${load.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textPrimary,
              )
            }
            LinearProgressIndicator(
              progress = { (load / 100f).coerceIn(0.03f, 1f) },
              modifier = Modifier.fillMaxWidth().height(4.dp),
              color = if (load > 85f) palette.warning else palette.primary,
              trackColor = palette.primaryDim.copy(alpha = 0.35f),
            )
          }
        }
        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

@Composable
fun MemoryAndStoragePanel(telemetry: SystemTelemetry, modifier: Modifier = Modifier) {
  val palette = LocalEdexPalette.current
  val ramFraction = (telemetry.ramUsedMb.toFloat() / telemetry.ramTotalMb.coerceAtLeast(1L)).coerceIn(0f, 1f)
  val storageFraction =
    (telemetry.storageUsedGb / telemetry.storageTotalGb.coerceAtLeast(0.1f)).coerceIn(0f, 1f)

  EdexHudPanel(
    title = "MEMORY & STORAGE",
    subtitle = "${telemetry.ramUsedMb}/${telemetry.ramTotalMb} MB",
    modifier = modifier,
  ) {
    // Segmented RAM Bar
    Text(
      text = "SYSTEM RAM (${(ramFraction * 100).toInt()}%)",
      style = MaterialTheme.typography.labelMedium,
      color = palette.primary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    SegmentedHudBar(
      fraction = ramFraction,
      segments = 24,
      activeColor = if (ramFraction > 0.85f) palette.warning else palette.primaryBright,
      inactiveColor = palette.primaryDim.copy(alpha = 0.3f),
      modifier = Modifier.fillMaxWidth().height(12.dp),
    )
    Spacer(modifier = Modifier.height(6.dp))
    TelemetrySparklineChart(
      values = telemetry.ramHistory,
      maxValue = 100f,
      lineColor = palette.secondary,
      modifier = Modifier.fillMaxWidth().height(38.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    TelemetryKvRow(
      "SWAP",
      if (telemetry.swapTotalMb > 0) "${telemetry.swapUsedMb} / ${telemetry.swapTotalMb} MB"
      else "DISABLED",
    )
    TelemetryKvRow("JVM HEAP", "${telemetry.jvmHeapUsedMb} / ${telemetry.jvmHeapMaxMb} MB")
    Spacer(modifier = Modifier.height(6.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "DATA VOLUME (/data)",
        style = MaterialTheme.typography.labelMedium,
        color = palette.primary,
      )
      Text(
        text =
          String.format(
            Locale.US,
            "%.1f / %.1f GB",
            telemetry.storageUsedGb,
            telemetry.storageTotalGb,
          ),
        style = MaterialTheme.typography.labelSmall,
        color = palette.textPrimary,
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
      progress = { storageFraction },
      modifier = Modifier.fillMaxWidth().height(5.dp),
      color = palette.primary,
      trackColor = palette.primaryDim.copy(alpha = 0.35f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "POWER // ${telemetry.batteryStatusLabel}",
        style = MaterialTheme.typography.labelMedium,
        color = if (telemetry.batteryCharging) palette.success else palette.primary,
      )
      Text(
        text =
          "${telemetry.batteryPercent}% • ${String.format(Locale.US, "%.1f°C", telemetry.batteryTempC)} • ${telemetry.batteryVoltageMv}mV",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textPrimary,
      )
    }
  }
}

@Composable
fun ProcessesPanel(processes: List<ProcessEntry>, modifier: Modifier = Modifier) {
  val palette = LocalEdexPalette.current
  EdexHudPanel(
    title = "ACTIVE PROCESSES",
    subtitle = "${processes.size} TRACKED",
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("PID/TID", style = MaterialTheme.typography.labelSmall, color = palette.primary, modifier = Modifier.width(58.dp))
      Text("NAME", style = MaterialTheme.typography.labelSmall, color = palette.primary, modifier = Modifier.weight(1f))
      Text("STATE", style = MaterialTheme.typography.labelSmall, color = palette.primary, modifier = Modifier.width(44.dp))
    }
    processes.take(7).forEach { proc ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(palette.surfaceElevated.copy(alpha = 0.45f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = proc.id.toString(),
          style = MaterialTheme.typography.labelSmall,
          color = palette.textMuted,
          modifier = Modifier.width(58.dp),
          maxLines = 1,
        )
        Text(
          text = proc.name,
          style = MaterialTheme.typography.bodySmall,
          color = palette.textPrimary,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = proc.state,
          style = MaterialTheme.typography.labelSmall,
          color = if (proc.state == "RUNN" || proc.state == "FG") palette.success else palette.primary,
          modifier = Modifier.width(44.dp),
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
fun TelemetrySparklineChart(
  values: List<Float>,
  maxValue: Float,
  lineColor: Color,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  Canvas(
    modifier =
      modifier
        .background(palette.background.copy(alpha = 0.65f), CutCornerShape(4.dp))
        .border(1.dp, palette.gridLine, CutCornerShape(4.dp))
  ) {
    if (values.size < 2) return@Canvas
    val safeMax = maxValue.coerceAtLeast(1f)
    val w = size.width
    val h = size.height
    val stepX = w / (values.size - 1).coerceAtLeast(1)

    // Grid horizontal lines
    for (i in 1..2) {
      val gy = (h / 3f) * i
      drawLine(palette.gridLine, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
    }

    val linePath = Path()
    val fillPath = Path()
    values.forEachIndexed { idx, value ->
      val x = idx * stepX
      val norm = (value / safeMax).coerceIn(0f, 1f)
      val y = h - (norm * (h - 6.dp.toPx())) - 3.dp.toPx()
      if (idx == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, h)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
      if (idx == values.lastIndex) {
        fillPath.lineTo(x, h)
        fillPath.close()
      }
    }

    drawPath(
      path = fillPath,
      brush =
        Brush.verticalGradient(
          colors = listOf(lineColor.copy(alpha = 0.32f), Color.Transparent),
          startY = 0f,
          endY = h,
        ),
    )
    drawPath(
      path = linePath,
      color = lineColor,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

@Composable
fun SegmentedHudBar(
  fraction: Float,
  segments: Int,
  activeColor: Color,
  inactiveColor: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    if (segments <= 0 || size.width <= 0f) return@Canvas
    val gap = 2.dp.toPx()
    val segWidth = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(1f)
    val activeCount = (fraction.coerceIn(0f, 1f) * segments).toInt()
    for (i in 0 until segments) {
      val x = i * (segWidth + gap)
      drawRect(
        color = if (i < activeCount) activeColor else inactiveColor,
        topLeft = Offset(x, 0f),
        size = androidx.compose.ui.geometry.Size(segWidth, size.height),
      )
    }
  }
}

@Composable
fun TelemetryKvRow(label: String, value: String) {
  val palette = LocalEdexPalette.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = palette.textMuted,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.labelSmall,
      color = palette.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
