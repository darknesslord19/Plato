package com.kaan.play.data.web

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Web sitesi ekleme hatası. Arayüz [kind]'a göre yerelleştirilmiş mesaj seçer. */
class WebSiteException(val kind: Kind) : Exception("Web site $kind") {
    enum class Kind {
        /** Adres boş, http(s) değil ya da kullanıcı adı/parola içeriyor. */
        InvalidUrl,
        AlreadyAdded,
    }
}

object WebSiteUrl {

    /**
     * Kullanıcının girdiği metni kanonik adrese çevirir: şema yoksa `https://` eklenir; yalnızca http/https
     * kabul edilir (`ftp://`, `intent://`, `javascript:` vb. reddedilir). `kullanıcı:parola@` içeren adresler de
     * reddedilir (adresin asıl sunucusunu gizlemeye yarar). Parça (`#...`) korunur: tek sayfalık sitelerde gerekir.
     * @throws WebSiteException [WebSiteException.Kind.InvalidUrl]
     */
    fun normalize(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) throw invalid()

        val withScheme = if ("://" in raw) raw else "https://$raw"
        val url = withScheme.toHttpUrlOrNull() ?: throw invalid()
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw invalid()
        return url.toString()
    }

    /** Ad girilmediyse görünen ad: sunucu adı, başındaki `www.` olmadan. */
    fun displayHost(url: String): String {
        val host = url.toHttpUrlOrNull()?.host ?: return url
        return host.removePrefix("www.")
    }

    private fun invalid() = WebSiteException(WebSiteException.Kind.InvalidUrl)
}
