package com.kaan.play.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.theme.PlayTheme
import com.kaan.play.ui.tv.FocusMemory
import com.kaan.play.ui.tv.focusMemory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Kumanda: yön tuşu akışları ve odak hafızası (gerçek Compose, cihazda). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TvFocusTest {

    @get:Rule
    val rule = createComposeRule()

    /** Gerçek kumanda gibi: bir yön/Tamam tuşu pencereyi dokunmatik kipten çıkarır; ancak o zaman tıklanabilirler odak alır. */
    private fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.waitForIdle()
    }

    @Test
    fun dpadMovesFocusBetweenPosterCardsAndBack() {
        val first = FocusRequester()
        rule.setContent {
            PlayTheme {
                Row {
                    PosterCard("A", null, null, onClick = {}, modifier = Modifier.width(100.dp).focusRequester(first).testTag("a"))
                    PosterCard("B", null, null, onClick = {}, modifier = Modifier.width(100.dp).testTag("b"))
                }
            }
        }
        leaveTouchMode()
        rule.runOnIdle { first.requestFocus() }
        rule.onNodeWithTag("a").assertIsFocused()

        rule.onNodeWithTag("a").performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag("b").assertIsFocused()

        rule.onNodeWithTag("b").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithTag("a").assertIsFocused()
    }

    @Test
    fun enterOnTheFocusedCardActivatesIt() {
        var clicked = 0
        val focus = FocusRequester()
        rule.setContent {
            PlayTheme {
                PosterCard("A", null, null, onClick = { clicked++ }, modifier = Modifier.width(100.dp).focusRequester(focus).testTag("a"))
            }
        }
        leaveTouchMode()
        rule.runOnIdle { focus.requestFocus() }
        rule.onNodeWithTag("a").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assert(clicked == 1) { "tıklama sayısı $clicked" } }
    }

    @Test
    fun dpadDownMovesFocusThroughAColumnOfButtons() {
        val top = FocusRequester()
        rule.setContent {
            PlayTheme {
                Column {
                    Button(onClick = {}, modifier = Modifier.focusRequester(top).testTag("one")) { Text("1") }
                    Button(onClick = {}, modifier = Modifier.testTag("two")) { Text("2") }
                }
            }
        }
        leaveTouchMode()
        rule.runOnIdle { top.requestFocus() }
        rule.onNodeWithTag("one").performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("two").assertIsFocused()
    }

    @Test
    fun focusMemoryReturnsFocusToTheLastItemWhenTheScreenComesBack() {
        // "b" önceki ziyarette odaklanmıştı: geri dönüşte (restoreKey = "b") odak ona gider, ilk öğeye değil.
        val memory = FocusMemory(restoreKey = "b")
        val go = mutableStateOf(false)
        rule.setContent {
            PlayTheme {
                LaunchedEffect(go.value) { if (go.value) memory.requestInitial() }
                Row {
                    Button(onClick = {}, modifier = Modifier.focusMemory(memory, "a", isDefault = true).testTag("a")) { Text("a") }
                    Button(onClick = {}, modifier = Modifier.focusMemory(memory, "b").testTag("b")) { Text("b") }
                }
            }
        }
        leaveTouchMode()
        go.value = true
        rule.waitForIdle()
        rule.onNodeWithTag("b").assertIsFocused()
    }

    @Test
    fun firstOpeningFocusesTheDefaultItem() {
        val memory = remember0()
        val go = mutableStateOf(false)
        rule.setContent {
            PlayTheme {
                LaunchedEffect(go.value) { if (go.value) memory.requestInitial() }
                Row {
                    Button(onClick = {}, modifier = Modifier.focusMemory(memory, "a", isDefault = true).testTag("a")) { Text("a") }
                    Button(onClick = {}, modifier = Modifier.focusMemory(memory, "b").testTag("b")) { Text("b") }
                }
            }
        }
        leaveTouchMode()
        go.value = true
        rule.waitForIdle()
        rule.onNodeWithTag("a").assertIsFocused()
    }

    @Test
    fun focusMemorySurvivesSaveAndRestore() {
        val memory = FocusMemory()
        memory.lastKey = "c"
        val saved = with(FocusMemory.Saver) { SaverScope { true }.save(memory) }
        val restored = FocusMemory.Saver.restore(saved!!)
        assertEquals("c", restored!!.restoreKey)
        // hiç odaklanılmadıysa geri yüklemede anahtar yok: ilk açılış gibi davranılır
        val empty = with(FocusMemory.Saver) { SaverScope { true }.save(FocusMemory()) }
        assertEquals(null, FocusMemory.Saver.restore(empty!!)!!.restoreKey)
    }

    private fun remember0() = FocusMemory()
}
