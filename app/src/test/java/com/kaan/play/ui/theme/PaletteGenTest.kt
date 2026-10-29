package com.kaan.play.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteGenTest {

    @Test
    fun fortyDistinctPalettesIncludingCharcoalAndCamel() {
        assertEquals(40, Palettes.ALL.size)
        assertEquals(40, Palettes.ALL.map { it.key }.toSet().size)
        assertTrue(Palettes.ALL.any { it.key == "komur" })
        assertTrue(Palettes.ALL.any { it.key == "deve_tuyu" })
        assertEquals(Palettes.DEFAULT, Palettes.ALL.first().key)
        assertEquals(Palettes.DEFAULT, Palettes.get("yok-boyle-bir-palet").key)
    }

    @Test
    fun everyPaletteIsReadableInEveryBrightness() {
        Palettes.ALL.forEach { palette ->
            PaletteGen.Brightness.entries.forEach { b ->
                val s = PaletteGen.scheme(palette.seed, b)
                val where = "${palette.key}/$b"
                assertTrue("vurgu/zemin $where", PaletteGen.contrast(s.primary, s.background) >= 4.5)
                assertTrue("vurgu üstü yazı $where", PaletteGen.contrast(s.onPrimary, s.primary) >= 4.5)
                assertTrue("metin/zemin $where", PaletteGen.contrast(s.onBackground, s.background) >= 10.0)
                assertTrue("soluk metin $where", PaletteGen.contrast(s.onSurfaceVariant, s.surfaceVariant) >= 4.5)
            }
        }
    }

    @Test
    fun brightnessLevelsOrderBackgrounds() {
        val seed = Palettes.get("deve_tuyu").seed
        fun l(b: PaletteGen.Brightness) = PaletteGen.toLch(PaletteGen.scheme(seed, b).background).first
        assertEquals(0xFF000000.toInt(), PaletteGen.scheme(seed, PaletteGen.Brightness.Black).background)
        assertTrue(l(PaletteGen.Brightness.Black) < l(PaletteGen.Brightness.Dark))
        assertTrue(l(PaletteGen.Brightness.Dark) < l(PaletteGen.Brightness.Medium))
        assertTrue(l(PaletteGen.Brightness.Medium) < l(PaletteGen.Brightness.Light))
    }

    @Test
    fun lchRoundTripKeepsColor() {
        listOf(0xFF00E5FF, 0xFF36454F, 0xFFC19A6B, 0xFF800020).forEach { argb ->
            val (l, c, h) = PaletteGen.toLch(argb.toInt())
            val back = PaletteGen.fromLch(l, c, h)
            listOf(16, 8, 0).forEach { shift ->
                val d = kotlin.math.abs(((back shr shift) and 0xFF) - ((argb.toInt() shr shift) and 0xFF))
                assertTrue("kanal farkı $d", d <= 2)
            }
        }
    }
}
