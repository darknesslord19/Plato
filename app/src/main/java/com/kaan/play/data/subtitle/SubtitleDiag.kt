package com.kaan.play.data.subtitle

import android.util.Log
import com.kaan.play.BuildConfig
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Altyazı teşhis logu (etiket `PlaySubs`). **Yalnızca debug derlemede** yazar. Altyazı adresi ve yerel dosya yolu loglanmaz:
 * yalnızca kaynak adı, dil, sayı ve sonuç.
 */
object SubtitleDiag {
    const val TAG = "PlaySubs"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    /** Sağlayıcının cevaplarını (kaynak başına dil dağılımı) loglayan sarmalayıcı. */
    fun provider(inner: SubtitleProvider): SubtitleProvider = object : SubtitleProvider {
        override fun subtitles(request: StreamRequest): Flow<SubtitleResult> = inner.subtitles(request).onEach { result ->
            when (result) {
                is SubtitleResult.Found -> d(
                    "kaynak=${result.source.addonName} içerik=${request.contentId ?: "imdb yok (tmdb ${request.tmdbId})"} " +
                        "sonuç=${result.tracks.size} diller=${result.tracks.groupingBy { it.language }.eachCount()}",
                )
                is SubtitleResult.Failed -> d("kaynak=${result.source.addonName} yanıt vermedi: ${result.reason}")
            }
        }

        override suspend fun sources(request: StreamRequest) = inner.sources(request)
    }

    /** Yükleme sonucunu (tür, dil, satır sayısı, kodlama) loglayan sarmalayıcı. */
    fun loader(inner: TrackLoader): TrackLoader = TrackLoader { track ->
        inner.load(track).also { result ->
            val what = "tür=${track.kind} kaynak=${track.origin.takeIf { track.kind != SubtitleKind.Local } ?: "dosya"} dil=${track.language}"
            when (result) {
                is SubtitleLoad.Loaded -> d("yüklendi $what satır=${result.cues.size} kodlama=${result.charset} önbellek=${result.fromCache}")
                is SubtitleLoad.Failed -> d("yüklenemedi $what neden=${result.reason}")
            }
        }
    }
}
