package com.kaan.play.ui.theme

import androidx.annotation.StringRes
import com.kaan.play.R

/**
 * Köşe yapısı: her tema kart, düğme ve afişlerde kendi biçimini taşır.
 * Yuvarlak 14 dp (Play'in kendisi), Yumuşak 22 dp, Keskin 4 dp.
 */
enum class PaletteShape(val small: Int, val medium: Int, val large: Int) {
    Rounded(10, 14, 20),
    Soft(16, 22, 28),
    Sharp(3, 4, 6),
}

/**
 * Seçilebilir tema: baskın zemin rengi [background], vurgu [accent] ve köşe yapısı [shape].
 * Tonlar her parlaklık için [PaletteGen] ile üretilir. İlki ("play") uygulamanın kendi kimliğidir ve Koyu/Açık modda
 * bugüne kadarki renklerin aynısını verir.
 */
data class PlayPalette(
    val key: String,
    val background: Int,
    val accent: Int,
    val shape: PaletteShape,
    @StringRes val nameRes: Int,
)

object Palettes {
    const val DEFAULT = "play"

    private fun p(key: String, background: Long, accent: Long, shape: PaletteShape, @StringRes name: Int) =
        PlayPalette(key, background.toInt(), accent.toInt(), shape, name)

    val ALL: List<PlayPalette> = listOf(
        p("play", 0xFF07111A, 0xFF00E5FF, PaletteShape.Rounded, R.string.palette_play),
        p("komur", 0xFF2B2B2B, 0xFFFFB300, PaletteShape.Sharp, R.string.palette_komur),
        p("deve_tuyu", 0xFF3B2A1A, 0xFFE8C39E, PaletteShape.Soft, R.string.palette_deve_tuyu),
        p("grafit", 0xFF23272E, 0xFF8AB4F8, PaletteShape.Sharp, R.string.palette_grafit),
        p("gumus", 0xFF2B2B2E, 0xFFE3E6EA, PaletteShape.Rounded, R.string.palette_gumus),
        p("arduvaz", 0xFF1E2A36, 0xFF7FB3D5, PaletteShape.Rounded, R.string.palette_arduvaz),
        p("kum", 0xFF3A3326, 0xFFE6CF8B, PaletteShape.Soft, R.string.palette_kum),
        p("bej", 0xFF2F2A24, 0xFFF2DFC2, PaletteShape.Soft, R.string.palette_bej),
        p("kahve", 0xFF2A1B12, 0xFFC8A27A, PaletteShape.Rounded, R.string.palette_kahve),
        p("cikolata", 0xFF24130A, 0xFFFF9E57, PaletteShape.Soft, R.string.palette_cikolata),
        p("kiremit", 0xFF2E1410, 0xFFFF8A65, PaletteShape.Sharp, R.string.palette_kiremit),
        p("mercan", 0xFF2A1418, 0xFFFF7F6E, PaletteShape.Soft, R.string.palette_mercan),
        p("turuncu", 0xFF1A120A, 0xFFFF8C1A, PaletteShape.Sharp, R.string.palette_turuncu),
        p("kehribar", 0xFF1E1608, 0xFFFFC107, PaletteShape.Rounded, R.string.palette_kehribar),
        p("hardal", 0xFF1F1C0A, 0xFFE1B12C, PaletteShape.Sharp, R.string.palette_hardal),
        p("altin", 0xFF14110A, 0xFFF5C542, PaletteShape.Soft, R.string.palette_altin),
        p("limon", 0xFF12160A, 0xFFE4F04A, PaletteShape.Sharp, R.string.palette_limon),
        p("zeytin", 0xFF1C1F10, 0xFFB5C26A, PaletteShape.Rounded, R.string.palette_zeytin),
        p("cam", 0xFF0F1F18, 0xFF6FCF97, PaletteShape.Rounded, R.string.palette_cam),
        p("orman", 0xFF0E1A0F, 0xFF4CAF50, PaletteShape.Soft, R.string.palette_orman),
        p("nane", 0xFF0E1F1A, 0xFF7DF2C5, PaletteShape.Rounded, R.string.palette_nane),
        p("zumrut", 0xFF0A1E16, 0xFF2EE59D, PaletteShape.Sharp, R.string.palette_zumrut),
        p("yesim", 0xFF0B1C1A, 0xFF00C9A7, PaletteShape.Soft, R.string.palette_yesim),
        p("deniz_kopugu", 0xFF0C1E22, 0xFF8FF5D8, PaletteShape.Rounded, R.string.palette_deniz_kopugu),
        p("turkuaz", 0xFF062226, 0xFF40E0D0, PaletteShape.Sharp, R.string.palette_turkuaz),
        p("gok", 0xFF0B1828, 0xFF4FB3F6, PaletteShape.Rounded, R.string.palette_gok),
        p("buz", 0xFF0E1D27, 0xFFBDE6FF, PaletteShape.Soft, R.string.palette_buz),
        p("okyanus", 0xFF04162A, 0xFF1E9BFF, PaletteShape.Rounded, R.string.palette_okyanus),
        p("lacivert", 0xFF0A1030, 0xFF6C8CFF, PaletteShape.Sharp, R.string.palette_lacivert),
        p("indigo", 0xFF120F2E, 0xFF8C7CFF, PaletteShape.Soft, R.string.palette_indigo),
        p("lavanta", 0xFF1C1428, 0xFFC9A6FF, PaletteShape.Soft, R.string.palette_lavanta),
        p("leylak", 0xFF22182A, 0xFFE0B7E8, PaletteShape.Rounded, R.string.palette_leylak),
        p("mor", 0xFF1A0D26, 0xFFB266FF, PaletteShape.Sharp, R.string.palette_mor),
        p("erik", 0xFF240F22, 0xFFE07BD0, PaletteShape.Rounded, R.string.palette_erik),
        p("gul", 0xFF2A1020, 0xFFFF6FA8, PaletteShape.Soft, R.string.palette_gul),
        p("pembe", 0xFF2A0F1F, 0xFFFF8ACB, PaletteShape.Rounded, R.string.palette_pembe),
        p("kirmizi", 0xFF1C0A0A, 0xFFFF4D4D, PaletteShape.Sharp, R.string.palette_kirmizi),
        p("bordo", 0xFF2A0A14, 0xFFFF7A93, PaletteShape.Soft, R.string.palette_bordo),
        p("visne", 0xFF2B060C, 0xFFFF3D6A, PaletteShape.Sharp, R.string.palette_visne),
        p("gece", 0xFF080C1A, 0xFFA0B4FF, PaletteShape.Rounded, R.string.palette_gece),
    )

    private val byKey = ALL.associateBy { it.key }

    fun get(key: String?): PlayPalette = byKey[key] ?: byKey.getValue(DEFAULT)
}
