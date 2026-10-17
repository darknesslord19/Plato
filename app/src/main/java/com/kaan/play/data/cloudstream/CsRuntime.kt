package com.kaan.play.data.cloudstream

import android.util.Log
import androidx.media3.common.MimeTypes
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
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

        Log.d(TAG, "$pluginName: ${apis.size} MainAPI ile deneniyor (başlık=\"$title\")")
        for (api in apis) {
            val started = System.currentTimeMillis()
            try {
                val streams = runForApi(api, request, title, source, timeoutMs)
                Log.d(TAG, "$pluginName/${api.name}: ${streams.size} akış (${System.currentTimeMillis() - started} ms)")
                results += streams
                if (results.isNotEmpty()) break
            } catch (t: Throwable) {
                Log.d(TAG, "$pluginName/${api.name}: hata ${t.javaClass.simpleName}: ${t.message} (${System.currentTimeMillis() - started} ms)")
            }
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
        // 1. Arama; sonuç yoksa noktalamasız başlıkla bir kez daha (bazı site aramaları ":" / "-" ile boş döner)
        var searchResults = search(api, title, timeoutMs) ?: return emptyList()
        val plain = normalize(title)
        if (searchResults.isEmpty() && plain != title.lowercase()) {
            searchResults = search(api, plain, timeoutMs) ?: return emptyList()
        }

        // 2. Eşleştirme
        val match = matchResult(searchResults, title)
        Log.d(TAG, "${api.name}: matchResult ${if (match != null) "bulundu" else "yok"}")
        if (match == null) return emptyList()

        // 3. Detay yükle
        val loadResp = withTimeout(timeoutMs) {
            try { api.load(match.url) } catch (t: Throwable) {
                Log.d(TAG, "${api.name}: load hata ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
        }
        Log.d(TAG, "${api.name}: load ${if (loadResp != null) "başarılı" else "boş"}")
        if (loadResp == null) return emptyList()

        // 4. Link alma
        val data = resolveData(loadResp, request)
        if (data == null) {
            Log.d(TAG, "${api.name}: resolveData boş (sezon/bölüm eşleşmedi)")
            return emptyList()
        }
        return loadLinksDirect(api, data, source, timeoutMs)
    }

    suspend fun loadLinksDirect(
        api: MainAPI,
        data: String,
        source: StreamSource,
        timeoutMs: Long,
    ): List<PlayableStream> {
        val extApis = com.lagradost.cloudstream3.utils.extractorApis
        extApis.removeAll {
            it.mainUrl.contains("hotlinger", ignoreCase = true) ||
            it.mainUrl.contains("pichive", ignoreCase = true) ||
            it.mainUrl.contains("dplayer", ignoreCase = true)
        }
        val pichiveExts = listOf(
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Pichive", "https://four.pichive.online"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Pichive", "https://pichive.online"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Hotlinger", "https://sn.hotlinger.com"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Hotlinger", "https://hotlinger.com"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("DPlayer", "https://four.dplayer82.site"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("DPlayer", "https://sn.dplayer82.site"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("DPlayer", "https://dplayer82.site"),
        )
        for (ext in pichiveExts) {
            extApis.add(0, ext)
        }

        val links = mutableListOf<ExtractorLink>()
        val subs = mutableListOf<SubtitleFile>()
        withTimeout(timeoutMs) {
            try {
                api.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { subs += it },
                    callback = { links += it },
                )
            } catch (t: Throwable) {
                Log.d(TAG, "${api.name}: loadLinks hata ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }
        Log.d(TAG, "${api.name}: loadLinks ${links.size} ExtractorLink " +
            links.map { "${it.name}/${it.type}/${it.quality}@${runCatching { java.net.URI(it.url).host }.getOrNull()}" })

        return links.map { link ->
            PlayableStream(
                source = source,
                url = link.url,
                resolution = qualityToResolution(link.quality),
                sizeBytes = null,
                headers = buildMap {
                    put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    if (link.referer.isNotEmpty()) put("Referer", link.referer)
                    putAll(link.headers)
                },
                subtitles = subs.map { StreamSubtitle(url = it.url, language = it.lang) },
                language = null,
                dubbed = com.kaan.play.data.stream.DubDetector.isDubbed(link.name),
                mimeType = when (link.type) {
                    ExtractorLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
                    ExtractorLinkType.DASH -> MimeTypes.APPLICATION_MPD
                    else -> null
                },
            )
        }
    }


    private suspend fun search(api: MainAPI, query: String, timeoutMs: Long): List<SearchResponse>? {
        val results = withTimeout(timeoutMs) {
            try { api.search(query) } catch (t: Throwable) {
                Log.d(TAG, "${api.name}: search hata ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
        } ?: return null
        Log.d(TAG, "${api.name}: search \"$query\" ${results.size} sonuç ${results.take(5).map { it.name }}")
        return results
    }

    /** Önce birebir, sonra noktalama/büyük-küçük harf yok sayılarak birebir, en son kelime sınırlı içerme. */
    private fun matchResult(results: List<SearchResponse>, title: String): SearchResponse? {
        val plain = normalize(title)
        return results.firstOrNull { it.name == title }
            ?: results.firstOrNull { normalize(it.name) == plain }
            ?: results.firstOrNull {
                val name = normalize(it.name)
                name.isNotEmpty() && (" $name ".contains(" $plain ") || " $plain ".contains(" $name "))
            }
    }

    /** Harf/rakam dışını boşluğa çevirir, boşlukları teke indirir; Türkçe büyük harfler (İ, I) doğru küçülür. */
    private fun normalize(text: String): String =
        text.lowercase(TURKISH).replace(NON_WORD, " ").trim().replace(SPACES, " ")

    private fun resolveData(resp: LoadResponse, request: StreamRequest): String? = when (resp) {
        is MovieLoadResponse -> resp.dataUrl
        is LiveStreamLoadResponse -> resp.dataUrl
        is TvSeriesLoadResponse -> {
            val s = request.season
            val e = request.episode
            findEpisodeData(resp.episodes, s, e)
        }
        is AnimeLoadResponse -> {
            val s = request.season
            val e = request.episode
            val allEpisodes = resp.episodes.values.flatten()
            findEpisodeData(allEpisodes, s, e)
        }
        else -> null
    }

    private fun findEpisodeData(episodes: List<com.lagradost.cloudstream3.Episode>, season: Int?, episode: Int?): String? {
        if (episodes.isEmpty()) return null
        if (episode == null) return episodes.firstOrNull()?.data

        // 1. Exact match on season and episode
        if (season != null) {
            val match = episodes.firstOrNull { it.season == season && it.episode == episode }
            if (match != null) return match.data
        }

        // 2. Match on episode directly
        val epMatch = episodes.firstOrNull { it.episode == episode }
        if (epMatch != null) return epMatch.data

        // 3. Fallback: extract episode number from name or data URL
        val extractedMatch = episodes.firstOrNull { ep ->
            val num = extractEpisodeNum(ep)
            num == episode && (season == null || ep.season == null || ep.season == season)
        }
        if (extractedMatch != null) return extractedMatch.data

        // 4. Fallback if index-based: e.g. episode 1 is index 0
        if (season == null || episodes.all { it.season == null || it.season == season }) {
            val index = episode - 1
            if (index in episodes.indices) {
                return episodes[index].data
            }
        }

        return null
    }

    private fun extractEpisodeNum(ep: com.lagradost.cloudstream3.Episode): Int? {
        if (ep.episode != null && ep.episode!! > 0) return ep.episode
        val name = ep.name ?: ""
        val nameMatch = Regex("""(?:bölüm|episode|ep\.?)\s*(\d+)|\b(\d+)\s*\.\s*bölüm""", RegexOption.IGNORE_CASE).find(name)
        if (nameMatch != null) {
            val numStr = nameMatch.groupValues[1].ifEmpty { nameMatch.groupValues[2] }
            numStr.toIntOrNull()?.let { return it }
        }
        val dataMatch = Regex("""(?:bölüm|bolum|ep)\D*?(\d+)""", RegexOption.IGNORE_CASE).find(ep.data)
        return dataMatch?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun qualityToResolution(quality: Int): Int? = when {
        quality <= 0 -> null
        quality < 480 -> 360
        quality < 720 -> 480
        quality < 1080 -> 720
        quality < 2160 -> 1080
        else -> 2160
    }

    /**
     * Eklentinin ana sayfa satırlarını çeker.
     * Her [MainPageData] girdisi için ayrı [withTimeout] uygulanır;
     * hata olursa o satır atlanır / uygulama asla çökmez.
     */
    suspend fun mainPage(
        api: MainAPI,
        page: Int = 1,
        timeoutMs: Long = 6_000L,
    ): List<CsCatalogSection> {
        val sectionsResult = runCatching { api.mainPage }
        val sections = sectionsResult.getOrNull()
        val results = mutableListOf<CsCatalogSection>()

        if (sectionsResult.isFailure) {
            val err = sectionsResult.exceptionOrNull()
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: api.mainPage istisna fırlattı -> ${err?.javaClass?.name}: ${err?.message}")
        } else if (sections.isNullOrEmpty()) {
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: api.mainPage boş liste (hasMainPage=${api.hasMainPage})")
        } else {
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: api.mainPage dolu, ${sections.size} bölüm bulundu: ${sections.map { it.name }}")
        }

        val mainPageStarted = System.currentTimeMillis()
        if (!sections.isNullOrEmpty()) {
            for (section in sections) {
                // Eklenti mainUrl'den farklı (güncel) bir alan adına gidebilir: engeli o istekten öğren.
                val cf = com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
                if (cf.wasBlocked(api.mainUrl) || cf.unresolvedSince(mainPageStarted) != null) {
                    Log.w(TAG, "[DEBUG_MAINPAGE] ${api.name}: Bot koruması aşılamadığı için kalan bölümler atlanıyor.")
                    break
                }
                // DiziPal gibi 20+ bölüm tanımlayan sitelerde 10+ satır alındıysa ve süre uzadıysa sonlandır
                if (results.size >= 10 && System.currentTimeMillis() - mainPageStarted > 12_000L) {
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: Yeterli bölüm (${results.size}) toplandı, kalan bölümler atlanıyor.")
                    break
                }
                val started = System.currentTimeMillis()
                try {
                    if (results.isNotEmpty()) {
                        kotlinx.coroutines.delay(100L)
                    }
                    val req = com.lagradost.cloudstream3.MainPageRequest(section.name, section.data, section.horizontalImages)
                    val resp = withTimeout(timeoutMs) {
                        api.getMainPage(page, req)
                    }
                    if (resp == null) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: getMainPage null döndü (${System.currentTimeMillis() - started} ms)")
                    } else if (resp.items.isEmpty()) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: resp.items boş liste (hasNext=${resp.hasNext}) (${System.currentTimeMillis() - started} ms)")
                    } else {
                        val totalItems = resp.items.sumOf { it.list.size }
                        if (totalItems == 0) {
                            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: resp.items dolu (${resp.items.size} satır) ama içindeki item.list'ler boş (${System.currentTimeMillis() - started} ms)")
                        } else {
                            for (item in resp.items) {
                                val title = item.name.ifBlank { section.name }
                                results += CsCatalogSection(
                                    title = title,
                                    items = item.list,
                                    isHorizontalImages = item.isHorizontalImages,
                                    hasNext = resp.hasNext,
                                )
                            }
                            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: başarılı, $totalItems öğe (${System.currentTimeMillis() - started} ms)")
                        }
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: istisna -> ${t.javaClass.name}: ${t.message} (${System.currentTimeMillis() - started} ms)")
                    if (t.cause != null) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: kök neden -> ${t.cause?.javaClass?.name}: ${t.cause?.message}")
                    }
                }
            }
        } else if (api.hasMainPage) {
            val started = System.currentTimeMillis()
            try {
                Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage=true, tekil getMainPage çağrılıyor (sayfa=$page)...")
                val req = com.lagradost.cloudstream3.MainPageRequest(api.name, api.mainUrl, horizontalImages = false)
                val resp = withTimeout(timeoutMs) { api.getMainPage(page, req) }
                if (resp == null) {
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage ile getMainPage null döndü (${System.currentTimeMillis() - started} ms)")
                } else if (resp.items.isEmpty()) {
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage ile resp.items boş (${System.currentTimeMillis() - started} ms)")
                } else {
                    val totalItems = resp.items.sumOf { it.list.size }
                    if (totalItems == 0) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage ile resp.items dolu ama listeler boş (${System.currentTimeMillis() - started} ms)")
                    } else {
                        for (item in resp.items) {
                            results += CsCatalogSection(
                                title = item.name.ifBlank { api.name },
                                items = item.list,
                                isHorizontalImages = item.isHorizontalImages,
                                hasNext = resp.hasNext,
                            )
                        }
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage başarılı, $totalItems öğe (${System.currentTimeMillis() - started} ms)")
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage istisna -> ${t.javaClass.name}: ${t.message} (${System.currentTimeMillis() - started} ms)")
            }
        } else {
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hem api.mainPage boş hem de hasMainPage=false, katalog yok")
        }

        return results
    }

    suspend fun mainPage(
        apis: List<MainAPI>,
        page: Int = 1,
        timeoutMs: Long = 27_000L,
    ): List<CsCatalogSection> {
        val results = mutableListOf<CsCatalogSection>()
        for (api in apis) {
            results += mainPage(api, page, timeoutMs)
        }
        return results
    }

    private companion object {
        const val TAG = "PlayCs"
        val TURKISH: java.util.Locale = java.util.Locale.forLanguageTag("tr")
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        val SPACES = Regex("\\s+")
    }
}

/** CS ana sayfa satırı sonucu (başlık + SearchResponse listesi). */
data class CsCatalogSection(
    val title: String,
    val items: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
    val hasNext: Boolean = false,
)
