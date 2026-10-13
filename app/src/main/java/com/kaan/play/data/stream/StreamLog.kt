package com.kaan.play.data.stream

import android.util.Log
import com.kaan.play.BuildConfig
import java.util.Locale

/**
 * Akış adreslerini yalnızca debug derlemede ve **kısaltılmış** (şema + host + uzantı) loglar; yol/sorgu token taşıyabilir.
 * Reddedilen adresin (magnet, torrent...) türünü anlamak için.
 */
object StreamLog {
    private const val TAG = "PlayStreamUrl"

    /** `magnet`, `https://host/.m3u8`, `(http dışı)` gibi tek satırlık özet. */
    internal fun describe(url: String): String {
        val u = url.trim()
        val scheme = u.substringBefore(':', "").lowercase(Locale.ROOT).take(16)
        if (scheme.isEmpty() || !scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return "(adres değil)"
        if (scheme != "http" && scheme != "https") return "$scheme:"
        val rest = u.substringAfter("://", "")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').take(80)
        val path = rest.substring(host.length.coerceAtMost(rest.length)).substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT).take(8)
        return "$scheme://$host/${if (ext.isEmpty()) "" else ".$ext"}"
    }

    fun rejected(origin: String, url: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$origin: reddedildi (http(s) değil) ${describe(url)}")
    }

    fun opening(url: String, mimeType: String?) {
        if (BuildConfig.DEBUG) Log.d(TAG, "oynatılıyor ${describe(url)} mime=${mimeType ?: "?"}")
    }
}
