package com.lagradost.cloudstream3.utils

class M3u8Helper {
    companion object {
        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = emptyMap(),
            name: String = source,
        ): List<ExtractorLink> = listOf(
            ExtractorLink(
                source = source,
                name = name,
                url = streamUrl,
                type = ExtractorLinkType.M3U8,
            ).apply {
                this.referer = referer
                this.quality = quality ?: Qualities.Unknown.value
                this.headers = headers
            }
        )
    }
}
