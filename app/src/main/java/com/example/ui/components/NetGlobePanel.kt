package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GlobeNodeFilter
import com.example.model.NetworkConnectionNode
import com.example.model.SystemTelemetry
import com.example.ui.theme.LocalEdexPalette
import java.util.Locale

@Composable
fun NetGlobePanel(
  telemetry: SystemTelemetry,
  modifier: Modifier = Modifier,
  isServiceRunning: Boolean = true,
  selectedNodeId: String? = null,
  nodeFilter: GlobeNodeFilter = GlobeNodeFilter.ALL,
  onSelectNode: (String?) -> Unit = {},
  onSelectFilter: (GlobeNodeFilter) -> Unit = {},
  onProbeNode: (NetworkConnectionNode) -> Unit = {},
  onRunNetDiagnostic: () -> Unit = {},
  onGlobeTap: () -> Unit = {},
  onGlobeReset: () -> Unit = {},
) {
  val palette = LocalEdexPalette.current
  val clipboard = LocalClipboardManager.current
  val livePacketBlink =
    isServiceRunning && telemetry.networkConnected && telemetry.packetTrafficActive
  var copiedFeedback by remember(selectedNodeId) { mutableStateOf(false) }

  val filteredConnections =
    remember(telemetry.activeConnections, nodeFilter) {
      filterGlobeConnections(telemetry.activeConnections, nodeFilter)
    }

  val selectedNode =
    remember(telemetry.activeConnections, selectedNodeId) {
      telemetry.activeConnections.firstOrNull { it.id == selectedNodeId }
    }

  val statusLabel =
    when {
      !isServiceRunning -> "SVC PAUSED"
      !telemetry.networkConnected -> "OFFLINE"
      telemetry.packetTrafficActive -> "PKT ${telemetry.packetsPerSec}/s"
      else -> "IDLE • ${filteredConnections.size} NODES"
    }
  val statusColor =
    when {
      !isServiceRunning -> palette.warning
      !telemetry.networkConnected -> palette.warning
      telemetry.packetTrafficActive -> palette.success
      else -> palette.primary
    }

  EdexHudPanel(
    title = "NET // WORLD GLOBE",
    subtitle = if (telemetry.networkConnected) telemetry.networkTransport else "OFFLINE",
    modifier = modifier,
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(185.dp)
          .background(palette.background.copy(alpha = 0.72f), CutCornerShape(6.dp))
          .border(1.dp, palette.gridLine, CutCornerShape(6.dp)),
      contentAlignment = Alignment.Center,
    ) {
      WireframeGlobeCanvas(
        networkConnected = telemetry.networkConnected,
        connections = filteredConnections,
        packetTrafficActive = livePacketBlink,
        packetIntensity = telemetry.packetIntensity,
        rxSpeedKbps = telemetry.rxSpeedKbps,
        txSpeedKbps = telemetry.txSpeedKbps,
        selectedNodeId = selectedNode?.id,
        onSelectNode = onSelectNode,
        modifier = Modifier.fillMaxSize(),
        onGlobeTap = onGlobeTap,
        onGlobeReset = onGlobeReset,
      )

      Text(
        text = "${telemetry.interfaceName} // ${telemetry.ipAddress}",
        style = MaterialTheme.typography.labelSmall,
        color = palette.primary,
        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
      )

      Row(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(6.dp)
            .background(palette.surface.copy(alpha = 0.85f), CutCornerShape(4.dp))
            .border(1.dp, statusColor.copy(alpha = 0.7f), CutCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("globe_packet_status"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PacketBlinkDot(color = statusColor, blinking = livePacketBlink)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = statusLabel,
          style = MaterialTheme.typography.labelSmall,
          color = statusColor,
          maxLines = 1,
        )
      }

      Text(
        text = "TAP NODE • DRAG",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textMuted.copy(alpha = 0.75f),
        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Node Filter Bar
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GlobeNodeFilter.entries.forEach { mode ->
        val selected = nodeFilter == mode
        Surface(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("globe_filter_${mode.name.lowercase()}")
              .clickable { onSelectFilter(mode) },
          color =
            if (selected) palette.primaryDim.copy(alpha = 0.7f)
            else palette.background.copy(alpha = 0.65f),
          border = BorderStroke(1.dp, if (selected) palette.primaryBright else palette.gridLine),
          shape = CutCornerShape(4.dp),
        ) {
          Text(
            text = mode.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.primaryBright else palette.textMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Live RX / TX Throughput Dual Chart
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = String.format(Locale.US, "RX %.1f KB/s", telemetry.rxSpeedKbps),
        style = MaterialTheme.typography.labelSmall,
        color = palette.primaryBright,
      )
      Text(
        text = String.format(Locale.US, "TX %.1f KB/s", telemetry.txSpeedKbps),
        style = MaterialTheme.typography.labelSmall,
        color = palette.warning,
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    DualTrafficChart(
      rxHistory = telemetry.rxHistory,
      txHistory = telemetry.txHistory,
      rxColor = palette.primaryBright,
      txColor = palette.warning,
      modifier = Modifier.fillMaxWidth().height(54.dp),
    )

    Spacer(modifier = Modifier.height(8.dp))
    TelemetryKvRow("GATEWAY", telemetry.gatewayIp)
    TelemetryKvRow(
      "DNS",
      telemetry.dnsServers.take(2).joinToString(", ").ifBlank { "--" },
    )
    TelemetryKvRow(
      "TRAFFIC",
      String.format(
        Locale.US,
        "↓ %.1f MB  ↑ %.1f MB",
        telemetry.totalRxMb,
        telemetry.totalTxMb,
      ),
    )

    if (filteredConnections.isNotEmpty()) {
      Spacer(modifier = Modifier.height(6.dp))
      ActiveConnectionsStrip(
        connections = filteredConnections,
        selectedNodeId = selectedNode?.id,
        blinking = livePacketBlink,
        onSelectNode = { node ->
          onGlobeTap()
          onSelectNode(if (selectedNodeId == node.id) null else node.id)
        },
      )
      AnimatedVisibility(visible = selectedNode != null) {
        selectedNode?.let { node ->
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .padding(top = 6.dp)
                .background(palette.surfaceElevated.copy(alpha = 0.85f), CutCornerShape(6.dp))
                .border(1.dp, palette.primaryBright.copy(alpha = 0.7f), CutCornerShape(6.dp))
                .padding(8.dp)
                .testTag("globe_selected_node_detail"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${node.protocol} // ${node.endpoint}",
                  style = MaterialTheme.typography.labelMedium,
                  color = palette.primaryBright,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    String.format(
                      Locale.US,
                      "STATE %s • LAT %.1f° LON %.1f°",
                      node.state,
                      node.latDeg,
                      node.lonDeg,
                    ),
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textMuted,
                  maxLines = 1,
                )
              }
              IconButton(
                onClick = { onSelectNode(null) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("globe_clear_node_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = stringResource(R.string.cd_close_dialog),
                  tint = palette.textMuted,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              InspectorActionButton(
                label = stringResource(R.string.globe_probe_ping),
                icon = Icons.Filled.NetworkPing,
                accent = palette.success,
                onClick = { onProbeNode(node) },
                testTag = "globe_probe_ping_button",
              )
              InspectorActionButton(
                label = stringResource(R.string.globe_net_diag),
                icon = Icons.Filled.Terminal,
                accent = palette.primary,
                onClick = onRunNetDiagnostic,
                testTag = "globe_ifconfig_button",
              )
              InspectorActionButton(
                label = if (copiedFeedback) "COPIED" else stringResource(R.string.globe_copy_ip),
                icon = Icons.Filled.ContentCopy,
                accent = palette.secondary,
                onClick = {
                  clipboard.setText(AnnotatedString(node.endpoint))
                  copiedFeedback = true
                  onGlobeTap()
                },
                testTag = "globe_copy_ip_button",
              )
            }
          }
        }
      }
    }
  }
}

internal fun filterGlobeConnections(
  connections: List<NetworkConnectionNode>,
  filter: GlobeNodeFilter,
): List<NetworkConnectionNode> =
  when (filter) {
    GlobeNodeFilter.ALL -> connections
    GlobeNodeFilter.SOCKETS ->
      connections.filter { it.isOrigin || it.protocol == "TCP" || it.protocol == "UDP" }
    GlobeNodeFilter.GW_DNS ->
      connections.filter { it.isOrigin || it.protocol == "GW" || it.protocol == "DNS" }
  }

@Composable
private fun InspectorActionButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  val shape = CutCornerShape(4.dp)
  Surface(
    modifier =
      Modifier.minimumInteractiveComponentSize()
        .clip(shape)
        .testTag(testTag)
        .clickable(onClick = onClick),
    color = accent.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, accent),
    shape = shape,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = accent,
        modifier = Modifier.size(14.dp),
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
    }
  }
}

/** Isolated blinking LED dot using graphicsLayer alpha so parent HUD panels never recompose at 60fps. */
@Composable
private fun PacketBlinkDot(color: Color, blinking: Boolean) {
  if (!blinking) {
    Box(modifier = Modifier.size(6.dp).background(color.copy(alpha = 0.8f), CircleShape))
    return
  }
  val transition = rememberInfiniteTransition(label = "pkt_dot_anim")
  val pulseAlpha by
    transition.animateFloat(
      initialValue = 0.2f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(240, easing = LinearEasing), RepeatMode.Reverse),
      label = "pkt_dot_alpha",
    )
  Box(
    modifier =
      Modifier.size(6.dp)
        .graphicsLayer { alpha = pulseAlpha }
        .background(color, CircleShape)
  )
}

@Composable
private fun ActiveConnectionsStrip(
  connections: List<NetworkConnectionNode>,
  selectedNodeId: String?,
  blinking: Boolean,
  onSelectNode: (NetworkConnectionNode) -> Unit,
) {
  val palette = LocalEdexPalette.current
  Column(modifier = Modifier.fillMaxWidth().testTag("globe_active_connections")) {
    Text(
      text = "ACTIVE NODES (${connections.size})",
      style = MaterialTheme.typography.labelSmall,
      color = palette.textMuted,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      connections.take(8).forEach { node ->
        val isSelected = selectedNodeId == node.id
        val nodeColor =
          when {
            isSelected -> palette.success
            node.isOrigin -> palette.primaryBright
            node.protocol == "TCP" || node.protocol == "UDP" -> palette.success
            node.protocol == "DNS" -> palette.secondary
            else -> palette.primary
          }
        val shape = CutCornerShape(4.dp)
        Row(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .clip(shape)
              .background(
                if (isSelected) palette.primaryDim.copy(alpha = 0.65f)
                else palette.background.copy(alpha = 0.72f),
                shape,
              )
              .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) nodeColor else nodeColor.copy(alpha = 0.45f),
                shape = shape,
              )
              .clickable { onSelectNode(node) }
              .padding(horizontal = 8.dp, vertical = 6.dp)
              .testTag("globe_node_chip_${node.id}"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          PacketBlinkDot(color = nodeColor, blinking = blinking)
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = "${node.label} ${node.endpoint}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) palette.primaryBright else palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun DualTrafficChart(
  rxHistory: List<Float>,
  txHistory: List<Float>,
  rxColor: Color,
  txColor: Color,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  Canvas(
    modifier =
      modifier
        .background(palette.background.copy(alpha = 0.65f), CutCornerShape(4.dp))
        .border(1.dp, palette.gridLine, CutCornerShape(4.dp))
  ) {
    val peak = maxOf(rxHistory.maxOrNull() ?: 0f, txHistory.maxOrNull() ?: 0f, 16f)
    fun drawSeries(series: List<Float>, color: Color) {
      if (series.size < 2) return
      val stepX = size.width / (series.size - 1).coerceAtLeast(1)
      val path = Path()
      series.forEachIndexed { idx, v ->
        val x = idx * stepX
        val y = size.height - ((v / peak).coerceIn(0f, 1f) * (size.height - 6.dp.toPx())) - 3.dp.toPx()
        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(
        path = path,
        color = color,
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
    }
    drawSeries(rxHistory, rxColor)
    drawSeries(txHistory, txColor)
  }
}
