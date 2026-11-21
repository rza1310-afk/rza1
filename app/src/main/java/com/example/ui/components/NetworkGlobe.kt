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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.google.android.filament.Filament
import com.google.android.filament.MaterialInstance
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Safely probes whether Google Filament native JNI libraries can initialize in the current runtime.
 * Returns false on headless JVM test runners (e.g., Robolectric) so Compose UI tests fall back
 * cleanly without triggering UnsatisfiedLinkError.
 */
object SceneViewRuntimeSupport {
  val isAvailable: Boolean by lazy {
    runCatching {
      Filament.init()
      true
    }.getOrDefault(false)
  }
}

private data class GlobeBeaconBinding(
  val index: Int,
  val spec: NetworkConnectionNode,
  val node: SphereNode,
  val originPos: Position,
  val peerPos: Position,
)

private data class GlobePacketPulseBinding(
  val linkIndex: Int,
  val originPos: Position,
  val peerPos: Position,
  val rxNode: SphereNode,
  val txNode: SphereNode,
)

private data class ProjectedNodeOverlay(
  val node: NetworkConnectionNode,
  val screenPos: Offset,
  val facingDot: Float,
  val depth: Float,
)

/**
 * 3D Earth visualization powered by SceneView (Google Filament) for Jetpack Compose.
 *
 * Features:
 * - Procedural 3D Earth sphere core with equatorial/axial structural rings and continental lattice.
 * - Interactive camera orbit rotation (touch drag to orbit, tap to pause/resume, double-tap to
 *   reset, plus smooth focus tracking when selecting an active network node).
 * - Synchronized 3D beacons + 2D HUD node point overlays showing live network traffic rings,
 *   RX/TX packet arcs, and endpoint labels.
 */
