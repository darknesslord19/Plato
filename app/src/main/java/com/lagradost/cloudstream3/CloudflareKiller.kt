package com.lagradost.cloudstream3

import android.os.Looper
import android.util.Log
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.cloudstream.cloudflare.CloudflareDetector
import com.kaan.play.data.cloudstream.cloudflare.CloudflareResult
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * CloudflareKiller interceptor'ı. Gerçek CloudStream ikili arayüzü ile uyumlu olarak
 * [Interceptor] uygular ve Cloudflare challenge sayfalarını çözer.
 */
open class CloudflareKiller : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()
        val host = CloudflareCache.normalizeHost(url)

        // 1. Varsa önbellekteki çerez ve User-Agent ile isteği donat
        val cached = CloudflareCache.get(host)
        val initialRequest = if (cached != null) {
            val builder = originalRequest.newBuilder()
            if (cached.cookies.isNotEmpty()) {
                val existing = originalRequest.header("Cookie")
                val merged = if (existing.isNullOrBlank()) {
                    cached.cookieHeader
                } else {
                    "$existing; ${cached.cookieHeader}"
                }
                builder.header("Cookie", merged)
            }
            if (cached.userAgent.isNotBlank()) {
                builder.header("User-Agent", cached.userAgent)
            }
            builder.build()
        } else {
            originalRequest
        }

        var response = chain.proceed(initialRequest)

        // 2. Yanıt 403/503/429 ve Cloudflare challenge ise ve daha önce tekrar denenmediyse:
        val isRetry = originalRequest.tag(CloudflareRetryTag::class.java) != null
        if (!isRetry && (response.code in setOf(403, 503, 429)) && !CloudflareSolver.wasBlocked(host)) {
            val peek = runCatching { response.peekBody(4096).string() }.getOrNull()
            if (CloudflareDetector.isChallenge(response.code, response.headers, peek)) {
                Log.d(TAG, "[CloudflareKiller] Challenge algılandı ($host, HTTP ${response.code}), çözücü başlatılıyor...")

                val result = if (Looper.myLooper() == Looper.getMainLooper()) {
                    CloudflareResult.Failure("Ana iş parçacığında senkron interceptor çalıştırılamaz")
                } else {
                    runBlocking { CloudflareSolver.solve(url) }
                }

                if (result is CloudflareResult.Success) {
                    Log.d(TAG, "[CloudflareKiller] Başarıyla çözüldü ($host), istek tekrarlanıyor")
                    response.close()
                    val retryReq = originalRequest.newBuilder()
                        .tag(CloudflareRetryTag::class.java, CloudflareRetryTag)
                        .header("Cookie", result.credentials.cookieHeader)
                        .header("User-Agent", result.credentials.userAgent)
                        .build()
                    response = chain.proceed(retryReq)
                    // Çerezle de engel sayfası geldiyse çerez geçersizdir (çoğunlukla WebView deposunda
                    // kalan eski cf_clearance): sil ve kullanıcıya "Doğrula" gösterilsin.
                    val retryPeek = runCatching { response.peekBody(4096).string() }.getOrNull()
                    if (CloudflareDetector.isChallenge(response.code, response.headers, retryPeek)) {
                        Log.w(TAG, "[CloudflareKiller] Çerezle de engel sayfası geldi ($host); eski çerez siliniyor")
                        CloudflareSolver.invalidate(host, url)
                        CloudflareSolver.markBlocked(host, url)
                        CloudflareSolver.noteUnresolved(host, url)
                    }
                } else {
                    Log.w(TAG, "[CloudflareKiller] Çözülemedi ($host)")
                    CloudflareSolver.noteUnresolved(host, url)
                }
            }
        }

        return response
    }

    suspend fun bypassCloudflare(url: String): NiceResponse = app.get(url)

    suspend fun getUserAgent(): String = CloudflareCache.get(Requests.DEFAULT_UA)?.userAgent ?: Requests.DEFAULT_UA

    fun getCookieHeaders(url: String): Map<String, String> {
        val creds = CloudflareCache.get(url) ?: return emptyMap()
        return if (creds.cookies.isNotEmpty()) mapOf("Cookie" to creds.cookieHeader) else emptyMap()
    }

    companion object {
        private const val TAG = "PlayCs"

        @JvmStatic
        val DEFAULT_HEADERS: Map<String, String> = mapOf("User-Agent" to Requests.DEFAULT_UA)
    }
}

object CloudflareRetryTag
