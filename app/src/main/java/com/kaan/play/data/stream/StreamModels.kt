package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * Bir içerik için kaynak isteği. Birincil kimlik TMDB id; addon'lar IMDb [imdbId] ister.
 * Dizide [season] ve [episode] zorunludur.
 */
data class StreamRequest(
    val tmdbId: Int,
    val imdbId: String?,
    val type: MediaType,
    val season: Int? = null,
    val episode: Int? = null,
) {
    /**
     * Addon'a giden kimlik: film `tt123`, dizi `tt123:sezon:bölüm`.
     * IMDb kimliği (ya da dizide sezon/bölüm) yoksa null: sorgulanacak bir şey yok.
     */
    val contentId: String?
        get() {
            val imdb = imdbId ?: return null
            return when (type) {
                MediaType.Movie -> imdb
                MediaType.Tv -> if (season != null && episode != null) "$imdb:$season:$episode" else null
            }
        }
}

/** Sonucu veren addon. */
data class StreamSource(val addonId: Long, val addonName: String)

/**
 * Oynatılabilir tek kaynak (yalnızca `url` tipli akışlar buraya girer).
 * [resolution]: dikey çözünürlük (1080, 2160 ...), bilinmiyorsa null. [headers]: addon'un
 * `behaviorHints.proxyHeaders.request` değeri; oynatıcı (Media3 OkHttp datasource) bunları istekle gönderir.
 */
data class PlayableStream(
    val source: StreamSource,
    val url: String,
    val resolution: Int?,
    val sizeBytes: Long?,
    val headers: Map<String, String>,
    val subtitles: List<StreamSubtitle>,
)

/** Akışla gelen altyazı; [language] addon'un verdiği ham etiket (`eng`, `tur` ...). */
data class StreamSubtitle(val url: String, val language: String?)

enum class FailureReason { Timeout, Network, Http, InvalidResponse }

/** Bir addon'un cevabı: ya kaynaklar (boş olabilir) ya da "yanıt vermedi" bilgisi. */
sealed interface StreamResult {
    val source: StreamSource

    data class Found(override val source: StreamSource, val streams: List<PlayableStream>) : StreamResult

    data class Failed(override val source: StreamSource, val reason: FailureReason) : StreamResult
}

/**
 * Kaynak toplama arayüzü. Sonuçlar, kaynak başına geldikçe akar; akış tüm kaynaklar bitince tamamlanır.
 * Faz 1'de tek uygulama [AddonStreamProvider]; sonraki fazların kaynakları da buraya takılır.
 */
fun interface StreamProvider {
    fun streams(request: StreamRequest): Flow<StreamResult>
}
