package com.kaan.play.ui.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import com.kaan.play.data.settings.SubtitleBackground
import com.kaan.play.data.settings.SubtitleColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubtitleStylingTest {

    @Test
    fun colorsAreWhiteOrYellowAndOpaque() {
        assertEquals(Color.WHITE, SubtitleStyling.foreground(SubtitleColor.White))
        val yellow = SubtitleStyling.foreground(SubtitleColor.Yellow)
        assertNotEquals(Color.WHITE, yellow)
        assertEquals(0xFF, yellow ushr 24) // tam opak
    }

    @Test
    fun backgroundsAreTransparentTranslucentOrBlack() {
        assertEquals(Color.TRANSPARENT, SubtitleStyling.background(SubtitleBackground.None))
        assertEquals(Color.BLACK, SubtitleStyling.background(SubtitleBackground.Black))
        val translucent = SubtitleStyling.background(SubtitleBackground.Translucent)
        val alpha = translucent ushr 24
        assert(alpha in 1..254) { "yarı saydam bir alfa bekleniyordu: $alpha" }
        assertEquals(0, translucent and 0x00FFFFFF) // siyah tonu
    }

    @Test
    fun outlineOnlyWhenThereIsNoBackground() {
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, SubtitleStyling.edgeType(SubtitleBackground.None))
        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, SubtitleStyling.edgeType(SubtitleBackground.Translucent))
        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, SubtitleStyling.edgeType(SubtitleBackground.Black))
    }

    @Test
    fun boldAndItalicMapToTypefaceStyles() {
        assertEquals(Typeface.NORMAL, SubtitleStyling.typefaceStyle(bold = false, italic = false))
        assertEquals(Typeface.BOLD, SubtitleStyling.typefaceStyle(bold = true, italic = false))
        assertEquals(Typeface.ITALIC, SubtitleStyling.typefaceStyle(bold = false, italic = true))
        assertEquals(Typeface.BOLD_ITALIC, SubtitleStyling.typefaceStyle(bold = true, italic = true))
    }
}