@Composable
fun NetworkGlobe(
  networkConnected: Boolean,
  modifier: Modifier = Modifier,
  connections: List<NetworkConnectionNode> = emptyList(),
  packetTrafficActive: Boolean = false,
  packetIntensity: Float = 0f,
  rxSpeedKbps: Float = 0f,
  txSpeedKbps: Float = 0f,
  selectedNodeId: String? = null,
  onSelectNode: ((String) -> Unit)? = null,
  onGlobeTap: () -> Unit = {},
  onGlobeReset: () -> Unit = {},
) {
  if (!SceneViewRuntimeSupport.isAvailable) {
    WireframeGlobeCanvas(
      networkConnected = networkConnected,
      connections = connections,
      packetTrafficActive = packetTrafficActive,
      packetIntensity = packetIntensity,
      rxSpeedKbps = rxSpeedKbps,
      txSpeedKbps = txSpeedKbps,
      selectedNodeId = selectedNodeId,
      modifier = modifier,
      onGlobeTap = onGlobeTap,
      onGlobeReset = onGlobeReset,
    )
    return
  }

  val palette = LocalEdexPalette.current
  val textMeasurer = rememberTextMeasurer()
  val engine = rememberEngine()
  val modelLoader = rememberModelLoader(engine)
  val materialLoader = rememberMaterialLoader(engine)
  val environmentLoader = rememberEnvironmentLoader(engine)

  val activeNodes =
    remember(connections, networkConnected) {
      if (connections.isNotEmpty()) {
        connections
      } else if (networkConnected) {
        NETWORK_FALLBACK_NODES
      } else {
        listOf(NETWORK_FALLBACK_NODES.first())
      }
    }

  val originNode = activeNodes.firstOrNull { it.isOrigin } ?: activeNodes.firstOrNull()
  val defaultCameraYawDeg =
    remember(originNode?.lonDeg) {
      if (originNode != null) originNode.lonDeg.toFloat() else 25f
    }
  val defaultCameraPitchDeg =
    remember(originNode?.latDeg) {
      if (originNode != null) (originNode.latDeg * 0.35).toFloat().coerceIn(-40f, 40f) else 18f
    }
  val latestDefaultYawDeg by rememberUpdatedState(defaultCameraYawDeg)
  val latestDefaultPitchDeg by rememberUpdatedState(defaultCameraPitchDeg)

  var autoRotate by remember { mutableStateOf(true) }
  var cameraYawDeg by remember { mutableFloatStateOf(defaultCameraYawDeg) }
  var cameraPitchDeg by remember { mutableFloatStateOf(defaultCameraPitchDeg) }
  var lastFrameNanos by remember { mutableLongStateOf(0L) }

  val targetTrafficIntensity =
    if (networkConnected && packetTrafficActive) packetIntensity.coerceIn(0.35f, 1f) else 0f
  val trafficBlend by
    animateFloatAsState(
      targetValue = targetTrafficIntensity,
      animationSpec = tween(durationMillis = 280),
      label = "network_globe_traffic_blend",
    )

  val latestTrafficBlend by rememberUpdatedState(trafficBlend)
  val latestAutoRotate by rememberUpdatedState(autoRotate)
  val latestSelectedNodeId by rememberUpdatedState(selectedNodeId)
  val latestRxSpeed by rememberUpdatedState(rxSpeedKbps)
  val latestTxSpeed by rememberUpdatedState(txSpeedKbps)
  val latestConnected by rememberUpdatedState(networkConnected)

  val selectedNode =
    remember(selectedNodeId, activeNodes) { activeNodes.firstOrNull { it.id == selectedNodeId } }
  val latestFocusYawDeg by rememberUpdatedState(selectedNode?.lonDeg?.toFloat())
  val latestFocusPitchDeg by
    rememberUpdatedState(selectedNode?.latDeg?.times(0.45)?.toFloat()?.coerceIn(-55f, 55f))

  LaunchedEffect(selectedNodeId) {
    if (selectedNodeId != null) {
      autoRotate = false
    }
  }

  // Palette-synced Filament MaterialInstances for Earth sphere, rings, landmass lattice, and beacons
  val earthCoreMaterial =
    remember(palette.background, palette.primaryDim) {
      materialLoader.createColorInstance(
        color = blendColors(palette.background, palette.primaryDim, 0.42f),
        metallic = 0.22f,
        roughness = 0.68f,
        reflectance = 0.35f,
      )
    }
  val continentMaterial =
    remember(palette.primary) {
      materialLoader.createColorInstance(
        color = palette.primary,
        metallic = 0.1f,
        roughness = 0.28f,
        reflectance = 0.72f,
      )
    }
  val ringMaterial =
    remember(palette.border, palette.primaryBright) {
      materialLoader.createColorInstance(
        color = blendColors(palette.border, palette.primaryBright, 0.55f),
        metallic = 0.58f,
        roughness = 0.26f,
        reflectance = 0.8f,
      )
    }
  val originMaterial =
    remember(palette.primaryBright, networkConnected, palette.warning) {
      materialLoader.createColorInstance(
        color = if (networkConnected) palette.primaryBright else palette.warning,
        metallic = 0.05f,
        roughness = 0.18f,
        reflectance = 0.92f,
      )
    }
  val socketMaterial =
    remember(palette.success) {
      materialLoader.createColorInstance(
        color = palette.success,
        metallic = 0.05f,
        roughness = 0.16f,
        reflectance = 0.92f,
      )
    }
  val dnsMaterial =
    remember(palette.secondary) {
      materialLoader.createColorInstance(
        color = palette.secondary,
        metallic = 0.05f,
        roughness = 0.2f,
        reflectance = 0.85f,
      )
    }
  val txMaterial =
    remember(palette.warning) {
      materialLoader.createColorInstance(
        color = palette.warning,
        metallic = 0.0f,
        roughness = 0.14f,
        reflectance = 0.95f,
      )
    }

  val earthRootNode = remember(engine) { Node(engine = engine) }

  val earthSphereNode =
    remember(engine, earthCoreMaterial) {
      SphereNode(
        engine = engine,
        radius = EARTH_RADIUS,
        materialInstance = earthCoreMaterial,
      )
    }

  val equatorSegments =
    remember(engine, ringMaterial) {
      (0 until 36).map { idx ->
        val angleRad = idx * (2.0 * PI / 36.0)
        CylinderNode(
          engine = engine,
          radius = 0.007f,
          height = 0.16f,
          materialInstance = ringMaterial,
        ).apply {
          val x = (cos(angleRad) * (EARTH_RADIUS + 0.022f)).toFloat()
          val z = (sin(angleRad) * (EARTH_RADIUS + 0.022f)).toFloat()
          position = Position(x = x, y = 0f, z = z)
          rotation = Rotation(x = 90f, y = (-idx * 10f), z = 0f)
        }
      }
    }

  val polarAxisNode =
    remember(engine, ringMaterial) {
      CylinderNode(
        engine = engine,
        radius = 0.005f,
        height = EARTH_RADIUS * 2.3f,
        materialInstance = ringMaterial,
      )
    }

  val continentDotNodes =
    remember(engine, continentMaterial) {
      EARTH_CONTINENT_COORDS.map { (latDeg, lonDeg) ->
        SphereNode(
          engine = engine,
          radius = 0.015f,
          materialInstance = continentMaterial,
        ).apply {
          position = latLonToWorldPosition(latDeg, lonDeg, EARTH_RADIUS + 0.012f)
        }
      }
    }

  val dynamicBeacons = remember { mutableStateOf<List<GlobeBeaconBinding>>(emptyList()) }
  val pulseBindings = remember { mutableStateOf<List<GlobePacketPulseBinding>>(emptyList()) }

  DisposableEffect(
    activeNodes,
    originMaterial,
    socketMaterial,
    dnsMaterial,
    ringMaterial,
    txMaterial,
  ) {
    earthRootNode.clearChildNodes()
    earthRootNode.addChildNode(earthSphereNode)
    earthRootNode.addChildNode(polarAxisNode)
    equatorSegments.forEach { earthRootNode.addChildNode(it) }
    continentDotNodes.forEach { earthRootNode.addChildNode(it) }

    val originSpec = activeNodes.firstOrNull { it.isOrigin } ?: activeNodes.first()
    val originPos = latLonToWorldPosition(originSpec.latDeg, originSpec.lonDeg, EARTH_RADIUS + 0.03f)

    val beacons =
      activeNodes.mapIndexed { idx, spec ->
        val mat: MaterialInstance =
          when {
            spec.id == selectedNodeId -> socketMaterial
            spec.isOrigin -> originMaterial
            spec.protocol == "TCP" || spec.protocol == "UDP" -> socketMaterial
            spec.protocol == "DNS" -> dnsMaterial
            else -> ringMaterial
          }
        val pos = latLonToWorldPosition(spec.latDeg, spec.lonDeg, EARTH_RADIUS + 0.034f)
        val beaconSphere =
          SphereNode(
            engine = engine,
            radius = if (spec.isOrigin) 0.036f else 0.024f,
            materialInstance = mat,
          ).apply {
            position = pos
          }
        earthRootNode.addChildNode(beaconSphere)
        GlobeBeaconBinding(
          index = idx,
          spec = spec,
          node = beaconSphere,
          originPos = originPos,
          peerPos = pos,
        )
      }

    val pulses = mutableListOf<GlobePacketPulseBinding>()
    var linkIndex = 0
    beacons.forEach { binding ->
      if (binding.spec.id == originSpec.id) return@forEach
      val rxProbe =
        SphereNode(engine = engine, radius = 0.017f, materialInstance = socketMaterial).apply {
          scale = Scale(0.001f)
          position = binding.peerPos
        }
      val txProbe =
        SphereNode(engine = engine, radius = 0.017f, materialInstance = txMaterial).apply {
          scale = Scale(0.001f)
          position = binding.originPos
        }
      earthRootNode.addChildNode(rxProbe)
      earthRootNode.addChildNode(txProbe)
      pulses +=
        GlobePacketPulseBinding(
          linkIndex = linkIndex++,
          originPos = binding.originPos,
          peerPos = binding.peerPos,
          rxNode = rxProbe,
          txNode = txProbe,
        )
    }

    dynamicBeacons.value = beacons
    pulseBindings.value = pulses

    onDispose {
      beacons.forEach { it.node.destroy() }
      pulses.forEach {
        it.rxNode.destroy()
        it.txNode.destroy()
      }
    }
  }

  val cameraNode =
    rememberCameraNode(engine) {
      position = orbitCameraPosition(defaultCameraYawDeg, defaultCameraPitchDeg, CAMERA_ORBIT_RADIUS)
      lookAt(Position(0f, 0f, 0f))
    }

  val mainLightNode =
    rememberMainLightNode(engine) {
      intensity = 95_000f
      position = Position(x = 2.8f, y = 3.2f, z = 4.2f)
    }

  val overlayTransition = rememberInfiniteTransition(label = "network_globe_overlay_anim")
  val reticleAngle by
    overlayTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
      label = "network_globe_reticle_deg",
    )
  val trafficPulsePhase by
    overlayTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing), RepeatMode.Restart),
      label = "network_globe_traffic_ring",
    )

  val overlayLabelStyle =
    remember(palette.textPrimary) {
      TextStyle(
        color = palette.textPrimary,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
      )
    }
  val latestProjectedNodes = remember { mutableStateOf<List<ProjectedNodeOverlay>>(emptyList()) }

  Box(
    modifier =
      modifier
        .testTag("network_globe_scene")
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            autoRotate = false
            cameraYawDeg = (cameraYawDeg - dragAmount.x * 0.42f) % 360f
            cameraPitchDeg = (cameraPitchDeg + dragAmount.y * 0.35f).coerceIn(-65f, 65f)
          }
        }
        .pointerInput(onSelectNode) {
          detectTapGestures(
            onTap = { tapOffset ->
              val hit =
                latestProjectedNodes.value
                  .filter { it.facingDot > -0.1f }
                  .minByOrNull { hypot(it.screenPos.x - tapOffset.x, it.screenPos.y - tapOffset.y) }
              if (hit != null &&
                onSelectNode != null &&
                hypot(hit.screenPos.x - tapOffset.x, hit.screenPos.y - tapOffset.y) <= 28.dp.toPx()
              ) {
                autoRotate = false
                onSelectNode(hit.node.id)
              } else {
                autoRotate = !autoRotate
                onGlobeTap()
              }
            },
            onDoubleTap = {
              cameraYawDeg = latestDefaultYawDeg
              cameraPitchDeg = latestDefaultPitchDeg
              autoRotate = true
              onGlobeReset()
            },
          )
        }
  ) {
    Scene(
      modifier = Modifier.fillMaxSize(),
      engine = engine,
      modelLoader = modelLoader,
      materialLoader = materialLoader,
      environmentLoader = environmentLoader,
      cameraNode = cameraNode,
      cameraManipulator = null,
      mainLightNode = mainLightNode,
      isOpaque = false,
      childNodes = rememberNodes { add(earthRootNode) },
      onFrame = { frameTimeNanos ->
        val dtSec =
          if (lastFrameNanos == 0L) 0.016f
          else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.1f)
        lastFrameNanos = frameTimeNanos

        // Smooth camera orbit rotation around the 3D Earth center
        val focusYaw = latestFocusYawDeg
        val focusPitch = latestFocusPitchDeg
        if (focusYaw != null && focusPitch != null && !latestAutoRotate) {
          val yawDelta = normalizeAngleDeg(focusYaw - cameraYawDeg)
          val pitchDelta = focusPitch - cameraPitchDeg
          val factor = (dtSec * 6f).coerceIn(0.05f, 0.35f)
          cameraYawDeg += yawDelta * factor
          cameraPitchDeg = (cameraPitchDeg + pitchDelta * factor).coerceIn(-65f, 65f)
        } else if (latestAutoRotate) {
          cameraYawDeg = (cameraYawDeg + 18f * dtSec) % 360f
        }

        val camPos = orbitCameraPosition(cameraYawDeg, cameraPitchDeg, CAMERA_ORBIT_RADIUS)
        cameraNode.position = camPos
        cameraNode.lookAt(Position(0f, 0f, 0f))

        // Animate 3D surface node beacons & RX/TX packet probes
        val timeSec = (frameTimeNanos / 1_000_000_000.0).toFloat()
        val traffic = latestTrafficBlend
        dynamicBeacons.value.forEach { binding ->
          val focused = latestSelectedNodeId != null && binding.spec.id == latestSelectedNodeId
          val baseScale = if (focused) 1.45f else 1.0f
          val blinkWave = ((sin(timeSec * 15f + binding.index * 1.15f) + 1f) * 0.5f)
          val pulseScale =
            baseScale +
              0.55f *
                blinkWave *
                blinkWave *
                traffic *
                binding.spec.trafficWeight.coerceIn(0.45f, 1f)
          binding.node.scale = Scale(pulseScale)
        }

        val showRx = latestConnected && traffic > 0.04f && (latestRxSpeed > 0f || latestTxSpeed <= 0f)
        val showTx = latestConnected && traffic > 0.04f && latestTxSpeed > 0f
        pulseBindings.value.forEach { link ->
          if (showRx) {
            val rxT = 1f - (((timeSec * 0.65f) + link.linkIndex * 0.23f) % 1f)
            link.rxNode.position = arcWorldPosition(link.originPos, link.peerPos, rxT)
            link.rxNode.scale = Scale(0.8f + 0.5f * traffic)
          } else {
            link.rxNode.scale = Scale(0.001f)
          }
          if (showTx) {
            val txT = ((timeSec * 0.65f) + link.linkIndex * 0.37f + 0.5f) % 1f
            link.txNode.position = arcWorldPosition(link.originPos, link.peerPos, txT)
            link.txNode.scale = Scale(0.8f + 0.5f * traffic)
          } else {
            link.txNode.scale = Scale(0.001f)
          }
        }
      },
    )

    // Projected HUD reticle & network traffic node point overlays synchronized with camera orbit
    Canvas(modifier = Modifier.fillMaxSize().testTag("network_globe_node_overlay")) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val reticleRadius = minOf(size.width, size.height) * 0.44f
      if (reticleRadius > 4f) {
        rotate(degrees = reticleAngle, pivot = Offset(cx, cy)) {
          for (deg in 0 until 360 step 15) {
            val rad = deg * (PI / 180.0)
            val tickLen = if (deg % 45 == 0) 6.dp.toPx() else 3.dp.toPx()
            drawLine(
              color = palette.primary.copy(alpha = if (deg % 45 == 0) 0.5f else 0.2f),
              start =
                Offset(
                  cx + (cos(rad) * (reticleRadius - tickLen)).toFloat(),
                  cy + (sin(rad) * (reticleRadius - tickLen)).toFloat(),
                ),
              end =
                Offset(
                  cx + (cos(rad) * reticleRadius).toFloat(),
                  cy + (sin(rad) * reticleRadius).toFloat(),
                ),
              strokeWidth = if (deg % 45 == 0) 1.4.dp.toPx() else 0.8.dp.toPx(),
            )
          }
        }
      }

      val projected =
        projectNodesToViewport(
          nodes = activeNodes,
          cameraYawDeg = cameraYawDeg,
          cameraPitchDeg = cameraPitchDeg,
          viewportWidth = size.width,
          viewportHeight = size.height,
        )
      latestProjectedNodes.value = projected

      val originProjection = projected.firstOrNull { it.node.isOrigin } ?: projected.firstOrNull()
      val traffic = trafficBlend
      val dashEffect =
        PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), -trafficPulsePhase * 18.dp.toPx())

      // Draw network traffic link overlays from origin to active peer nodes
      if (originProjection != null) {
        projected.forEach { target ->
          if (target.node.id == originProjection.node.id) return@forEach
          if (originProjection.facingDot < -0.2f && target.facingDot < -0.2f) return@forEach
          val linkColor =
            if (!networkConnected) palette.warning
            else if (target.node.protocol == "TCP" || target.node.protocol == "UDP") palette.success
            else palette.primaryBright
          val alpha =
            if (target.facingDot > 0f && originProjection.facingDot > 0f) 0.55f + 0.35f * traffic
            else 0.25f
          drawLine(
            color = linkColor.copy(alpha = alpha.coerceIn(0.15f, 0.9f)),
            start = originProjection.screenPos,
            end = target.screenPos,
            strokeWidth = (1.1.dp.toPx() + traffic * 0.8.dp.toPx()),
            cap = StrokeCap.Round,
            pathEffect = if (traffic > 0.05f) dashEffect else null,
          )
        }
      }

      // Draw glowing node point overlays and endpoint tags for front-facing network nodes
      projected
        .sortedByDescending { it.depth }
        .forEach { item ->
          if (item.facingDot < -0.12f) return@forEach
          val node = item.node
          val isSelected = selectedNodeId != null && node.id == selectedNodeId
          val baseColor =
            when {
              !networkConnected -> palette.warning
              isSelected -> palette.success
              node.isOrigin -> palette.primaryBright
              node.protocol == "TCP" || node.protocol == "UDP" -> palette.success
              node.protocol == "DNS" -> palette.secondary
              else -> palette.primary
            }

          // Traffic pulse halo when Telemetry Service detects packet bursts
          if (networkConnected && (traffic > 0.04f || isSelected)) {
            val wave = ((trafficPulsePhase + node.trafficWeight) % 1f)
            val ringRadius = (6.dp.toPx() + wave * (10.dp.toPx() + 6.dp.toPx() * traffic))
            val ringAlpha = ((1f - wave) * (0.4f + 0.5f * traffic)).coerceIn(0f, 0.9f)
            drawCircle(
              color = baseColor.copy(alpha = ringAlpha),
              radius = ringRadius,
              center = item.screenPos,
              style = Stroke(width = if (isSelected) 1.8.dp.toPx() else 1.1.dp.toPx()),
            )
          }

          val dotRadius = if (node.isOrigin || isSelected) 4.5.dp.toPx() else 3.2.dp.toPx()
          drawCircle(
            color = baseColor.copy(alpha = 0.28f),
            radius = dotRadius * 1.9f,
            center = item.screenPos,
          )
          drawCircle(
            color = baseColor,
            radius = dotRadius,
            center = item.screenPos,
          )

          if (node.isOrigin || isSelected || item.facingDot > 0.28f) {
            val tagText =
              if (isSelected) "${node.label} [${node.protocol}]" else node.label
            val measured = textMeasurer.measure(tagText, style = overlayLabelStyle)
            val labelX =
              (item.screenPos.x + 7.dp.toPx()).coerceIn(2f, (size.width - measured.size.width - 2f).coerceAtLeast(2f))
            val labelY =
              (item.screenPos.y - measured.size.height / 2f).coerceIn(2f, (size.height - measured.size.height - 2f).coerceAtLeast(2f))
            drawText(
              textLayoutResult = measured,
              color = if (isSelected) baseColor else palette.textPrimary.copy(alpha = 0.88f),
              topLeft = Offset(labelX, labelY),
            )
          }
        }
    }
  }
}

