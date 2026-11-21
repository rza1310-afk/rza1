package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkConnectionNode
import com.example.ui.theme.LocalEdexPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

data class Vec3(val x: Float, val y: Float, val z: Float)

@Composable
fun WireframeGlobeCanvas(
  networkConnected: Boolean,
  modifier: Modifier = Modifier,
  connections: List<NetworkConnectionNode> = emptyList(),
  packetTrafficActive: Boolean = false,
  packetIntensity: Float = 0f,
  rxSpeedKbps: Float = 0f,
  txSpeedKbps: Float = 0f,
  selectedNodeId: String? = null,
  onGlobeTap: () -> Unit = {},
  onGlobeReset: () -> Unit = {},
) {
  val palette = LocalEdexPalette.current
  val textMeasurer = rememberTextMeasurer()

  val activeNodes =
    remember(connections, networkConnected) {
      if (connections.isNotEmpty()) {
        connections
      } else if (networkConnected) {
        DEFAULT_FALLBACK_NODES
      } else {
        listOf(DEFAULT_FALLBACK_NODES.first())
      }
    }

  val nodeVectors =
    remember(activeNodes) {
      activeNodes.map { node ->
        node to latLonToVec3(node.latDeg * PI / 180.0, node.lonDeg * PI / 180.0)
      }
    }

  val defaultYaw =
    remember(activeNodes.firstOrNull()?.lonDeg) {
      val origin = activeNodes.firstOrNull { it.isOrigin } ?: activeNodes.firstOrNull()
      if (origin != null) (PI / 2.0 - origin.lonDeg * PI / 180.0).toFloat() else 0.45f
    }

  var yawRad by remember { mutableFloatStateOf(defaultYaw) }
  var pitchRad by remember { mutableFloatStateOf(0.28f) }
  val rollRad = 0.14f // Slight axial tilt like eDEX-UI's Earth globe
  var autoRotate by remember { mutableStateOf(true) }

  val targetTrafficIntensity =
    if (networkConnected && packetTrafficActive) packetIntensity.coerceIn(0.35f, 1f) else 0f
  val trafficBlend by
    animateFloatAsState(
      targetValue = targetTrafficIntensity,
      animationSpec = tween(durationMillis = 280),
      label = "globe_traffic_blend",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "globe_hud_anim")
  val reticleAngle by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
      label = "reticle_rotation",
    )
  val packetProgress by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
      label = "packet_travel",
    )
  val blinkPhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Restart),
      label = "packet_node_blink",
    )
  val ambientPulsePhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
      label = "beacon_pulse",
    )

  // Smoothly focus camera onto a selected connection node when clicked in the HUD strip
  LaunchedEffect(selectedNodeId) {
    val targetNode = activeNodes.firstOrNull { it.id == selectedNodeId } ?: return@LaunchedEffect
    autoRotate = false
    val targetYaw = (PI / 2.0 - targetNode.lonDeg * PI / 180.0).toFloat()
    val targetPitch = (-targetNode.latDeg * PI / 180.0 * 0.45).toFloat().coerceIn(-1.15f, 1.15f)
    repeat(18) {
      val yawDelta = atan2(sin(targetYaw - yawRad), cos(targetYaw - yawRad))
      val pitchDelta = targetPitch - pitchRad
      if (kotlin.math.abs(yawDelta) < 0.01f && kotlin.math.abs(pitchDelta) < 0.01f) return@LaunchedEffect
      yawRad += yawDelta * 0.24f
      pitchRad += pitchDelta * 0.24f
      delay(16L)
    }
  }

  LaunchedEffect(autoRotate) {
    while (autoRotate) {
      delay(28L)
      yawRad += 0.012f
    }
  }

  val labelTextStyle =
    remember(palette.textPrimary) {
      TextStyle(
        color = palette.textPrimary,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
      )
    }

  Canvas(
    modifier =
      modifier
        .testTag("network_globe_canvas")
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            autoRotate = false
            yawRad += dragAmount.x * 0.008f
            pitchRad = (pitchRad + dragAmount.y * 0.006f).coerceIn(-1.15f, 1.15f)
          }
        }
        .pointerInput(defaultYaw) {
          detectTapGestures(
            onTap = {
              autoRotate = !autoRotate
              onGlobeTap()
            },
            onDoubleTap = {
              yawRad = defaultYaw
              pitchRad = 0.28f
              autoRotate = true
              onGlobeReset()
            },
          )
        }
  ) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = minOf(size.width, size.height) * 0.40f
    if (radius <= 1f) return@Canvas

    // 1. Rotating outer HUD degree reticle using Canvas rotate transformation
    rotate(degrees = reticleAngle, pivot = Offset(cx, cy)) {
      val outerR = radius * 1.14f
      for (deg in 0 until 360 step 15) {
        val rad = deg * (PI / 180.0)
        val innerTick = if (deg % 45 == 0) outerR - 6.dp.toPx() else outerR - 3.dp.toPx()
        drawLine(
          color = palette.primary.copy(alpha = if (deg % 45 == 0) 0.55f else 0.24f),
          start = Offset(cx + (cos(rad) * innerTick).toFloat(), cy + (sin(rad) * innerTick).toFloat()),
          end = Offset(cx + (cos(rad) * outerR).toFloat(), cy + (sin(rad) * outerR).toFloat()),
          strokeWidth = if (deg % 45 == 0) 1.5.dp.toPx() else 0.9.dp.toPx(),
        )
      }
    }

    // Sphere horizon silhouette
    drawCircle(
      color = palette.border,
      radius = radius,
      center = Offset(cx, cy),
      style = Stroke(width = 1.4.dp.toPx()),
    )

    // 2. Precomputed Latitude rings transformed in 3D space
    for ((isEquator, ringVectors) in GLOBE_LAT_RING_VECTORS) {
      var prev: Vec3? = null
      for (vec in ringVectors) {
        val pt = rotatePoint3D(vec, yawRad, pitchRad, rollRad)
        if (prev != null && (pt.z > -0.18f || prev.z > -0.18f)) {
          val alpha = if (pt.z > 0f) 0.40f else 0.10f
          drawLine(
            color = palette.primary.copy(alpha = alpha),
            start = Offset(cx + prev.x * radius, cy - prev.y * radius),
            end = Offset(cx + pt.x * radius, cy - pt.y * radius),
            strokeWidth = if (isEquator) 1.5.dp.toPx() else 0.9.dp.toPx(),
          )
        }
        prev = pt
      }
    }

    // 3. Precomputed Longitude meridians transformed in 3D space
    for (meridianVectors in GLOBE_LON_MERIDIAN_VECTORS) {
      var prev: Vec3? = null
      for (vec in meridianVectors) {
        val pt = rotatePoint3D(vec, yawRad, pitchRad, rollRad)
        if (prev != null && (pt.z > -0.18f || prev.z > -0.18f)) {
          val alpha = if (pt.z > 0f) 0.34f else 0.08f
          drawLine(
            color = palette.primary.copy(alpha = alpha),
            start = Offset(cx + prev.x * radius, cy - prev.y * radius),
            end = Offset(cx + pt.x * radius, cy - pt.y * radius),
            strokeWidth = 0.85.dp.toPx(),
          )
        }
        prev = pt
      }
    }

    // 4. Precomputed Continental dot-matrix projection with depth attenuation
    for (vec in GLOBE_CONTINENT_VECTORS) {
      val pt = rotatePoint3D(vec, yawRad, pitchRad, rollRad)
      if (pt.z > 0.02f) {
        val alpha = (0.30f + 0.70f * pt.z).coerceIn(0f, 1f)
        drawCircle(
          color = palette.primary.copy(alpha = alpha),
          radius = (1.6f + 0.8f * pt.z).dp.toPx(),
          center = Offset(cx + pt.x * radius, cy - pt.y * radius),
        )
      }
    }

    // 5. Great-circle arcs & directional RX/TX packet pulses between origin and active remote nodes
    val originPair = nodeVectors.firstOrNull { it.first.isOrigin } ?: nodeVectors.firstOrNull()

    if (networkConnected && originPair != null && nodeVectors.size > 1) {
      val originVec = originPair.second
      var linkIdx = 0
      nodeVectors.forEach { (peerNode, peerVec) ->
        if (peerNode.id == originPair.first.id) return@forEach
        val isFocused = selectedNodeId != null && peerNode.id == selectedNodeId
        val steps = 16
        var prevArc: Vec3? = null
        val arcAlpha =
          (if (isFocused) 0.75f else 0.30f) + 0.42f * trafficBlend
        for (s in 0..steps) {
          val t = s.toFloat() / steps
          val arcBase = slerpNormalized(originVec, peerVec, t)
          val altitude = 1.0f + sin(t * PI.toFloat()) * 0.16f
          val elevated = Vec3(arcBase.x * altitude, arcBase.y * altitude, arcBase.z * altitude)
          val rotated = rotatePoint3D(elevated, yawRad, pitchRad, rollRad)
          if (prevArc != null && (rotated.z > 0f || prevArc.z > 0f)) {
            drawLine(
              color =
                (if (isFocused) palette.success else palette.primaryBright).copy(
                  alpha = arcAlpha.coerceIn(0f, 1f)
                ),
              start = Offset(cx + prevArc.x * radius, cy - prevArc.y * radius),
              end = Offset(cx + rotated.x * radius, cy - rotated.y * radius),
              strokeWidth = ((if (isFocused) 1.7f else 1.1f) + 0.6f * trafficBlend).dp.toPx(),
              cap = StrokeCap.Round,
            )
          }
          prevArc = rotated
        }

        // Animated RX (inbound peer -> origin) & TX (outbound origin -> peer) packet pulses
        if (trafficBlend > 0.04f) {
          val showRx = rxSpeedKbps > 0f || txSpeedKbps <= 0f
          val showTx = txSpeedKbps > 0f

          if (showRx) {
            val rxT = 1f - ((packetProgress + linkIdx * 0.23f) % 1f)
            drawArcPacketPulse(
              originVec = originVec,
              peerVec = peerVec,
              t = rxT,
              yawRad = yawRad,
              pitchRad = pitchRad,
              rollRad = rollRad,
              cx = cx,
              cy = cy,
              radius = radius,
              color = palette.success,
              alpha = trafficBlend,
            )
          }
          if (showTx) {
            val txT = (packetProgress + linkIdx * 0.37f + 0.5f) % 1f
            drawArcPacketPulse(
              originVec = originVec,
              peerVec = peerVec,
              t = txT,
              yawRad = yawRad,
              pitchRad = pitchRad,
              rollRad = rollRad,
              cx = cx,
              cy = cy,
              radius = radius,
              color = palette.warning,
              alpha = trafficBlend,
            )
          }
        }
        linkIdx++
      }
    }

    // 6. Glowing active network connection nodes (blink sharply when Telemetry Service detects packet traffic)
    val rotatedNodes =
      nodeVectors
        .mapIndexed { idx, (node, vec) ->
          Triple(idx, node, rotatePoint3D(vec, yawRad, pitchRad, rollRad))
        }
        .sortedBy { it.third.z }

    val ambientBreath = (sin(ambientPulsePhase) + 1f) * 0.5f
    val drawnLabelCenters = mutableListOf<Offset>()
    val minLabelSpacingPx = 38.dp.toPx()

    rotatedNodes.forEach { (idx, node, pt) ->
      if (pt.z <= -0.10f) return@forEach

      val pos = Offset(cx + pt.x * radius, cy - pt.y * radius)
      val depthAlpha = ((pt.z + 0.10f) / 1.10f).coerceIn(0.22f, 1f)
      val isSelected = selectedNodeId != null && node.id == selectedNodeId

      val baseNodeColor =
        when {
          !networkConnected -> palette.warning
          isSelected -> palette.success
          node.isOrigin -> palette.primaryBright
          node.protocol == "TCP" || node.protocol == "UDP" -> palette.success
          node.protocol == "DNS" -> palette.secondary
          else -> palette.primaryBright
        }

      // Per-node electronic blink wave scaled by real Telemetry Service packet intensity
      val rawBlink = (sin(blinkPhase + idx * 1.15f) + 1f) * 0.5f
      val sharpBlink = rawBlink * rawBlink
      val blinkStrength = sharpBlink * trafficBlend * node.trafficWeight.coerceIn(0.45f, 1f)

      val glowRadiusPx =
        ((6.5f + 2.0f * ambientBreath + 10.5f * blinkStrength + (if (isSelected) 4.0f else 0f)) *
            (if (node.isOrigin) 1.24f else 1.0f))
          .dp
          .toPx()

      val glowColor =
        if (trafficBlend > 0.05f && txSpeedKbps > rxSpeedKbps * 1.15f && node.isOrigin) {
          lerp(baseNodeColor, palette.warning, blinkStrength * 0.55f)
        } else {
          lerp(baseNodeColor, palette.success, blinkStrength * 0.45f)
        }

      // Multi-layered radial glow halo
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                glowColor.copy(alpha = (0.50f + 0.45f * blinkStrength) * depthAlpha),
                glowColor.copy(alpha = (0.16f + 0.24f * blinkStrength) * depthAlpha),
                Color.Transparent,
              ),
            center = pos,
            radius = glowRadiusPx,
          ),
        radius = glowRadiusPx,
        center = pos,
      )

      // Expanding packet-burst ping ring while blinking
      if (blinkStrength > 0.06f) {
        val ringPhase = ((blinkPhase / (2f * PI.toFloat())) + idx * 0.19f) % 1f
        val ringRadius = (3.8f + ringPhase * 11.5f).dp.toPx()
        val ringAlpha = ((1f - ringPhase) * 0.78f * trafficBlend * depthAlpha).coerceIn(0f, 1f)
        drawCircle(
          color = glowColor.copy(alpha = ringAlpha),
          radius = ringRadius,
          center = pos,
          style = Stroke(width = 1.3.dp.toPx()),
        )
      }

      // Precision targeting crosshair on local origin or selected node
      if ((node.isOrigin || isSelected) && pt.z > 0.05f) {
        val bracketR = (if (isSelected) 7.5f else 5.8f).dp.toPx()
        val bracketColor = (if (isSelected) palette.success else palette.primaryBright).copy(alpha = 0.75f * depthAlpha)
        drawLine(bracketColor, Offset(pos.x - bracketR, pos.y), Offset(pos.x - bracketR * 0.45f, pos.y), 1.dp.toPx())
        drawLine(bracketColor, Offset(pos.x + bracketR * 0.45f, pos.y), Offset(pos.x + bracketR, pos.y), 1.dp.toPx())
        drawLine(bracketColor, Offset(pos.x, pos.y - bracketR), Offset(pos.x, pos.y - bracketR * 0.45f), 1.dp.toPx())
        drawLine(bracketColor, Offset(pos.x, pos.y + bracketR * 0.45f), Offset(pos.x, pos.y + bracketR), 1.dp.toPx())
      }

      // Crisp inner glowing node core
      val coreRadius =
        ((if (node.isOrigin || isSelected) 3.2f else 2.4f) + 1.15f * blinkStrength).dp.toPx()
      val coreColor = lerp(baseNodeColor, palette.textPrimary, blinkStrength * 0.78f)
      drawCircle(
        color = coreColor.copy(alpha = depthAlpha),
        radius = coreRadius,
        center = pos,
      )
      drawCircle(
        color = palette.textPrimary.copy(alpha = (0.48f + 0.52f * blinkStrength) * depthAlpha),
        radius = (coreRadius * 0.44f).coerceAtLeast(1.dp.toPx()),
        center = pos,
      )

      // Micro HUD label on prominent front-facing nodes without overlap
      val shouldLabel =
        (isSelected || (pt.z > 0.36f && drawnLabelCenters.size < 3)) &&
          drawnLabelCenters.none { prev -> hypot(prev.x - pos.x, prev.y - pos.y) < minLabelSpacingPx }
      if (shouldLabel) {
        drawnLabelCenters += pos
        val measured = textMeasurer.measure(node.label, style = labelTextStyle)
        val padH = 3.dp.toPx()
        val padV = 1.dp.toPx()
        val lx = (pos.x + 6.dp.toPx()).coerceIn(4f, (size.width - measured.size.width - padH * 2 - 4f).coerceAtLeast(4f))
        val ly = (pos.y - measured.size.height / 2f).coerceIn(4f, (size.height - measured.size.height - padV * 2 - 4f).coerceAtLeast(4f))
        drawRoundRect(
          color = palette.surface.copy(alpha = 0.78f * depthAlpha),
          topLeft = Offset(lx - padH, ly - padV),
          size = Size(measured.size.width + padH * 2, measured.size.height + padV * 2),
          cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawText(
          textLayoutResult = measured,
          color = (if (isSelected || blinkStrength > 0.4f) palette.textPrimary else baseNodeColor).copy(alpha = depthAlpha),
          topLeft = Offset(lx, ly),
        )
      }
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcPacketPulse(
  originVec: Vec3,
  peerVec: Vec3,
  t: Float,
  yawRad: Float,
  pitchRad: Float,
  rollRad: Float,
  cx: Float,
  cy: Float,
  radius: Float,
  color: Color,
  alpha: Float,
) {
  val pktBase = slerpNormalized(originVec, peerVec, t)
  val pktAlt = 1.0f + sin(t * PI.toFloat()) * 0.16f
  val pktRot =
    rotatePoint3D(
      Vec3(pktBase.x * pktAlt, pktBase.y * pktAlt, pktBase.z * pktAlt),
      yawRad,
      pitchRad,
      rollRad,
    )
  if (pktRot.z > 0.02f) {
    val pktPos = Offset(cx + pktRot.x * radius, cy - pktRot.y * radius)
    drawCircle(
      color = color.copy(alpha = 0.38f * alpha),
      radius = 5.4.dp.toPx(),
      center = pktPos,
    )
    drawCircle(
      color = color.copy(alpha = alpha),
      radius = 2.3.dp.toPx(),
      center = pktPos,
    )
  }
}

private fun latLonToVec3(lat: Double, lon: Double): Vec3 {
  val cosLat = cos(lat)
  return Vec3(
    x = (cosLat * cos(lon)).toFloat(),
    y = sin(lat).toFloat(),
    z = (cosLat * sin(lon)).toFloat(),
  )
}

/** Applies 3D rotation matrix transformations: Yaw (Y-axis) -> Pitch (X-axis) -> Roll (Z-axis). */
private fun rotatePoint3D(v: Vec3, yaw: Float, pitch: Float, roll: Float): Vec3 {
  val cy = cos(yaw)
  val sy = sin(yaw)
  val x1 = v.x * cy - v.z * sy
  val z1 = v.x * sy + v.z * cy

  val cp = cos(pitch)
  val sp = sin(pitch)
  val y2 = v.y * cp - z1 * sp
  val z2 = v.y * sp + z1 * cp

  val cr = cos(roll)
  val sr = sin(roll)
  val x3 = x1 * cr - y2 * sr
  val y3 = x1 * sr + y2 * cr

  return Vec3(x3, y3, z2)
}

private fun slerpNormalized(a: Vec3, b: Vec3, t: Float): Vec3 {
  val lx = a.x + (b.x - a.x) * t
  val ly = a.y + (b.y - a.y) * t
  val lz = a.z + (b.z - a.z) * t
  val len = sqrt(lx * lx + ly * ly + lz * lz).coerceAtLeast(1e-5f)
  return Vec3(lx / len, ly / len, lz / len)
}

private val DEFAULT_FALLBACK_NODES =
  listOf(
    NetworkConnectionNode(
      id = "local_default",
      label = "LOCAL",
      endpoint = "127.0.0.1",
      protocol = "LOCAL",
      state = "IDLE",
      latDeg = 37.7,
      lonDeg = -122.4,
      isOrigin = true,
      trafficWeight = 0.6f,
    ),
    NetworkConnectionNode(
      id = "hub_london",
      label = "GW",
      endpoint = "gateway",
      protocol = "GW",
      state = "LINK",
      latDeg = 51.5,
      lonDeg = -0.1,
      trafficWeight = 0.6f,
    ),
  )

private val GLOBE_LAT_RING_VECTORS: List<Pair<Boolean, List<Vec3>>> =
  listOf(-60, -30, 0, 30, 60).map { latDeg ->
    val lat = latDeg * PI / 180.0
    val points = (0..360 step 10).map { lonDeg -> latLonToVec3(lat, lonDeg * PI / 180.0) }
    (latDeg == 0) to points
  }

private val GLOBE_LON_MERIDIAN_VECTORS: List<List<Vec3>> =
  (0 until 180 step 30).map { lonDeg ->
    val lon = lonDeg * PI / 180.0
    (-90..90 step 10).map { latDeg -> latLonToVec3(latDeg * PI / 180.0, lon) }
  }

private val GLOBE_CONTINENT_VECTORS: List<Vec3> =
  listOf(
    60.0 to -135.0, 56.0 to -120.0, 52.0 to -105.0, 48.0 to -95.0, 45.0 to -75.0,
    40.0 to -120.0, 38.0 to -105.0, 36.0 to -90.0, 34.0 to -80.0, 30.0 to -100.0,
    24.0 to -102.0, 18.0 to -94.0,
    8.0 to -72.0, 4.0 to -64.0, -2.0 to -78.0, -5.0 to -52.0, -10.0 to -60.0,
    -15.0 to -45.0, -22.0 to -48.0, -30.0 to -62.0, -38.0 to -68.0, -48.0 to -72.0,
    58.0 to -4.0, 55.0 to 10.0, 52.0 to 20.0, 48.0 to 4.0, 46.0 to 14.0,
    42.0 to -4.0, 42.0 to 12.0, 62.0 to 16.0, 65.0 to 26.0, 50.0 to 30.0,
    34.0 to -6.0, 32.0 to 12.0, 30.0 to 30.0, 22.0 to 10.0, 16.0 to -12.0,
    12.0 to 18.0, 8.0 to 38.0, 2.0 to 24.0, -6.0 to 16.0, -12.0 to 32.0,
    -22.0 to 22.0, -30.0 to 24.0, -18.0 to 46.0,
    62.0 to 50.0, 60.0 to 75.0, 58.0 to 105.0, 60.0 to 135.0, 52.0 to 85.0,
    46.0 to 65.0, 42.0 to 85.0, 40.0 to 116.0, 36.0 to 138.0, 32.0 to 105.0,
    26.0 to 78.0, 22.0 to 114.0, 18.0 to 76.0, 14.0 to 100.0, 24.0 to 46.0,
    32.0 to 52.0, 4.0 to 102.0, -6.0 to 110.0,
    -18.0 to 128.0, -22.0 to 142.0, -26.0 to 122.0, -28.0 to 136.0, -32.0 to 148.0,
    -35.0 to 138.0,
  ).map { (latDeg, lonDeg) -> latLonToVec3(latDeg * PI / 180.0, lonDeg * PI / 180.0) }
