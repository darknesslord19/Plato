package com.kaan.play.data.settings

/** Altyazı boyutu: [fraction], oynatıcı yüksekliğine oranıdır (Media3 SubtitleView "fractional text size"). */
enum class SubtitleSize(val fraction: Float) {
    Small(0.04f),
    Normal(0.0533f),
    Large(0.07f),
    ExtraLarge(0.09f),
}

enum class SubtitleColor { White, Yellow }

enum class SubtitleBackground { None, Translucent, Black }

/** Kullanıcının altyazı görünümü. Varsayılan: normal boyut, beyaz, yarı saydam arka plan. */
data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.Normal,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val color: SubtitleColor = SubtitleColor.White,
    val background: SubtitleBackground = SubtitleBackground.Translucent,
)
