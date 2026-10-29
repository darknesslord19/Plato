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
 * İki tohum renkten (baskın zemin + vurgu) tonal renk şeması üretir. Compose'dan bağımsızdır (ARGB Int), bu yüzden
 * birim testlidir. Renkler CIELAB/LCh uzayında üretilir: ton sabit, istenen açıklık (L*), gamut dışına çıkarsa yalnızca
 * doygunluk düşer. Zemin, yüzeyler ve çerçeveler zemin tohumunun renginde (her tema kendi renginde görünür), seçili
 * dolgular ve düğmeler vurgunun renginde.
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

    /**
     * İki tohumdan tema: [background] temanın baskın zemin rengi (ton ve doygunluğu zemine, yüzeylere ve çerçevelere
     * taşınır; böylece her tema kendi renginde görünür), [accent] vurgu (düğme, seçili öğe, odak halkası).
     * Vurgu, zemin üzerinde en az 4.6:1 karşıtlık kalana kadar açılır (koyu) ya da koyulaşır (açık); ton korunur.
     */
    fun scheme(background: Int, accent: Int, brightness: Brightness): SchemeColors {
        val (_, bgSeedC, bgHue) = toLch(background)
        val (accL, accC, accHue) = toLch(accent)
        fun bgc(l: Double, chroma: Double) = fromLch(l, chroma, bgHue)
        fun acc(l: Double, chroma: Double) = fromLch(l, chroma, accHue)
        // Neredeyse gri tohum (kömür, gümüş) gri kalır; renkli tohumun doygunluğu korunur.
        val neutralBg = bgSeedC < 4.0

        if (brightness == Brightness.Light) {
            val c = if (neutralBg) bgSeedC else (bgSeedC * 0.55).coerceIn(4.0, 14.0)
            val bg = bgc(95.0, c)
            val pc = max(accC, 30.0)
            var pl = min(accL, 45.0)
            var primary = acc(pl, pc)
            while (contrast(primary, bg) < MIN_ACCENT && pl > 15.0) {
                pl -= 2.0
                primary = acc(pl, pc)
            }
            return SchemeColors(
                primary = primary,
                onPrimary = 0xFFFFFFFF.toInt(),
                primaryContainer = acc(88.0, min(accC, 30.0)),
                onPrimaryContainer = acc(min(pl, 30.0), pc),
                background = bg,
                onBackground = bgc(12.0, min(c, 10.0)),
                surface = bgc(98.0, c * 0.5),
                surfaceVariant = bgc(89.0, c + 4),
                onSurfaceVariant = bgc(36.0, c + 4),
                surfaceContainer = acc(88.0, min(accC, 30.0)),
                surfaceContainerHigh = bgc(89.0, c + 4),
                outline = bgc(74.0, c + 6),
            )
        }

        // Zemin açıklığı (L*): orta karanlık 20, koyu 9, simsiyah 0 (yüzeyler yine temanın renginde).
        val l = when (brightness) {
            Brightness.Medium -> 20.0
            Brightness.Dark -> 9.0
            else -> 0.0
        }
        val k = when (brightness) {
            Brightness.Medium -> 1.1
            Brightness.Dark -> 1.0
            else -> 0.9
        }
        val c = if (neutralBg) bgSeedC else (bgSeedC * k).coerceIn(3.0, 30.0)
        val bg = if (brightness == Brightness.Black) 0xFF000000.toInt() else bgc(l, c)
        val base = if (brightness == Brightness.Black) 3.0 else l
        var pl = if (accC > 12.0) max(accL, 70.0) else max(accL, 78.0)
        var primary = acc(pl, accC)
        while (contrast(primary, bg) < MIN_ACCENT && pl < 95.0) {
            pl += 2.0
            primary = acc(pl, accC)
        }
        val onPrimary = if (pl >= 60.0) acc(12.0, min(accC, 20.0)) else 0xFFFFFFFF.toInt()
        return SchemeColors(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = acc(base + 12, min(accC * 0.5, 30.0)),
            onPrimaryContainer = primary,
            background = bg,
            onBackground = bgc(97.0, min(c, 4.0)),
            surface = bgc(base + 4, c),
            surfaceVariant = bgc(base + 9, c + 2),
            onSurfaceVariant = bgc(74.0, min(c, 10.0) + 4),
            surfaceContainer = acc(base + 12, min(accC * 0.5, 30.0)),
            surfaceContainerHigh = bgc(base + 9, c + 2),
            outline = bgc(base + 22, c + 4),
        )
    }

    /** Vurgunun zemine karşı en düşük karşıtlığı. */
    private const val MIN_ACCENT = 4.6

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
