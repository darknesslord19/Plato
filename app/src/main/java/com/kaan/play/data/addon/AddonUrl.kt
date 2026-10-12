package com.kaan.play.data.addon

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AddonUrl {

    private const val MANIFEST = "manifest.json"

    /**
     * Kullanıcının girdiği metni kanonik manifest adresine çevirir:
     * şema yoksa `https://` eklenir; yol `manifest.json` ile bitmiyorsa eklenir; `#` kısmı atılır.
     * Yalnızca http/https kabul edilir. `?` parametreli adresler reddedilir: akış adresleri
     * taban yola eklenerek kurulduğu için sorgu dizesi korunamaz.
     */
    fun normalize(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) throw invalid()

        val withScheme = if ("://" in raw) raw else "https://$raw"
        val url = withScheme.toHttpUrlOrNull() ?: throw invalid()
        if (url.query != null) throw invalid()

        val segments = url.pathSegments.toMutableList()
        if (segments.last().isEmpty()) segments.removeAt(segments.lastIndex)
        if (segments.lastOrNull() != MANIFEST) segments += MANIFEST

        return url.newBuilder()
            .fragment(null)
            .encodedPath("/")
            .apply { segments.forEach { addEncodedPathSegment(it) } }
            .build()
            .toString()
    }

    /** `https://host/yol/manifest.json` -> `https://host/yol` (sonda eğik çizgi yok). */
    fun baseOf(manifestUrl: String): String = manifestUrl.removeSuffix("/$MANIFEST")

    private fun invalid() = AddonException(AddonException.Kind.InvalidUrl)
}