private const val EARTH_RADIUS = 0.86f
private const val CAMERA_ORBIT_RADIUS = 2.65f
private const val CAMERA_VERTICAL_FOV_DEG = 45.0

private fun latLonToWorldPosition(latDeg: Double, lonDeg: Double, radius: Float): Position {
  val lat = latDeg * (PI / 180.0)
  val lon = lonDeg * (PI / 180.0)
  val cosLat = cos(lat)
  return Position(
    x = (radius * cosLat * sin(lon)).toFloat(),
    y = (radius * sin(lat)).toFloat(),
    z = (radius * cosLat * cos(lon)).toFloat(),
  )
}

private fun orbitCameraPosition(yawDeg: Float, pitchDeg: Float, distance: Float): Position {
  val yaw = yawDeg * (PI / 180.0)
  val pitch = pitchDeg * (PI / 180.0)
  val cosPitch = cos(pitch)
  return Position(
    x = (distance * cosPitch * sin(yaw)).toFloat(),
    y = (distance * sin(pitch)).toFloat(),
    z = (distance * cosPitch * cos(yaw)).toFloat(),
  )
}

private fun projectNodesToViewport(
  nodes: List<NetworkConnectionNode>,
  cameraYawDeg: Float,
  cameraPitchDeg: Float,
  viewportWidth: Float,
  viewportHeight: Float,
): List<ProjectedNodeOverlay> {
  if (viewportWidth <= 1f || viewportHeight <= 1f) return emptyList()
  val yaw = cameraYawDeg * (PI / 180.0)
  val pitch = cameraPitchDeg * (PI / 180.0)
  val sinY = sin(yaw).toFloat()
  val cosY = cos(yaw).toFloat()
  val sinP = sin(pitch).toFloat()
  val cosP = cos(pitch).toFloat()

  // Camera position & orthonormal basis (Right, Up, Forward)
  val camX = CAMERA_ORBIT_RADIUS * cosP * sinY
  val camY = CAMERA_ORBIT_RADIUS * sinP
  val camZ = CAMERA_ORBIT_RADIUS * cosP * cosY

  // Unit vector pointing from origin toward camera (for front-hemisphere dot check)
  val viewDirX = cosP * sinY
  val viewDirY = sinP
  val viewDirZ = cosP * cosY

  val fwdX = -viewDirX
  val fwdY = -viewDirY
  val fwdZ = -viewDirZ

  val rightX = cosY
  val rightY = 0f
  val rightZ = -sinY

  // Up = Right x Forward
  val upX = rightY * fwdZ - rightZ * fwdY
  val upY = rightZ * fwdX - rightX * fwdZ
  val upZ = rightX * fwdY - rightY * fwdX

  val tanHalfFov = tan(CAMERA_VERTICAL_FOV_DEG * 0.5 * (PI / 180.0)).toFloat()
  val focalPx = (minOf(viewportWidth, viewportHeight) * 0.5f) / tanHalfFov
  val cx = viewportWidth * 0.5f
  val cy = viewportHeight * 0.5f

  return nodes.mapNotNull { spec ->
    val world = latLonToWorldPosition(spec.latDeg, spec.lonDeg, EARTH_RADIUS + 0.034f)
    val relX = world.x - camX
    val relY = world.y - camY
    val relZ = world.z - camZ

    val camSpaceX = relX * rightX + relY * rightY + relZ * rightZ
    val camSpaceY = relX * upX + relY * upY + relZ * upZ
    val camSpaceZ = relX * fwdX + relY * fwdY + relZ * fwdZ
    if (camSpaceZ <= 0.1f) return@mapNotNull null

    val worldLen = sqrt(world.x * world.x + world.y * world.y + world.z * world.z).coerceAtLeast(1e-5f)
    val facingDot = (world.x * viewDirX + world.y * viewDirY + world.z * viewDirZ) / worldLen

    val sx = cx + (camSpaceX / camSpaceZ) * focalPx
    val sy = cy - (camSpaceY / camSpaceZ) * focalPx
    ProjectedNodeOverlay(
      node = spec,
      screenPos = Offset(sx, sy),
      facingDot = facingDot,
      depth = camSpaceZ,
    )
  }
}

