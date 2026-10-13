package com.lagradost.cloudstream3.utils

/** Oynatılabilir bir akış bağlantısı. Eklentiler bunu `callback` ile bildirir. */
data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    val isDash: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val extractorData: String? = null,
)

/** Oynatıcıya iletilecek altyazı dosyası. */
data class SubtitleFile(val lang: String, val url: String)

/** Kalite sabitleri — eklentiler bunları kullanabilir. */
object Qualities {
    const val Unknown = -2
    const val P360 = 360
    const val P480 = 480
    const val P720 = 720
    const val P1080 = 1080
    const val P2160 = 2160
}

/**
 * Dışarıdan çağrılabilir `loadExtractor`: bir video host URL'sinden kaynak çıkarmayı dener.
 * Bu stub, 104 yerleşik CS çıkarıcısını UYGULAMAz; doğrudan URL'yi `callback`'e gönderir.
 * Bu URL zaten oynatılabilirdir (m3u8, mp4 vb.) ya da desteklenmeyen bir host'tur.
 *
 * İleride sık kullanılan host'lar buraya eklenebilir (Doodstream, Vidcloud vb.).
 */
suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val lower = url.lowercase()
    val isM3u8 = lower.contains(".m3u8") || lower.contains("m3u8")
    val isDash = lower.contains(".mpd") || lower.contains("mpd")
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        callback(
            ExtractorLink(
                source = "Direct",
                name = "Direct",
                url = url,
                referer = "",
                quality = Qualities.Unknown,
                isM3u8 = isM3u8,
                isDash = isDash,
            ),
        )
        return true
    }
    return false
}

// Eski imzalar — eklentiler farklı overload'lar çağırabilir; hepsi aynı işe yönlendirilir.
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean = loadExtractor(url, subtitleCallback, callback)
