package com.lagradost.cloudstream3.network

import android.webkit.CookieManager
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.cloudstream.cloudflare.CloudflareResult
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import com.lagradost.nicehttp.Requests
import okhttp3.Request

/**
 * Gerçek CloudStream ikili arayüzü ile uyumlu WebViewResolver.
 * Cloudflare ve JS challenge sayfalarını [CloudflareSolver] üzerinden çözer.
 */
class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = null,
    val useOkhttp: Boolean = false,
    val script: String? = null,
    val scriptCallback: (String) -> Unit = {},
    val timeout: Long = 15_000L,
) {
    companion object {
        fun getWebViewUserAgent(): String = Requests.DEFAULT_UA
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        userAgent: String? = null,
        callback: (Request) -> Boolean = { true },
    ): String? = resolveUsingWebView(url, referer, emptyMap(), userAgent, callback)

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        additionalData: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        callback: (Request) -> Boolean = { true },
    ): String? {
        val solveResult = CloudflareSolver.solve(url, timeoutMs = timeout.coerceAtLeast(25_000L))
        return if (solveResult is CloudflareResult.Success) {
            val creds = solveResult.credentials
            val cookieManager = CookieManager.getInstance()
            val host = CloudflareCache.normalizeHost(url)
            creds.cookies.forEach { (k, v) ->
                cookieManager.setCookie(url, "$k=$v; path=/")
                cookieManager.setCookie("https://$host", "$k=$v; path=/")
                cookieManager.setCookie("https://www.$host", "$k=$v; path=/")
            }
            cookieManager.flush()

            val dummyReq = Request.Builder()
                .url(url)
                .header("User-Agent", creds.userAgent)
                .header("Cookie", creds.cookieHeader)
                .build()
            try { callback(dummyReq) } catch (_: Throwable) {}
            url
        } else {
            null
        }
    }
}
