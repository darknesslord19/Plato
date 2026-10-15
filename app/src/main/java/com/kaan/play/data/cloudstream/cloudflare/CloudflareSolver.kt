package com.kaan.play.data.cloudstream.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kaan.play.PlayApp
import com.lagradost.cloudstream3.PlayNetworkGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ekran dışı (gizli) Android WebView kullanarak Cloudflare JavaScript challenge'larını çözen yardımcı.
 */
object CloudflareSolver {
    private const val TAG = "PlayCs"
    const val DEFAULT_TIMEOUT_MS = 25_000L

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying = _isVerifying.asStateFlow()

    private val blockedHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val blockedUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val hostMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    var customContext: Context? = null

    private fun getEffectiveContext(): Context? {
        return customContext ?: runCatching { PlayApp.appContext }.getOrNull()
    }

    fun wasBlocked(hostOrUrl: String): Boolean {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        val time = blockedHosts[host] ?: return false
        return (System.currentTimeMillis() - time) < 5 * 60 * 1000L
    }

    fun getBlockedUrl(hostOrUrl: String): String? {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        return blockedUrls[host]
    }

    fun unblock(hostOrUrl: String) {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        blockedHosts.remove(host)
        blockedUrls.remove(host)
    }

    suspend fun solve(
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): CloudflareResult {
        PlayNetworkGuard.checkUrl(url)

        val host = CloudflareCache.normalizeHost(url)
        CloudflareCache.get(host)?.let { cached ->
            return CloudflareResult.Success(cached)
        }

        val mutex = hostMutexes.computeIfAbsent(host) { Mutex() }
        return mutex.withLock {
            CloudflareCache.get(host)?.let { cached ->
                return CloudflareResult.Success(cached)
            }

            val ctx = getEffectiveContext()
            if (ctx == null) {
                return CloudflareResult.Failure("Context bulunamadı")
            }

            withContext(Dispatchers.Main) {
                _isVerifying.value = true
                try {
                    withTimeoutOrNull(timeoutMs) {
                        solveInternal(ctx, url, host)
                    } ?: run {
                        blockedHosts[host] = System.currentTimeMillis()
                        blockedUrls[host] = url
                        Log.w(TAG, "[Cloudflare] Çözümleme zaman aşımına uğradı ($host, ${timeoutMs}ms)")
                        CloudflareResult.Failure("Zaman aşımı: İntraktif Turnstile doğrulaması gerekebilir", isInteractive = true)
                    }
                } finally {
                    _isVerifying.value = false
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun solveInternal(
        context: Context,
        url: String,
        host: String,
    ): CloudflareResult {
        val deferred = CompletableDeferred<CloudflareResult>()
        val webView = WebView(context)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true

            // Standart Chrome User-Agent'ı kullanarak WebView engelleyicilerini hafiflet
            val originalUa = userAgentString
            val cleanUa = originalUa
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
            userAgentString = cleanUa
        }

        val handler = Handler(Looper.getMainLooper())
        var isDone = false

        fun finish(result: CloudflareResult) {
            if (isDone) return
            isDone = true
            handler.removeCallbacksAndMessages(null)
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            } catch (_: Throwable) {}
            deferred.complete(result)
        }

        fun checkCookies(): Boolean {
            if (isDone) return false
            val cookieStr = cookieManager.getCookie(url)
            if (!cookieStr.isNullOrBlank() && cookieStr.contains("cf_clearance=")) {
                val cookies = parseCookieString(cookieStr)
                val ua = webView.settings.userAgentString
                val creds = CloudflareCredentials(cookies = cookies, userAgent = ua)
                CloudflareCache.put(host, creds)
                blockedHosts.remove(host)
                blockedUrls.remove(host)
                cookies.forEach { (k, v) ->
                    cookieManager.setCookie(url, "$k=$v; path=/")
                    cookieManager.setCookie("https://$host", "$k=$v; path=/")
                    cookieManager.setCookie("https://www.$host", "$k=$v; path=/")
                }
                cookieManager.flush()
                Log.d(TAG, "[Cloudflare] cf_clearance başarıyla alındı ($host)")
                finish(CloudflareResult.Success(creds))
                return true
            }
            return false
        }

        val checkRunnable = object : Runnable {
            override fun run() {
                if (isDone) return
                if (!checkCookies() && !isDone) {
                    handler.postDelayed(this, 350)
                }
            }
        }
        handler.postDelayed(checkRunnable, 350)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString() ?: return false
                try {
                    PlayNetworkGuard.checkUrl(nextUrl)
                    return false
                } catch (_: SecurityException) {
                    return true
                }
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                val currentTitle = view?.title.orEmpty()
                Log.d(TAG, "[Cloudflare] onPageFinished: host=$host title='$currentTitle' url=$finishedUrl")
                checkCookies()
            }
        }

        webView.loadUrl(url)

        return try {
            deferred.await()
        } finally {
            handler.removeCallbacksAndMessages(null)
            if (!isDone) {
                isDone = true
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                } catch (_: Throwable) {}
            }
        }
    }

    fun parseCookieString(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(';')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) part.take(idx).trim() to part.substring(idx + 1).trim() else null
            }
            .toMap()
    }
}
