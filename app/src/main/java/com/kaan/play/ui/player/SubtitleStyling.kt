package com.kaan.play.ui.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.kaan.play.data.settings.SubtitleBackground
import com.kaan.play.data.settings.SubtitleColor
import com.kaan.play.data.settings.SubtitleStyle

/**
 * [SubtitleStyle] -> Media3 [CaptionStyleCompat] / [SubtitleView]. Oynatıcı ve Ayarlar'daki önizleme
 * aynı eşlemeyi kullanır, bu yüzden önizleme gerçek altyazıyla birebir aynı görünür.
 * Sarı yalnızca altyazı metni içindir (arayüzün vurgu rengi turkuaz kalır).
 */
@OptIn(UnstableApi::class)
object SubtitleStyling {

    private const val YELLOW = 0xFFFFEB3B.toInt()
    private const val TRANSLUCENT_BLACK = 0x99000000.toInt()

    fun foreground(color: SubtitleColor): Int = when (color) {
        SubtitleColor.White -> Color.WHITE
        SubtitleColor.Yellow -> YELLOW
    }

    fun background(background: SubtitleBackground): Int = when (background) {
        SubtitleBackground.None -> Color.TRANSPARENT
        SubtitleBackground.Translucent -> TRANSLUCENT_BLACK
        SubtitleBackground.Black -> Color.BLACK
    }

    /** Arka plan yoksa metin okunsun diye siyah kontur çizilir. */
    fun edgeType(background: SubtitleBackground): Int =
        if (background == SubtitleBackground.None) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE

    fun typefaceStyle(bold: Boolean, italic: Boolean): Int = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }

    fun captionStyle(style: SubtitleStyle): CaptionStyleCompat = CaptionStyleCompat(
        foreground(style.color),
        background(style.background),
        Color.TRANSPARENT,
        edgeType(style.background),
        Color.BLACK,
        Typeface.create(Typeface.DEFAULT, typefaceStyle(style.bold, style.italic)),
    )

    /** Oynatma kesilmeden anında uygulanır. [sizeScale]: önizlemede küçük kutuyu gerçek oynatıcıya oranlar. */
    fun apply(view: SubtitleView, style: SubtitleStyle, sizeScale: Float = 1f) {
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.setStyle(captionStyle(style))
        view.setFractionalTextSize(style.size.fraction * sizeScale)
    }
}
