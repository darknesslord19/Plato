package com.lagradost.cloudstream3.utils

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
