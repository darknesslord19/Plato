package com.kaan.play.ui.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.kaan.play.data.web.WebDataCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Bir site kaldırılınca web verisini siler. WebView çerezleri/önbelleği uygulamaya özeldir; yine de
 * siteye ait çerezler ve depolama ayrıca silinir, son site kaldırılınca hepsi silinir.
 * WebView API'leri ana iş parçacığında çağrılır.
 */
class AndroidWebDataCleaner(private val context: Context) : WebDataCleaner {

    override suspend fun clear(url: String, anySiteLeft: Boolean) {
        withContext(Dispatchers.Main) {
            val cookies = CookieManager.getInstance()
            if (!anySiteLeft) {
                removeAllCookies(cookies)
                WebStorage.getInstance().deleteAllData()
            } else {
                expireCookiesOf(cookies, url)
                url.toHttpUrlOrNull()?.let { u ->
                    val origin = "${u.scheme}://${u.host}" + if (u.port != defaultPort(u.scheme)) ":${u.port}" else ""
                    WebStorage.getInstance().deleteOrigin(origin)
                }
            }
            cookies.flush()
            // Önbellek tüm sitelerce paylaşılır; kaldırmada baştan oluşması sorun değildir.
            WebView(context.applicationContext).apply { clearCache(true); destroy() }
        }
    }

    private suspend fun removeAllCookies(cookies: CookieManager) =
        suspendCancellableCoroutine { cont -> cookies.removeAllCookies { cont.resume(Unit) { } } }

    /** Çerez adlarını `Max-Age=0` ile bitirir; sunucu adı ve üst alan adları (a.b.c -> b.c) için denenir. */
    private fun expireCookiesOf(cookies: CookieManager, url: String) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        val labels = host.split('.')
        val domains = (0..labels.size - 2).map { labels.drop(it).joinToString(".") } // son iki etikete kadar
        for (domain in domains.ifEmpty { listOf(host) }) {
            val header = cookies.getCookie("https://$domain/") ?: cookies.getCookie("http://$domain/") ?: continue
            for (pair in header.split(';')) {
                val name = pair.substringBefore('=').trim()
                if (name.isEmpty()) continue
                for (base in listOf("https://$domain/", "http://$domain/")) {
                    cookies.setCookie(base, "$name=; Max-Age=0; Path=/")
                    cookies.setCookie(base, "$name=; Max-Age=0; Path=/; Domain=$domain")
                }
            }
        }
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80
}
