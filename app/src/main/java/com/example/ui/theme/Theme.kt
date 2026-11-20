package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalEdexPalette = staticCompositionLocalOf { EdexPalettes.Tron }

val EdexShapes =
  Shapes(
    extraSmall = CutCornerShape(4.dp),
    small = CutCornerShape(6.dp),
    medium = CutCornerShape(8.dp),
    large = CutCornerShape(12.dp),
    extraLarge = CutCornerShape(16.dp),
  )

@Composable
fun EdexTheme(
  themeId: EdexThemeId = EdexThemeId.TRON,
  content: @Composable () -> Unit,
) {
  val palette = EdexPalettes.byId(themeId)
  val colorScheme =
    darkColorScheme(
      primary = palette.primary,
      onPrimary = palette.background,
      primaryContainer = palette.primaryDim,
      onPrimaryContainer = palette.primary,
      secondary = palette.secondary,
      onSecondary = palette.background,
      tertiary = palette.primaryBright,
      background = palette.background,
      onBackground = palette.textPrimary,
      surface = palette.surface,
      onSurface = palette.textPrimary,
      surfaceVariant = palette.surfaceElevated,
      onSurfaceVariant = palette.textMuted,
      outline = palette.border,
      error = palette.danger,
    )

  CompositionLocalProvider(LocalEdexPalette provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = EdexTypography,
      shapes = EdexShapes,
      content = content,
    )
  }
}

@Composable
fun TelemetryThemeOverlay(
  telemetryThemeId: TelemetryThemeId,
  globalThemeId: EdexThemeId,
  content: @Composable () -> Unit,
) {
  val target = EdexPalettes.forTelemetryTheme(telemetryThemeId, globalThemeId)
  val spec = tween<androidx.compose.ui.graphics.Color>(durationMillis = 280)

  val background by animateColorAsState(target.background, spec, label = "tel_bg")
  val surface by animateColorAsState(target.surface, spec, label = "tel_surface")
  val surfaceElevated by animateColorAsState(target.surfaceElevated, spec, label = "tel_surface_elev")
  val primary by animateColorAsState(target.primary, spec, label = "tel_primary")
  val primaryBright by animateColorAsState(target.primaryBright, spec, label = "tel_primary_bright")
  val primaryDim by animateColorAsState(target.primaryDim, spec, label = "tel_primary_dim")
  val secondary by animateColorAsState(target.secondary, spec, label = "tel_secondary")
  val border by animateColorAsState(target.border, spec, label = "tel_border")
  val gridLine by animateColorAsState(target.gridLine, spec, label = "tel_grid")
  val textPrimary by animateColorAsState(target.textPrimary, spec, label = "tel_text")
  val textMuted by animateColorAsState(target.textMuted, spec, label = "tel_text_muted")
  val success by animateColorAsState(target.success, spec, label = "tel_success")
  val warning by animateColorAsState(target.warning, spec, label = "tel_warning")
  val danger by animateColorAsState(target.danger, spec, label = "tel_danger")

  val animatedPalette =
    EdexPalette(
      id = target.id,
      background = background,
      surface = surface,
      surfaceElevated = surfaceElevated,
      primary = primary,
      primaryBright = primaryBright,
      primaryDim = primaryDim,
      secondary = secondary,
      border = border,
      gridLine = gridLine,
      textPrimary = textPrimary,
      textMuted = textMuted,
      success = success,
      warning = warning,
      danger = danger,
    )

  CompositionLocalProvider(LocalEdexPalette provides animatedPalette, content = content)
}
