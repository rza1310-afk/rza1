package com.example.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalEdexPalette
import kotlin.math.sin
import kotlinx.coroutines.isActive

private const val EDEX_GLITCH_AGSL =
  """
    uniform shader content;
    uniform float2 resolution;
    uniform float time;
    uniform float intensity;

    float hash(float n) {
        return fract(sin(n) * 43758.5453123);
    }

    half4 main(float2 fragCoord) {
        float2 safeRes = max(resolution, float2(1.0, 1.0));
        float row = floor(fragCoord.y / 14.0);
        float tick = floor(time * 10.0);
        float burst = step(0.70, hash(tick + row * 1.37));
        float sliceNoise = (hash(row * 17.3 + floor(time * 18.0)) - 0.5) * 2.0;
        float shiftX = sliceNoise * burst * intensity * 16.0;

        float chromaOffset = (1.5 + 3.5 * burst) * intensity;
        float2 sampleBase = float2(clamp(fragCoord.x + shiftX, 0.0, safeRes.x - 1.0), fragCoord.y);
        float2 sampleR = float2(clamp(sampleBase.x + chromaOffset, 0.0, safeRes.x - 1.0), fragCoord.y);
        float2 sampleB = float2(clamp(sampleBase.x - chromaOffset, 0.0, safeRes.x - 1.0), fragCoord.y);

        half r = content.eval(sampleR).r;
        half4 gCol = content.eval(sampleBase);
        half b = content.eval(sampleB).b;

        float scan = 1.0 - (0.14 * intensity * step(0.5, fract(fragCoord.y * 0.25)));
        return half4(r * scan, gCol.g * scan, b * scan, gCol.a);
    }
  """

/**
 * Wraps telemetry HUD components in an AGSL RuntimeShader glitch effect (API 33+) with a
 * lightweight Canvas glitch fallback (API 24..32) whenever system load exceeds the threshold.
 */
@Composable
fun HudGlitchShaderContainer(
  active: Boolean,
  intensity: Float,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val clampedIntensity = if (active) intensity.coerceIn(0.15f, 1f) else 0f
  val palette = LocalEdexPalette.current
  var timeSec by remember { mutableFloatStateOf(0f) }
  var boxSize by remember { mutableStateOf(IntSize.Zero) }

  LaunchedEffect(active) {
    if (!active) {
      timeSec = 0f
      return@LaunchedEffect
    }
    var startMs = -1L
    while (isActive) {
      withFrameMillis { frameMs ->
        if (startMs < 0L) startMs = frameMs
        timeSec = (frameMs - startMs) / 1000f
      }
    }
  }

  val runtimeShader =
    remember {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { createAgslGlitchShader() }.getOrNull()
      } else {
        null
      }
    }

  val glitchModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
      Modifier.onSizeChanged { boxSize = it }
        .graphicsLayer {
          if (active && boxSize.width > 0 && boxSize.height > 0) {
            runtimeShader.setFloatUniform(
              "resolution",
              boxSize.width.toFloat(),
              boxSize.height.toFloat(),
            )
            runtimeShader.setFloatUniform("time", timeSec)
            runtimeShader.setFloatUniform("intensity", clampedIntensity)
            renderEffect =
              AndroidRenderEffect.createRuntimeShaderEffect(runtimeShader, "content")
                .asComposeRenderEffect()
          } else {
            renderEffect = null
          }
        }
    } else {
      // Pre-API 33 fallback: horizontal glitch tear bands & chromatic edge flash
      Modifier.drawWithContent {
        drawContent()
        if (!active) return@drawWithContent
        val pulse = ((sin(timeSec * 22f) + 1f) * 0.5f) * clampedIntensity
        if (pulse > 0.25f) {
          val bandY = ((timeSec * 190f) % size.height.coerceAtLeast(1f))
          drawRect(
            color = palette.danger.copy(alpha = 0.14f * pulse),
            topLeft = Offset(0f, bandY),
            size = Size(size.width, 6.dp.toPx()),
          )
          drawRect(
            color = palette.primaryBright.copy(alpha = 0.16f * pulse),
            topLeft = Offset(0f, (bandY + 18.dp.toPx()) % size.height.coerceAtLeast(1f)),
            size = Size(size.width, 3.dp.toPx()),
          )
        }
      }
    }

  Box(
    modifier = modifier.then(glitchModifier).testTag(if (active) "glitch_active" else "glitch_idle"),
    content = content,
  )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createAgslGlitchShader(): RuntimeShader = RuntimeShader(EDEX_GLITCH_AGSL)
