package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class EdexThemeId(val displayName: String, val subtitle: String) {
  TRON("TRON", "Classic Cyber Cyan"),
  BLADE("BLADE", "2049 Amber Phosphor"),
  APOLLO("APOLLO", "Lunar Command Frost"),
  MATRIX("MATRIX", "Phosphor Emerald Grid"),
  CHALK("CHALK", "Crimson Tactical Red"),
}

enum class TelemetryThemeId(
  val displayName: String,
  val shortLabel: String,
  val subtitle: String,
) {
  SYNC_HUD("Sync With HUD", "HUD SYNC", "Matches global interface theme"),
  CYBERPUNK_NEON("Cyberpunk Neon", "CYBERPUNK", "Electric Magenta & Cyber Cyan"),
  MONOCHROME_TERMINAL("Monochrome Terminal", "MONO CRT", "High-Contrast Phosphor Grayscale"),
  MILITARY_HUD("Military HUD", "MILITARY", "Tactical NVG Olive & Amber"),
}

@Immutable
data class EdexPalette(
  val id: EdexThemeId,
  val background: Color,
  val surface: Color,
  val surfaceElevated: Color,
  val primary: Color,
  val primaryBright: Color,
  val primaryDim: Color,
  val secondary: Color,
  val border: Color,
  val gridLine: Color,
  val textPrimary: Color,
  val textMuted: Color,
  val success: Color,
  val warning: Color,
  val danger: Color,
)

object EdexPalettes {
  val Tron =
    EdexPalette(
      id = EdexThemeId.TRON,
      background = Color(0xFF05080A),
      surface = Color(0xFF081016),
      surfaceElevated = Color(0xFF0C1821),
      primary = Color(0xFFAAE8FF),
      primaryBright = Color(0xFF00E5FF),
      primaryDim = Color(0xFF1D4E63),
      secondary = Color(0xFF4DD0E1),
      border = Color(0x59AAE8FF),
      gridLine = Color(0x1F00E5FF),
      textPrimary = Color(0xFFDFF7FF),
      textMuted = Color(0xFF6B99AC),
      success = Color(0xFF4CFFA8),
      warning = Color(0xFFFFC857),
      danger = Color(0xFFFF5263),
    )

  val Blade =
    EdexPalette(
      id = EdexThemeId.BLADE,
      background = Color(0xFF090603),
      surface = Color(0xFF130D06),
      surfaceElevated = Color(0xFF1C1308),
      primary = Color(0xFFFFB74D),
      primaryBright = Color(0xFFFF8F00),
      primaryDim = Color(0xFF5C3A0E),
      secondary = Color(0xFFFFCC80),
      border = Color(0x59FFB74D),
      gridLine = Color(0x1FFF8F00),
      textPrimary = Color(0xFFFFEED9),
      textMuted = Color(0xFFA67C4E),
      success = Color(0xFF81C784),
      warning = Color(0xFFFFD54F),
      danger = Color(0xFFFF5252),
    )

  val Apollo =
    EdexPalette(
      id = EdexThemeId.APOLLO,
      background = Color(0xFF06090F),
      surface = Color(0xFF0C121D),
      surfaceElevated = Color(0xFF121C2C),
      primary = Color(0xFFE3EEFF),
      primaryBright = Color(0xFF82B1FF),
      primaryDim = Color(0xFF334A6B),
      secondary = Color(0xFFB3D1FF),
      border = Color(0x59E3EEFF),
      gridLine = Color(0x1F82B1FF),
      textPrimary = Color(0xFFF3F7FF),
      textMuted = Color(0xFF7D93B2),
      success = Color(0xFF69F0AE),
      warning = Color(0xFFFFD740),
      danger = Color(0xFFFF5252),
    )

  val Matrix =
    EdexPalette(
      id = EdexThemeId.MATRIX,
      background = Color(0xFF030A05),
      surface = Color(0xFF06130A),
      surfaceElevated = Color(0xFF0A1E10),
      primary = Color(0xFF72FF9D),
      primaryBright = Color(0xFF00E676),
      primaryDim = Color(0xFF174E28),
      secondary = Color(0xFF69F0AE),
      border = Color(0x5972FF9D),
      gridLine = Color(0x1F00E676),
      textPrimary = Color(0xFFDCFFE6),
      textMuted = Color(0xFF5A9A6E),
      success = Color(0xFF00E676),
      warning = Color(0xFFFFEB3B),
      danger = Color(0xFFFF5252),
    )

