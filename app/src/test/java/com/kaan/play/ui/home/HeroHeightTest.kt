package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroHeightTest {

    @Test
    fun portraitPhoneHeroIsTallButLeavesRoomForRows() {
        // 360x640dp (720x1280 @ 320dpi): genişliğe yakın, ekranın %62'sini aşmaz.
        val h = heroHeightFor(360, 640).value
        assertEquals(396f, h, 1f)
        assertTrue(h <= 640 * 0.62f + 0.5f)
    }

    @Test
    fun lowLandscapeScreenKeepsMinimumForLogoLineAndButton() {
        assertEquals(260f, heroHeightFor(800, 360).value, 0.1f) // yatay telefon
    }

    @Test
    fun tabletHeroIsCapped() {
        val h = heroHeightFor(1280, 800).value // 1280x800 tablet
        assertEquals(800 * 0.62f, h, 1f)
        assertTrue(heroHeightFor(2560, 1600).value <= 520f)
    }
}