private fun arcWorldPosition(a: Position, b: Position, t: Float): Position {
  val lx = a.x + (b.x - a.x) * t
  val ly = a.y + (b.y - a.y) * t
  val lz = a.z + (b.z - a.z) * t
  val len = sqrt(lx * lx + ly * ly + lz * lz).coerceAtLeast(1e-5f)
  val altitude = (EARTH_RADIUS + 0.03f) + sin(t * PI.toFloat()) * 0.16f
  return Position(x = (lx / len) * altitude, y = (ly / len) * altitude, z = (lz / len) * altitude)
}

private fun normalizeAngleDeg(deg: Float): Float {
  var a = deg % 360f
  if (a > 180f) a -= 360f
  if (a < -180f) a += 360f
  return a
}

private fun blendColors(a: Color, b: Color, t: Float): Color {
  val clamped = t.coerceIn(0f, 1f)
  return Color(
    red = a.red + (b.red - a.red) * clamped,
    green = a.green + (b.green - a.green) * clamped,
    blue = a.blue + (b.blue - a.blue) * clamped,
    alpha = 1f,
  )
}

private val NETWORK_FALLBACK_NODES =
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

private val EARTH_CONTINENT_COORDS =
  listOf(
    60.0 to -135.0, 52.0 to -105.0, 45.0 to -75.0, 40.0 to -120.0, 36.0 to -90.0, 30.0 to -100.0,
    18.0 to -94.0, 8.0 to -72.0, -5.0 to -52.0, -15.0 to -45.0, -30.0 to -62.0, -48.0 to -72.0,
    58.0 to -4.0, 52.0 to 20.0, 46.0 to 14.0, 42.0 to -4.0, 62.0 to 16.0, 50.0 to 30.0,
    34.0 to -6.0, 30.0 to 30.0, 16.0 to -12.0, 8.0 to 38.0, -6.0 to 16.0, -22.0 to 22.0,
    60.0 to 75.0, 58.0 to 105.0, 60.0 to 135.0, 46.0 to 65.0, 40.0 to 116.0, 36.0 to 138.0,
    26.0 to 78.0, 22.0 to 114.0, 14.0 to 100.0, 4.0 to 102.0, -6.0 to 110.0,
    -18.0 to 128.0, -26.0 to 122.0, -32.0 to 148.0, -35.0 to 138.0,
  )
