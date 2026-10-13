package com.kaan.play.data.cloudstream

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import kotlinx.coroutines.withTimeout

/**
 * CloudStream eklentisi çalıştırıcısı. Yüklenmiş [MainAPI] örnekleriyle şu akışı izler:
 *  1. [MainAPI.search] ile başlık arar
 *  2. Başlık eşlemesi ([matchResult])
 *  3. [MainAPI.load] ile detay yükler
 *  4. Film: `dataUrl` ile [MainAPI.loadLinks] çağırır
 *     Dizi: hedef sezonu/bölümü [Episode] listesinden bulup [MainAPI.loadLinks] çağırır
 *  5. Gelen [ExtractorLink]'leri [PlayableStream]'e çevirir
 *
 * [timeoutMs] her adım için ayrı uygulanır.
 */
class CsRuntime {

    /**
     * Kayıtlı [MainAPI] örneklerinden oynatılabilir kaynak listesi döner.
     * [title] TMDB'den gelen başlık; CS eklentisi bu başlıkla arama yapar.
     */
    suspend fun streams(
        apis: List<MainAPI>,
        request: StreamRequest,
        title: String,
        pluginRowId: Long,
        pluginName: String,
        timeoutMs: Long = 15_000L,
    ): List<PlayableStream> {
        val source = StreamSource(addonId = pluginRowId, addonName = pluginName)
        val results = mutableListOf<PlayableStream>()

        for (api in apis) {
            try {
                val streams = runForApi(api, request, title, source, timeoutMs)
                results += streams
                if (results.isNotEmpty()) break
            } catch (_: Exception) {}
        }
        return results
    }

    private suspend fun runForApi(
        api: MainAPI,
        request: StreamRequest,
        title: String,
        source: StreamSource,
        timeoutMs: Long,
    ): List<PlayableStream> {
        // 1. Arama
        val searchResults = withTimeout(timeoutMs) {
            try { api.search(title) } catch (_: Exception) { null }
        } ?: return emptyList()

        // 2. Eşleştirme
        val match = matchResult(searchResults, title) ?: return emptyList()

        // 3. Detay yükle
        val loadResp = withTimeout(timeoutMs) {
            try { api.load(match.url) } catch (_: Exception) { null }
        } ?: return emptyList()

        // 4. Link alma
        val links = mutableListOf<ExtractorLink>()
        val subs = mutableListOf<SubtitleFile>()
        val data = resolveData(loadResp, request) ?: return emptyList()

        withTimeout(timeoutMs) {
            try {
                api.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { subs += it },
                    callback = { links += it },
                )
            } catch (_: Exception) {}
        }

        // 5. PlayableStream'e çevir
        return links.map { link ->
            PlayableStream(
                source = source,
                url = link.url,
                resolution = qualityToResolution(link.quality),
                sizeBytes = null,
                headers = buildMap {
                    if (link.referer.isNotEmpty()) put("Referer", link.referer)
                    putAll(link.headers)
                },
                subtitles = subs.map { StreamSubtitle(url = it.url, language = it.lang) },
                language = null,
            )
        }
    }

    private fun matchResult(results: List<SearchResponse>, title: String): SearchResponse? {
        val lower = title.lowercase().trim()
        return results.firstOrNull { it.name.equals(title, ignoreCase = false) }
            ?: results.firstOrNull { it.name.lowercase().trim() == lower }
            ?: results.firstOrNull {
                it.name.lowercase().contains(lower) || lower.contains(it.name.lowercase())
            }
    }

    private fun resolveData(resp: LoadResponse, request: StreamRequest): String? = when (resp) {
        is MovieLoadResponse -> resp.dataUrl
        is LiveStreamLoadResponse -> resp.dataUrl
        is TvSeriesLoadResponse -> {
            val s = request.season ?: return null
            val e = request.episode ?: return null
            resp.episodes.firstOrNull { it.season == s && it.episode == e }?.data
                ?: resp.episodes.firstOrNull { it.episode == e }?.data
        }
        is AnimeLoadResponse -> {
            val e = request.episode ?: return null
            resp.episodes.values.flatten().firstOrNull { it.episode == e }?.data
        }
        else -> null
    }

    private fun qualityToResolution(quality: Int): Int? = when {
        quality <= 0 -> null
        quality < 480 -> 360
        quality < 720 -> 480
        quality < 1080 -> 720
        quality < 2160 -> 1080
        else -> 2160
    }
}