  val Chalk =
    EdexPalette(
      id = EdexThemeId.CHALK,
      background = Color(0xFF0A0406),
      surface = Color(0xFF15080B),
      surfaceElevated = Color(0xFF210D12),
      primary = Color(0xFFFF7A8A),
      primaryBright = Color(0xFFFF1744),
      primaryDim = Color(0xFF5E1B28),
      secondary = Color(0xFFFF9EAB),
      border = Color(0x59FF7A8A),
      gridLine = Color(0x1FFF1744),
      textPrimary = Color(0xFFFFECEE),
      textMuted = Color(0xFFA86A73),
      success = Color(0xFF69F0AE),
      warning = Color(0xFFFFCA28),
      danger = Color(0xFFFF1744),
    )

  val CyberpunkNeon =
    EdexPalette(
      id = EdexThemeId.TRON,
      background = Color(0xFF090414),
      surface = Color(0xFF120824),
      surfaceElevated = Color(0xFF1B0D36),
      primary = Color(0xFFFF4FD8),
      primaryBright = Color(0xFF00F5FF),
      primaryDim = Color(0xFF4D165E),
      secondary = Color(0xFFFF2A85),
      border = Color(0x66FF2A85),
      gridLine = Color(0x2600F5FF),
      textPrimary = Color(0xFFF7EEFF),
      textMuted = Color(0xFFA384C9),
      success = Color(0xFF39FF14),
      warning = Color(0xFFFFE600),
      danger = Color(0xFFFF1E56),
    )

  val MonochromeTerminal =
    EdexPalette(
      id = EdexThemeId.APOLLO,
      background = Color(0xFF050505),
      surface = Color(0xFF0D0D0D),
      surfaceElevated = Color(0xFF161616),
      primary = Color(0xFFCFCFCF),
      primaryBright = Color(0xFFFFFFFF),
      primaryDim = Color(0xFF2E2E2E),
      secondary = Color(0xFF9E9E9E),
      border = Color(0x59D9D9D9),
      gridLine = Color(0x24FFFFFF),
      textPrimary = Color(0xFFF2F2F2),
      textMuted = Color(0xFF8A8A8A),
      success = Color(0xFFE0E0E0),
      warning = Color(0xFFB8B8B8),
      danger = Color(0xFFFFFFFF),
    )

  val MilitaryHud =
    EdexPalette(
      id = EdexThemeId.MATRIX,
      background = Color(0xFF070B05),
      surface = Color(0xFF0D1409),
      surfaceElevated = Color(0xFF141F0E),
      primary = Color(0xFFA3D95B),
      primaryBright = Color(0xFF8CFF2E),
      primaryDim = Color(0xFF2A4018),
      secondary = Color(0xFFD4B23D),
      border = Color(0x598CFF2E),
      gridLine = Color(0x218CFF2E),
      textPrimary = Color(0xFFE6F2D5),
      textMuted = Color(0xFF7B9460),
      success = Color(0xFF8CFF2E),
      warning = Color(0xFFE6B800),
      danger = Color(0xFFFF5722),
    )

  fun byId(id: EdexThemeId): EdexPalette =
    when (id) {
      EdexThemeId.TRON -> Tron
      EdexThemeId.BLADE -> Blade
      EdexThemeId.APOLLO -> Apollo
      EdexThemeId.MATRIX -> Matrix
      EdexThemeId.CHALK -> Chalk
    }

  fun forTelemetryTheme(
    telemetryThemeId: TelemetryThemeId,
    globalThemeId: EdexThemeId,
  ): EdexPalette =
    when (telemetryThemeId) {
      TelemetryThemeId.SYNC_HUD -> byId(globalThemeId)
      TelemetryThemeId.CYBERPUNK_NEON -> CyberpunkNeon
      TelemetryThemeId.MONOCHROME_TERMINAL -> MonochromeTerminal
      TelemetryThemeId.MILITARY_HUD -> MilitaryHud
    }
}
