package com.kaan.play.data.subtitle

import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.flow.Flow

/**
 * Seçilebilir tek harici altyazı. [language]: [SubtitleLanguages.normalize] ile iki harfli temel dil (bilinmiyorsa null);
 * [origin]: satırda gösterilen kaynak adı (altyazı addon'unun, eklentinin ya da akışı veren addon'un adı).
 * [url] kimliktir: aynı adres tek kez listelenir ve kaydırma değeri adrese bağlı saklanır.
 */
data class SubtitleTrack(val url: String, val language: String?, val origin: String)

/** Bir altyazı addon'unun cevabı: ya parçalar (boş olabilir) ya da "yanıt vermedi". */
sealed interface SubtitleResult {
    val source: StreamSource

    data class Found(override val source: StreamSource, val tracks: List<SubtitleTrack>) : SubtitleResult

    data class Failed(override val source: StreamSource, val reason: FailureReason) : SubtitleResult
}

/** Altyazı toplama arayüzü; [com.kaan.play.data.stream.StreamProvider] ile aynı biçim: sonuçlar kaynak başına geldikçe akar. */
fun interface SubtitleProvider {
    fun subtitles(request: StreamRequest): Flow<SubtitleResult>

    /** Bu istek için sorulacak altyazı addon'ları (cevaplardan önce bilinir). Bilinmiyorsa boş. */
    suspend fun sources(request: StreamRequest): List<StreamSource> = emptyList()
}
