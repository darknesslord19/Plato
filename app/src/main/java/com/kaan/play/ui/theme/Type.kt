package com.kaan.play.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Arayüz ilkesi 2: uygulamada yalnızca iki yazı boyutu vardır.
 * - Başlık: 20sp SemiBold (ekran/bölüm/film başlığı) -> titleLarge, headline*, display*
 * - Gövde: 14sp; Normal (metin) ya da Medium (satır başlığı, düğme) -> title{Medium,Small}, body*, label*
 */
private val Heading = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
private val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
private val BodyMedium = Body.copy(fontWeight = FontWeight.Medium)

val PlayTypography = Typography(
    displayLarge = Heading, displayMedium = Heading, displaySmall = Heading,
    headlineLarge = Heading, headlineMedium = Heading, headlineSmall = Heading,
    titleLarge = Heading,
    titleMedium = BodyMedium, titleSmall = BodyMedium,
    bodyLarge = Body, bodyMedium = Body, bodySmall = Body,
    labelLarge = BodyMedium, labelMedium = BodyMedium, labelSmall = BodyMedium,
)
