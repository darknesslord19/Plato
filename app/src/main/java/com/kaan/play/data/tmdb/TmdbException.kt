package com.kaan.play.data.tmdb

/**
 * TMDB katmanının tek hata türü. Mesaj ve alanlar yalnızca uç noktayı ([path]) taşır;
 * API anahtarı sorgu parametresinde gittiği için tam URL hiçbir zaman hataya konmaz.
 */
class TmdbException(
    val kind: Kind,
    val httpCode: Int? = null,
    val path: String,
    cause: Throwable? = null,
) : Exception("TMDB $kind${httpCode?.let { " (HTTP $it)" } ?: ""}: $path", cause) {

    enum class Kind {
        /** local.properties içinde TMDB_API_KEY yok. */
        MissingApiKey,
        /** 401: anahtar TMDB tarafından reddedildi. */
        InvalidApiKey,
        NotFound,
        RateLimited,
        Http,
        Network,
        Parse,
    }
}
