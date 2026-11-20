package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val OrbitronFontFamily = FontFamily(Font(R.font.orbitron, FontWeight.Normal))

val RajdhaniFontFamily = FontFamily(Font(R.font.rajdhani, FontWeight.Normal))

val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

val EdexTypography =
  Typography(
    displayLarge =
      TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 2.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.5.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.2.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 1.0.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.1.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.6.sp,
      ),
  )
