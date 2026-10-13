package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelRulesTest {

    @Test
    fun rightHalfIsVolumeLeftHalfIsBrightness() {
        assertEquals(LevelKind.Volume, LevelRules.kindAt(1200f, 1600f))
        assertEquals(LevelKind.Volume, LevelRules.kindAt(800f, 1600f))
        assertEquals(LevelKind.Brightness, LevelRules.kindAt(799f, 1600f))
        assertEquals(LevelKind.Brightness, LevelRules.kindAt(0f, 1600f))
    }

    @Test
    fun swipingUpRaisesAndDownLowers() {
        val height = 1000f
        val full = height * LevelRules.RANGE_FRACTION
        assertEquals(0.5f, LevelRules.move(0.25f, full * 0.25f, height), 1e-6f)
        assertEquals(0.25f, LevelRules.move(0.5f, -full * 0.25f, height), 1e-6f)
    }

    @Test
    fun smallMovementMakesSmallChange() {
        // 10 piksel, 1000 piksel yüksekliğinde ekranda %2'den az.
        assertEquals(0.5f, LevelRules.move(0.5f, 10f, 1000f), 0.02f)
    }

    @Test
    fun levelStaysInRangeAndHasNoDeadZoneOnReversal() {
        var level = LevelRules.move(0.9f, 5000f, 1000f)
        assertEquals(1f, level, 0f)
        level = LevelRules.move(level, -1f, 1000f)
        assertEquals(true, level < 1f) // sınırı aşan kaydırma geri dönüşte birikmez
        assertEquals(0f, LevelRules.move(0.1f, -5000f, 1000f), 0f)
    }

    @Test
    fun zeroHeightKeepsLevel() {
        assertEquals(0.4f, LevelRules.move(0.4f, 100f, 0f), 0f)
    }

    @Test
    fun volumeIsWholeSteps() {
        assertEquals(0, LevelRules.volumeStep(0f, 15))
        assertEquals(15, LevelRules.volumeStep(1f, 15))
        assertEquals(8, LevelRules.volumeStep(0.5f, 15))
        assertEquals(0, LevelRules.volumeStep(0.5f, 0))
    }

    @Test
    fun percentRounds() {
        assertEquals(0, LevelRules.percent(0f))
        assertEquals(67, LevelRules.percent(0.666f))
        assertEquals(100, LevelRules.percent(1.4f))
    }
}
