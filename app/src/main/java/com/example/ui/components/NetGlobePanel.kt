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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

enum class GlobeRenderMode(val label: String) {
  SCENE_3D("3D GLOBE"),
  WIRE_2D("WIRE"),
}

fun filterGlobeConnections(
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
fun NetGlobePanel(
  telemetry: SystemTelemetry,
  modifier: Modifier = Modifier,
  isServiceRunning: Boolean = true,
  selectedNodeId: String? = null,
  nodeFilter: GlobeNodeFilter = GlobeNodeFilter.ALL,
  onSelectNode: ((String?) -> Unit)? = null,
  onSelectFilter: ((GlobeNodeFilter) -> Unit)? = null,
  onProbeNode: ((NetworkConnectionNode) -> Unit)? = null,
  onRunDiagnostic: (() -> Unit)? = null,
  onGlobeTap: () -> Unit = {},
  onGlobeReset: () -> Unit = {},
) {
  val palette = LocalEdexPalette.current
  val clipboardManager = LocalClipboardManager.current
  val livePacketBlink =
    isServiceRunning && telemetry.networkConnected && telemetry.packetTrafficActive

  var localSelectedNodeId by remember { mutableStateOf<String?>(null) }
  var localFilter by remember { mutableStateOf(GlobeNodeFilter.ALL) }

  val effectiveSelectedId = if (onSelectNode != null) selectedNodeId else localSelectedNodeId
  val effectiveFilter = if (onSelectFilter != null) nodeFilter else localFilter

  val updateSelectedId: (String?) -> Unit = { id ->
    if (onSelectNode != null) onSelectNode(id) else localSelectedNodeId = id
  }
  val updateFilter: (GlobeNodeFilter) -> Unit = { filter ->
    if (onSelectFilter != null) onSelectFilter(filter) else localFilter = filter
  }

  val filteredConnections =
    remember(telemetry.activeConnections, effectiveFilter) {
      filterGlobeConnections(telemetry.activeConnections, effectiveFilter)
    }

  val selectedNode =
    remember(filteredConnections, effectiveSelectedId) {
      filteredConnections.firstOrNull { it.id == effectiveSelectedId }
    }

  LaunchedEffect(effectiveSelectedId, selectedNode) {
    if (effectiveSelectedId != null && selectedNode == null) {
      updateSelectedId(null)
    }
  }

  val sceneAvailable = SceneViewRuntimeSupport.isAvailable
  var renderMode by
    remember(sceneAvailable) {
      mutableStateOf(if (sceneAvailable) GlobeRenderMode.SCENE_3D else GlobeRenderMode.WIRE_2D)
    }

  val statusLabel =
    when {
      !isServiceRunning -> "SVC PAUSED"
      !telemetry.networkConnected -> "OFFLINE"
      telemetry.packetTrafficActive -> "PKT ${telemetry.packetsPerSec}/s"
      else -> "IDLE • ${telemetry.activeConnections.size} NODES"
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
          .height(195.dp)
          .background(palette.background.copy(alpha = 0.72f), CutCornerShape(6.dp))
          .border(1.dp, palette.gridLine, CutCornerShape(6.dp)),
      contentAlignment = Alignment.Center,
    ) {
      if (renderMode == GlobeRenderMode.SCENE_3D && sceneAvailable) {
        NetworkGlobe(
          networkConnected = telemetry.networkConnected,
          connections = filteredConnections,
          packetTrafficActive = livePacketBlink,
          packetIntensity = telemetry.packetIntensity,
          rxSpeedKbps = telemetry.rxSpeedKbps,
          txSpeedKbps = telemetry.txSpeedKbps,
          selectedNodeId = selectedNode?.id,
          modifier = Modifier.fillMaxSize(),
          onSelectNode = { nodeId ->
            onGlobeTap()
            updateSelectedId(if (selectedNode?.id == nodeId) null else nodeId)
          },
          onGlobeTap = onGlobeTap,
          onGlobeReset = {
            updateSelectedId(null)
            onGlobeReset()
          },
        )
      } else {
        WireframeGlobeCanvas(
          networkConnected = telemetry.networkConnected,
          connections = filteredConnections,
          packetTrafficActive = livePacketBlink,
          packetIntensity = telemetry.packetIntensity,
          rxSpeedKbps = telemetry.rxSpeedKbps,
          txSpeedKbps = telemetry.txSpeedKbps,
          selectedNodeId = selectedNode?.id,
          modifier = Modifier.fillMaxSize(),
          onGlobeTap = onGlobeTap,
          onGlobeReset = {
            updateSelectedId(null)
            onGlobeReset()
          },
        )
      }

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

      if (sceneAvailable) {
        Surface(
          onClick = {
            onGlobeTap()
            renderMode =
              if (renderMode == GlobeRenderMode.SCENE_3D) GlobeRenderMode.WIRE_2D
              else GlobeRenderMode.SCENE_3D
          },
          modifier =
            Modifier.align(Alignment.BottomStart)
              .padding(6.dp)
              .testTag("globe_mode_toggle"),
          color = palette.surface.copy(alpha = 0.88f),
          border = BorderStroke(1.dp, palette.primaryBright.copy(alpha = 0.65f)),
          shape = CutCornerShape(4.dp),
        ) {
          Text(
            text = renderMode.label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.primaryBright,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }

      Text(
        text = if (renderMode == GlobeRenderMode.SCENE_3D && sceneAvailable) "3D ORBIT • TAP PAUSE" else "DRAG • TAP PAUSE",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textMuted.copy(alpha = 0.75f),
        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

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

    if (telemetry.activeConnections.isNotEmpty()) {
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("globe_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GlobeNodeFilter.entries.forEach { filter ->
          val selected = effectiveFilter == filter
          val shape = CutCornerShape(4.dp)
          Surface(
            onClick = { updateFilter(filter) },
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("globe_filter_${filter.name.lowercase()}"),
            color =
              if (selected) palette.primaryDim.copy(alpha = 0.65f)
              else palette.background.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, if (selected) palette.primaryBright else palette.gridLine),
            shape = shape,
          ) {
            Text(
              text = filter.label,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) palette.primaryBright else palette.textMuted,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
        if (onRunDiagnostic != null) {
          val shape = CutCornerShape(4.dp)
          Surface(
            onClick = { onRunDiagnostic() },
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("globe_net_diag_button"),
            color = palette.background.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.6f)),
            shape = shape,
          ) {
            Text(
              text = stringResource(R.string.globe_net_diag),
              style = MaterialTheme.typography.labelSmall,
              color = palette.primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      ActiveConnectionsStrip(
        connections = filteredConnections,
        selectedNodeId = selectedNode?.id,
        blinking = livePacketBlink,
        onSelectNode = { node ->
          onGlobeTap()
          updateSelectedId(if (selectedNode?.id == node.id) null else node.id)
        },
      )
      AnimatedVisibility(visible = selectedNode != null) {
        selectedNode?.let { node ->
          Column(
            modifier =
              Modifier.padding(top = 6.dp)
                .fillMaxWidth()
                .background(palette.surfaceElevated.copy(alpha = 0.7f), CutCornerShape(4.dp))
                .border(1.dp, palette.primaryBright.copy(alpha = 0.5f), CutCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("globe_selected_node_detail"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${node.protocol} ${node.endpoint}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.primaryBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text =
                  String.format(
                    Locale.US,
                    "%s • %.1f°, %.1f°",
                    node.state,
                    node.latDeg,
                    node.lonDeg,
                  ),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
                maxLines = 1,
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (onProbeNode != null) {
                Surface(
                  onClick = { onProbeNode(node) },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("globe_probe_ping_button"),
                  color = palette.success.copy(alpha = 0.16f),
                  border = BorderStroke(1.dp, palette.success),
                  shape = CutCornerShape(4.dp),
                ) {
                  Text(
                    text = stringResource(R.string.globe_probe_ping),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.success,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
              Surface(
                onClick = {
                  onGlobeTap()
                  clipboardManager.setText(AnnotatedString(node.endpoint))
                },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("globe_copy_ip_button"),
                color = palette.background.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, palette.border),
                shape = CutCornerShape(4.dp),
              ) {
                Text(
                  text = stringResource(R.string.globe_copy_ip),
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.primaryBright,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }
        }
      }
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
