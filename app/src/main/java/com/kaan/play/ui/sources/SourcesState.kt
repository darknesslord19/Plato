package com.kaan.play.ui.sources

import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.ranked
import com.kaan.play.data.stream.recommended

/** Kaynak seçim listesinin durumu; addon cevapları geldikçe [plus] ile büyür. */
data class SourcesState(
    /** Sıralı (yüksek kalite önce). */
    val streams: List<PlayableStream> = emptyList(),
    /** "Yanıt vermedi" bilgisi taşıyan addon'lar. */
    val failed: List<StreamSource> = emptyList(),
    val loading: Boolean = true,
) {
    val recommended: PlayableStream? get() = streams.recommended()

    operator fun plus(result: StreamResult): SourcesState = when (result) {
        is StreamResult.Found -> copy(streams = (streams + result.streams).ranked())
        is StreamResult.Failed -> copy(failed = failed + result.source)
    }

    fun finished() = copy(loading = false)
}
