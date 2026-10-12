package com.kaan.play.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaan.play.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** TV banner'ı 16:9 çizilir ve görsel kontrol için bir PNG olarak dışa aktarılabilir. */
@RunWith(AndroidJUnit4::class)
class BannerTest {

    @Test
    fun bannerIsA320x180dpDrawableThatRendersTheMarkOnTheDarkBackground() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.tv_banner, null)
        assertNotNull(drawable)

        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        drawable!!.setBounds(0, 0, 960, 540)
        drawable.draw(Canvas(bitmap))

        // Köşe koyu zemin (#0E1116), işaretin olduğu yerde turkuaz (#2DD4BF) çizilmeli.
        assertEquals(0xFF0E1116.toInt(), bitmap.getPixel(4, 4))
        var accentPixels = 0
        for (x in 0 until 960 step 4) for (y in 0 until 540 step 4) {
            if (bitmap.getPixel(x, y) == 0xFF2DD4BF.toInt()) accentPixels++
        }
        assert(accentPixels > 200) { "turkuaz işaret çizilmedi ($accentPixels)" }

        File(context.cacheDir, "banner.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
