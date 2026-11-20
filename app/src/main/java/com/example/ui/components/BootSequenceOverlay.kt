package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalEdexPalette
import kotlinx.coroutines.delay

@Composable
fun BootSequenceOverlay(
  deviceModel: String,
  osVersion: String,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  val bootMessages =
    remember(deviceModel, osVersion) {
      listOf(
        "[OK] Initializing eDEX-UI Tactical Interface v2.2",
        "[OK] Hardware Target: $deviceModel",
        "[OK] Kernel Layer: $osVersion",
        "[OK] Mounting System Telemetry & Proc Subsystems",
        "[OK] Calibrating 3D Network Telemetry Globe",
        "[OK] Spawning /system/bin/sh Terminal Multiplexer",
        "[READY] Engaging HUD Interface...",
      )
    }

  var visibleCount by remember { mutableIntStateOf(1) }
  LaunchedEffect(Unit) {
    for (i in 2..bootMessages.size) {
      delay(180L)
      visibleCount = i
    }
  }

  val progress by
    animateFloatAsState(
      targetValue = visibleCount.toFloat() / bootMessages.size.toFloat(),
      animationSpec = tween(durationMillis = 220),
      label = "boot_progress",
    )

  BackHandler(onBack = onSkip)

  val scrimInteraction = remember { MutableInteractionSource() }
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(palette.background)
        .clickable(interactionSource = scrimInteraction, indication = null, onClick = {})
        .safeDrawingPadding()
        .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 560.dp)
          .fillMaxWidth()
          .background(palette.surface, CutCornerShape(12.dp))
          .border(1.dp, palette.primaryBright, CutCornerShape(12.dp))
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "eDEX-UI // SYSTEM BOOT",
        style = MaterialTheme.typography.headlineMedium,
        color = palette.primaryBright,
      )
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = palette.primaryBright,
        trackColor = palette.primaryDim.copy(alpha = 0.4f),
      )
      Spacer(modifier = Modifier.height(4.dp))
      bootMessages.take(visibleCount).forEach { msg ->
        Text(
          text = msg,
          style = MaterialTheme.typography.bodySmall,
          color = if (msg.startsWith("[READY]")) palette.success else palette.textPrimary,
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        OutlinedButton(
          onClick = onSkip,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("skip_boot_button"),
          shape = CutCornerShape(6.dp),
          border = BorderStroke(1.dp, palette.primary),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primary),
        ) {
          Text(
            text = stringResource(R.string.boot_skip),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }
  }
}
