package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/** Çıkarıcı temel sınıfı. Eklentiler kendi çıkarıcılarını buradan türetebilir. */
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    open val requiresSubUrl: Boolean get() = false

    abstract suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    )

    open fun getExtractorUrl(id: String): String = "$mainUrl/$id"
}

/**
 * Eklentilerin `registerExtractorAPI` ile kaydettiği çıkarıcılar. Aynı sınıf yeniden kaydedilince
 * eskisinin yerine geçer (eklentiler her kaynak isteğinde yeniden yüklenir).
 */
val extractorApis: MutableList<ExtractorApi> = CopyOnWriteArrayList()

fun registerExtractor(extractor: ExtractorApi) {
    extractorApis.removeAll { it.javaClass.name == extractor.javaClass.name && it.mainUrl == extractor.mainUrl }
    extractorApis += extractor
}

private fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.") }.getOrNull()

/** [url]'nin sunucusu çıkarıcının `mainUrl` sunucusuyla aynı ya da onun alt alan adıysa eşleşir. */
private fun findExtractor(url: String): ExtractorApi? {
    val host = hostOf(url) ?: return null
    return extractorApis.firstOrNull { api ->
        val apiHost = hostOf(api.mainUrl) ?: return@firstOrNull false
        host == apiHost || host.endsWith(".$apiHost")
    }
}

/**
 * Dışarıdan çağrılabilir `loadExtractor`: bir video host URL'sinden kaynak çıkarmayı dener.
 * Önce eklentilerin kaydettiği çıkarıcılar ([extractorApis]) sunucu adına göre denenir.
 * CloudStream'in yerleşik çıkarıcıları UYGULANMAZ; eşleşen çıkarıcı yoksa yalnızca adres
 * doğrudan medya gibi görünüyorsa (m3u8, mpd, mp4 …) `callback`'e gönderilir, aksi halde
 * oynatılamayacak bir gömme sayfası kaynak listesine girmesin diye atlanır.
 */
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    findExtractor(url)?.let { extractor ->
        Log.d("PlayCs", "loadExtractor: ${extractor.name} ← ${hostOf(url)}")
        extractor.getUrl(url, referer, subtitleCallback, callback)
        return true
    }
    val path = lower.substringBefore('?')
    val isM3u8 = lower.contains("m3u8")
    val isDash = path.endsWith(".mpd")
    val isFile = MEDIA_EXTENSIONS.any { path.endsWith(it) }
    if (isM3u8 || isDash || isFile) {
        callback(
            ExtractorLink(
                source = "Direct",
                name = "Direct",
                url = url,
                type = when { isM3u8 -> ExtractorLinkType.M3U8; isDash -> ExtractorLinkType.DASH; else -> ExtractorLinkType.VIDEO },
            ).apply { this.referer = referer ?: "" },
        )
        return true
    }

    // Doğrudan medya uzantısı değilse gömülü sayfa (VidMoly, Streamtape, paketli JS vb.) ayrıştırması dene
    val host = hostOf(url) ?: return false
    Log.d("PlayCs", "loadExtractor: çıkarıcı yok, HTML ayrıştırılıyor ← $host")
    val extracted = extractFromHtmlPage(url, referer, subtitleCallback, callback)
    if (extracted) return true

    Log.d("PlayCs", "loadExtractor: çıkarıcı yok, atlandı ← $host")
    return false
}

