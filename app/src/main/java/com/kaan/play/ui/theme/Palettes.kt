package com.kaan.play.ui.theme

import androidx.annotation.StringRes
import com.kaan.play.R

/**
 * Seçilebilir renk paletleri: her biri tek bir tohum renktir; tonlar [PaletteGen] ile (Monet tarzı) üretilir.
 * İlki ("play") uygulamanın kendi kimliğidir ve Koyu/Açık modda bugüne kadarki renklerin aynısını verir.
 */
data class PlayPalette(val key: String, val seed: Int, @StringRes val nameRes: Int)

object Palettes {
    const val DEFAULT = "play"

    private fun p(key: String, seed: Long, @StringRes name: Int) = PlayPalette(key, seed.toInt(), name)

    val ALL: List<PlayPalette> = listOf(
        p("play", 0xFF00E5FF, R.string.palette_play),
        p("komur", 0xFF36454F, R.string.palette_komur),
        p("deve_tuyu", 0xFFC19A6B, R.string.palette_deve_tuyu),
        p("grafit", 0xFF4B4F56, R.string.palette_grafit),
        p("gumus", 0xFFA8A9AD, R.string.palette_gumus),
        p("arduvaz", 0xFF5A6E82, R.string.palette_arduvaz),
        p("kum", 0xFFC2B280, R.string.palette_kum),
        p("bej", 0xFFD8C3A5, R.string.palette_bej),
        p("kahve", 0xFF6F4E37, R.string.palette_kahve),
        p("cikolata", 0xFF7B3F00, R.string.palette_cikolata),
        p("kiremit", 0xFFE2725B, R.string.palette_kiremit),
        p("mercan", 0xFFFF7F50, R.string.palette_mercan),
        p("turuncu", 0xFFFF8C1A, R.string.palette_turuncu),
        p("kehribar", 0xFFFFBF00, R.string.palette_kehribar),
        p("hardal", 0xFFD4A017, R.string.palette_hardal),
        p("altin", 0xFFE6B422, R.string.palette_altin),
        p("limon", 0xFFE8E337, R.string.palette_limon),
        p("zeytin", 0xFF808000, R.string.palette_zeytin),
        p("cam", 0xFF3A6B4E, R.string.palette_cam),
        p("orman", 0xFF228B22, R.string.palette_orman),
        p("nane", 0xFF3EB489, R.string.palette_nane),
        p("zumrut", 0xFF50C878, R.string.palette_zumrut),
        p("yesim", 0xFF00A86B, R.string.palette_yesim),
        p("deniz_kopugu", 0xFF71EEB8, R.string.palette_deniz_kopugu),
        p("turkuaz", 0xFF40E0D0, R.string.palette_turkuaz),
        p("gok", 0xFF4FB3F6, R.string.palette_gok),
        p("buz", 0xFFA5D8FF, R.string.palette_buz),
        p("okyanus", 0xFF0077BE, R.string.palette_okyanus),
        p("lacivert", 0xFF1F3A93, R.string.palette_lacivert),
        p("indigo", 0xFF5C6BC0, R.string.palette_indigo),
        p("lavanta", 0xFFB57EDC, R.string.palette_lavanta),
        p("leylak", 0xFFC8A2C8, R.string.palette_leylak),
        p("mor", 0xFF8E44AD, R.string.palette_mor),
        p("erik", 0xFF8E4585, R.string.palette_erik),
        p("gul", 0xFFE75480, R.string.palette_gul),
        p("pembe", 0xFFFF69B4, R.string.palette_pembe),
        p("kirmizi", 0xFFE53935, R.string.palette_kirmizi),
        p("bordo", 0xFF800020, R.string.palette_bordo),
        p("visne", 0xFFB7102E, R.string.palette_visne),
        p("gece", 0xFF2C3E70, R.string.palette_gece),
    )

    private val byKey = ALL.associateBy { it.key }

    fun get(key: String?): PlayPalette = byKey[key] ?: byKey.getValue(DEFAULT)
}
