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
private val HeadingLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
private val Heading = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
private val TitleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
private val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
private val BodyMedium = Body.copy(fontWeight = FontWeight.Medium)
private val BodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
private val LabelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
private val LabelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal)

val PlayTypography = Typography(
    displayLarge = HeadingLarge, displayMedium = HeadingLarge, displaySmall = Heading,
    headlineLarge = HeadingLarge, headlineMedium = Heading, headlineSmall = Heading,
    titleLarge = Heading,
    titleMedium = TitleMedium, titleSmall = TitleMedium,
    bodyLarge = Body, bodyMedium = Body, bodySmall = BodySmall,
    labelLarge = TitleMedium, labelMedium = LabelMedium, labelSmall = LabelSmall,
)
