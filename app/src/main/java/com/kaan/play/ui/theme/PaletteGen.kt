package com.kaan.play.ui.theme

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tek bir "tohum" renkten Material You (Monet) tarzı tonal renk şeması üretir. Compose'dan bağımsızdır (ARGB Int),
 * bu yüzden birim testlidir. Renkler CIELAB/LCh uzayında üretilir: aynı ton (hue), istenen açıklık (L*) ve ekranın
 * gösterebildiği en yüksek doygunluk (gamut dışına çıkarsa doygunluk düşürülür). Yüzeyler tohumun tonuyla hafifçe
 * renklendirilir (Monet'deki gibi), vurgu doygun kalır.
 */
object PaletteGen {

    /** Arka plan parlaklığı. */
    enum class Brightness { Light, Medium, Dark, Black }

    data class SchemeColors(
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val background: Int,
        val onBackground: Int,
        val surface: Int,
        val surfaceVariant: Int,
        val onSurfaceVariant: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val outline: Int,
    )

    fun scheme(seed: Int, brightness: Brightness): SchemeColors {
        val (_, seedC, hue) = toLch(seed)
        val neutralSeed = seedC < 14.0
        // Nötr tohumlar (kömür, grafit, gümüş) gri kalır; renkli tohumlar doygun bir vurgu alır.
        val accentC = if (neutralSeed) seedC.coerceAtMost(10.0) else max(seedC, 42.0)
        val tint = if (neutralSeed) seedC.coerceAtMost(4.0) else min(seedC * 0.16, 9.0)

        fun c(l: Double, chroma: Double) = fromLch(l, chroma, hue)

        return when (brightness) {
            Brightness.Light -> SchemeColors(
                primary = c(40.0, accentC),
                onPrimary = 0xFFFFFFFF.toInt(),
                primaryContainer = c(90.0, min(accentC, 22.0)),
                onPrimaryContainer = c(24.0, accentC),
                background = c(96.0, tint * 0.7),
                onBackground = c(10.0, tint),
                surface = c(99.0, tint * 0.4),
                surfaceVariant = c(91.0, tint + 2),
                onSurfaceVariant = c(40.0, tint + 4),
                surfaceContainer = c(90.0, min(accentC, 18.0)),
                surfaceContainerHigh = c(91.0, tint + 2),
                outline = c(76.0, tint + 4),
            )
            else -> {
                // Arka plan açıklığı (L*): simsiyah 0, koyu 5, orta 15.
                val bg = when (brightness) {
                    Brightness.Black -> 0.0
                    Brightness.Dark -> 5.0
                    else -> 15.0
                }
                val step = if (brightness == Brightness.Medium) 4.0 else 3.5
                val primaryL = if (neutralSeed) 82.0 else toLch(seed).first.coerceIn(72.0, 84.0)
                SchemeColors(
                    primary = c(primaryL, accentC),
                    onPrimary = c(12.0, min(accentC, 28.0)),
                    primaryContainer = c(bg + 3 * step, min(accentC * 0.55, 26.0)),
                    onPrimaryContainer = c(primaryL, accentC),
                    background = if (brightness == Brightness.Black) 0xFF000000.toInt() else c(bg, tint),
                    onBackground = c(98.0, min(tint, 2.0)),
                    surface = c(bg + step, tint),
                    surfaceVariant = c(bg + 2 * step, tint + 2),
                    onSurfaceVariant = c(70.0, tint + 5),
                    surfaceContainer = c(bg + 3 * step, min(accentC * 0.55, 26.0)),
                    surfaceContainerHigh = c(bg + 2 * step, tint + 2),
                    outline = c(bg + 6 * step, tint + 8),
                )
            }
        }
    }

    // ── Renk uzayı dönüşümleri (sRGB ↔ CIELAB, D65) ─────────────────────────────

    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883

    private fun toLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun fromLinear(v: Double): Double =
        if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055

    private fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
    private fun fInv(t: Double) = if (t * t * t > 216.0 / 24389.0) t * t * t else (116.0 * t - 16.0) / (24389.0 / 27.0)

    /** ARGB → (L*, C*, h°). */
    fun toLch(argb: Int): Triple<Double, Double, Double> {
        val r = toLinear((argb shr 16) and 0xFF)
        val g = toLinear((argb shr 8) and 0xFF)
        val b = toLinear(argb and 0xFF)
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        val fx = f(x / XN)
        val fy = f(y / YN)
        val fz = f(z / ZN)
        val l = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        val h = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + 360.0 else it }
        return Triple(l, hypot(a, bb), h)
    }

    /** Lab → doğrusal sRGB; gamut dışıysa null. */
    private fun labToRgbOrNull(l: Double, a: Double, b: Double): IntArray? {
        val fy = (l + 16.0) / 116.0
        val x = XN * fInv(fy + a / 500.0)
        val y = YN * fInv(fy)
        val z = ZN * fInv(fy - b / 200.0)
        val rl = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val gl = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        val eps = 1e-4
        if (rl < -eps || gl < -eps || bl < -eps || rl > 1 + eps || gl > 1 + eps || bl > 1 + eps) return null
        return doubleArrayOf(rl, gl, bl)
            .map { (fromLinear(it.coerceIn(0.0, 1.0)) * 255.0 + 0.5).toInt().coerceIn(0, 255) }
            .toIntArray()
    }

    /**
     * Açıklık [l] ve ton [hueDeg] sabit kalarak doygunluk ([chroma]) gamut içine sığana kadar düşürülür
     * (Material'in HCT'sine benzer davranış: renk tonu kaymaz, yalnızca soluklaşır).
     */
    fun fromLch(l: Double, chroma: Double, hueDeg: Double): Int {
        val lc = l.coerceIn(0.0, 100.0)
        val h = Math.toRadians(hueDeg)
        fun at(c: Double) = labToRgbOrNull(lc, c * cos(h), c * sin(h))
        var rgb = at(chroma)
        if (rgb == null) {
            var lo = 0.0
            var hi = chroma
            repeat(24) {
                val mid = (lo + hi) / 2
                if (at(mid) != null) lo = mid else hi = mid
            }
            rgb = at(lo) ?: at(0.0)!!
        }
        return (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
    }

    /** WCAG göreli parlaklık karşıtlığı (testler ve okunurluk denetimi için). */
    fun contrast(a: Int, b: Int): Double {
        fun lum(c: Int) = 0.2126 * toLinear((c shr 16) and 0xFF) + 0.7152 * toLinear((c shr 8) and 0xFF) + 0.0722 * toLinear(c and 0xFF)
        val la = lum(a)
        val lb = lum(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