private suspend fun extractFromHtmlPage(
    url: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val host = hostOf(url) ?: return false
    return runCatching {
        val app = com.lagradost.cloudstream3.app
        val headers = buildMap {
            if (!referer.isNullOrBlank()) put("Referer", referer)
        }
        val response = app.get(url, headers = headers, timeout = 10)
        val rawHtml = response.text
        if (rawHtml.isBlank()) return false
        val html = JsUnpacker.unpackAll(rawHtml)

        // 1. m3u8 bağlantısını ara
        val m3u8Regex = Regex("""["'](https?://[^"'\s\\]+\.m3u8[^"'\s\\]*)["']""")
        val m3u8Match = m3u8Regex.find(html)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1].replace("\\/", "/")
            Log.d("PlayCs", "extractFromHtmlPage: m3u8 bulundu ($host) -> $streamUrl")
            callback(
                ExtractorLink(
                    source = host,
                    name = host,
                    url = streamUrl,
                    referer = url,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.M3U8,
                )
            )
            return true
        }

        // 2. Streamtape robotlink / get_video
        if (host.contains("streamtape") || host.contains("strcloud")) {
            val robotRegex = Regex("""id=['"]robotlink['"][^>]*>([^<]+)<""")
            val robotMatch = robotRegex.find(html)
            if (robotMatch != null) {
                var streamUrl = robotMatch.groupValues[1]
                if (!streamUrl.startsWith("http")) streamUrl = "https:$streamUrl"
                Log.d("PlayCs", "extractFromHtmlPage: streamtape video bulundu -> $streamUrl")
                callback(
                    ExtractorLink(
                        source = host,
                        name = host,
                        url = streamUrl,
                        referer = url,
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.VIDEO,
                    )
                )
                return true
            }
        }

        // 3. mp4 bağlantısını ara
        val mp4Regex = Regex("""["'](https?://[^"'\s\\]+\.mp4[^"'\s\\]*)["']""")
        val mp4Match = mp4Regex.find(html)
        if (mp4Match != null) {
            val streamUrl = mp4Match.groupValues[1].replace("\\/", "/")
            Log.d("PlayCs", "extractFromHtmlPage: mp4 bulundu ($host) -> $streamUrl")
            callback(
                ExtractorLink(
                    source = host,
                    name = host,
                    url = streamUrl,
                    referer = url,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.VIDEO,
                )
            )
            return true
        }

        false
    }.getOrDefault(false)
}

private val MEDIA_EXTENSIONS = listOf(".mp4", ".mkv", ".webm", ".m4v", ".mov", ".ts")

/** Eski imza — eklentiler referer'sız da çağırabilir. */
suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean = loadExtractor(url, null, subtitleCallback, callback)

/** `newExtractorLink { ... }` builder'ı; eklentiler `quality`/`referer`/`headers` burada ayarlar. */
suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    initializer: suspend ExtractorLink.() -> Unit = {},
): ExtractorLink {
    val link = ExtractorLink(source, name, url, type)
    link.initializer()
    return link
}

/** Görünen ad metninden kalite tahmini ("1080p", "HD", "CAM" vb.). */
fun getQualityFromName(name: String?): Int {
    if (name.isNullOrBlank()) return Qualities.Unknown.value
    val match = Regex("""(\d{3,4})[pP]?""").find(name)
    val num = match?.groupValues?.get(1)?.toIntOrNull()
    if (num != null) return num
    val lower = name.lowercase()
    return when {
        lower.contains("4k") || lower.contains("2160") -> Qualities.P2160.value
        lower.contains("1440") || lower.contains("2k") -> Qualities.P1440.value
        lower.contains("1080") || lower.contains("fhd") -> Qualities.P1080.value
        lower.contains("720") || lower.contains("hd") -> Qualities.P720.value
        lower.contains("480") || lower.contains("sd") -> Qualities.P480.value
        lower.contains("360") -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}

val INFER_TYPE: ExtractorLinkType = ExtractorLinkType.VIDEO

fun ExtractorApi.fixUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (url.startsWith("//")) return "https:$url"
    return "${this.mainUrl.trimEnd('/')}/${url.trimStart('/')}"
}

/**
 * `eval(function(p,a,c,k,e,d){...})` biçimindeki paketlenmiş (Dean Edwards "packer") JS'i açar.
 * Genel/kamuya açık bir teknik uygulanır (CloudStream'e özgü kod kopyalanmaz); eşleşme yoksa
 * metin olduğu gibi döner.
 */
fun getAndUnpack(script: String): String {
    val match = PACKER_REGEX.find(script) ?: return script
    return try {
        val (payload, radixRaw, countRaw, keywordsRaw) = match.destructured
        val radix = radixRaw.toIntOrNull() ?: 36
        val count = countRaw.toIntOrNull() ?: 0
        val keywords = keywordsRaw.split("|")
        var result = payload
        for (i in count - 1 downTo 0) {
            val word = keywords.getOrNull(i)
            if (!word.isNullOrEmpty()) {
                result = Regex("\\b${unbase(i, radix)}\\b").replace(result, Regex.escapeReplacement(word))
            }
        }
        result
    } catch (_: Exception) {
        script
    }
}

private val PACKER_REGEX = Regex(
    """\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
    RegexOption.DOT_MATCHES_ALL,
)

private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

private fun unbase(value: Int, base: Int): String {
    if (value == 0) return ALPHABET[0].toString()
    if (base in 2..36) return value.toString(base)
    var v = value
    val sb = StringBuilder()
    while (v > 0) { sb.append(ALPHABET[v % base]); v /= base }
    return sb.reverse().toString()
}
