package com.kaan.play.ui.player

import androidx.compose.ui.input.key.Key
import com.kaan.play.ui.player.PlayerKeyAction.OpenMenu
import com.kaan.play.ui.player.PlayerKeyAction.PassThrough
import com.kaan.play.ui.player.PlayerKeyAction.Reveal
import com.kaan.play.ui.player.PlayerKeyAction.SeekBack
import com.kaan.play.ui.player.PlayerKeyAction.SeekForward
import com.kaan.play.ui.player.PlayerKeyAction.TogglePlay
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerKeysTest {

    private fun hidden(key: Key) = PlayerKeys.actionFor(key, controlsVisible = false)
    private fun visible(key: Key) = PlayerKeys.actionFor(key, controlsVisible = true)

    @Test
    fun hiddenControlsMapEveryRemoteKeyToItsJob() {
        assertEquals(TogglePlay, hidden(Key.DirectionCenter))
        assertEquals(TogglePlay, hidden(Key.Enter))
        assertEquals(TogglePlay, hidden(Key.NumPadEnter))
        assertEquals(SeekBack, hidden(Key.DirectionLeft))
        assertEquals(SeekForward, hidden(Key.DirectionRight))
        assertEquals(Reveal, hidden(Key.DirectionUp))
        assertEquals(OpenMenu, hidden(Key.DirectionDown))
        assertEquals(OpenMenu, hidden(Key.Menu))
    }

    @Test
    fun anyOtherKeyBringsTheControlsBack() {
        assertEquals(Reveal, hidden(Key.Tab))
        assertEquals(Reveal, hidden(Key.A))
        assertEquals(Reveal, hidden(Key.Spacebar))
    }

    @Test
    fun mediaKeysAlwaysWork() {
        listOf(false, true).forEach { shown ->
            fun a(k: Key) = PlayerKeys.actionFor(k, shown)
            assertEquals(TogglePlay, a(Key.MediaPlayPause))
            assertEquals(TogglePlay, a(Key.MediaPlay))
            assertEquals(TogglePlay, a(Key.MediaPause))
            assertEquals(SeekForward, a(Key.MediaFastForward))
            assertEquals(SeekForward, a(Key.MediaStepForward))
            assertEquals(SeekBack, a(Key.MediaRewind))
            assertEquals(SeekBack, a(Key.MediaStepBackward))
        }
    }

    @Test
    fun visibleControlsLeaveDirectionsAndOkToTheFocusedButton() {
        listOf(Key.DirectionCenter, Key.Enter, Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
            .forEach { assertEquals(PassThrough, visible(it)) }
    }

    @Test
    fun volumeKeysAreNeverSwallowed() {
        listOf(Key.VolumeUp, Key.VolumeDown, Key.VolumeMute).forEach {
            assertEquals(PassThrough, hidden(it))
            assertEquals(PassThrough, visible(it))
        }
    }

    @Test
    fun holdingSeekAcceleratesInSteps() {
        assertEquals(10_000L, SeekAcceleration.stepMs(0)) // ilk basış
        assertEquals(0L, SeekAcceleration.stepMs(1)) // uzun basışın ilk gecikmesi
        assertEquals(0L, SeekAcceleration.stepMs(2))
        assertEquals(10_000L, SeekAcceleration.stepMs(3)) // ilk tekrar adımı
        assertEquals(0L, SeekAcceleration.stepMs(4))
        assertEquals(10_000L, SeekAcceleration.stepMs(7))
        assertEquals(10_000L, SeekAcceleration.stepMs(19))
        assertEquals(30_000L, SeekAcceleration.stepMs(23))
        assertEquals(30_000L, SeekAcceleration.stepMs(47))
        assertEquals(60_000L, SeekAcceleration.stepMs(51))
        assertEquals(60_000L, SeekAcceleration.stepMs(999 * 4 + 3))
    }

    @Test
    fun accelerationNeverGetsSlowerAndStepsAreWholeTenSeconds() {
        var previous = 0L
        for (repeat in 0..400) {
            val step = SeekAcceleration.stepMs(repeat)
            assertEquals(0L, step % 10_000L)
            if (step > 0) {
                assert(step >= previous) { "tekrar $repeat: $step < $previous" }
                previous = step
            }
        }
    }
}
