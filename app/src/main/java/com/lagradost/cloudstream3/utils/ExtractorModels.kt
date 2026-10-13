package com.lagradost.cloudstream3.utils

/** Oynatıcıya iletilecek akış türü. */
enum class ExtractorLinkType { VIDEO, M3U8, DASH, TORRENT, MAGNET }

/**
 * Oynatılabilir bir akış bağlantısı. Eklentiler bunu `newExtractorLink { ... }` builder'ı içinde
 * `var` alanlarla doldurup `callback` ile bildirir.
 */
class ExtractorLink(
    val source: String,
    val name: String,
    var url: String,
    var type: ExtractorLinkType = ExtractorLinkType.VIDEO,
) {
    var referer: String = ""
    var quality: Int = Qualities.Unknown.value
    var headers: Map<String, String> = emptyMap()
    var extractorData: String? = null

    val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    val isDash: Boolean get() = type == ExtractorLinkType.DASH
}

/** Kalite sabitleri. Gerçek eklentiler bunu enum sabiti olarak kullanır (`Qualities.P720.value`). */
enum class Qualities(val value: Int) {
    Unknown(-2),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P1440(1440),
    P2160(2160),
}
