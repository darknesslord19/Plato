package com.kaan.play.ui.sources

import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesStateTest {

    private val a = StreamSource(1, "A")
    private val b = StreamSource(2, "B")
    private val c = StreamSource(3, "C")

    private fun stream(source: StreamSource, res: Int, size: Long = 1) =
        PlayableStream(source, "https://x/${source.addonId}/$res", res, size, emptyMap(), emptyList())

    @Test
    fun pendingAddonsShrinkAsAnswersArrive() {
        var state = SourcesState(asked = listOf(a, b, c))
        assertEquals(listOf(a, b, c), state.pending)

        state += StreamResult.Found(b, listOf(stream(b, 1080)))
        assertEquals(listOf(a, c), state.pending)

        state += StreamResult.Failed(a, FailureReason.Timeout)
        assertEquals(listOf(c), state.pending)

        state += StreamResult.Found(c, emptyList()) // boş cevap da cevaptır
        assertTrue(state.pending.isEmpty())
    }

    @Test
    fun finishedClearsPendingEvenIfSomeNeverAnswered() {
        val state = SourcesState(asked = listOf(a, b)).finished()
        assertTrue(state.pending.isEmpty())
        assertEquals(false, state.loading)
    }

    @Test
    fun streamsAreGroupedPerAddonAndRankedWithinIt() {
        var state = SourcesState(asked = listOf(a, b))
        state += StreamResult.Found(b, listOf(stream(b, 720), stream(b, 2160)))
        state += StreamResult.Found(a, listOf(stream(a, 1080)))

        assertEquals(listOf(2160, 720), state.streamsOf(b.addonId).map { it.resolution })
        assertEquals(listOf(1080), state.streamsOf(a.addonId).map { it.resolution })
        // Bölüm sırası sorulan sıradır (cevap sırası değil).
        assertEquals(listOf(a, b), state.addons)
        // En uygun kaynak tüm addon'lar arasından: 1080p'ye kadar olanın en iyisi.
        assertEquals(1080, state.recommended?.resolution)
    }

    @Test
    fun addonsFallBackToAnswerOrderWhenAskedListIsUnknown() {
        var state = SourcesState()
        state += StreamResult.Found(b, listOf(stream(b, 1080)))
        state += StreamResult.Failed(c, FailureReason.Http)
        assertEquals(listOf(b, c), state.addons)
        assertTrue(state.pending.isEmpty())
    }
}
