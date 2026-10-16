package com.lagradost.nicehttp

import android.util.Log
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.cloudstream.cloudflare.CloudflareDetector
import com.kaan.play.data.cloudstream.cloudflare.CloudflareResult
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import com.lagradost.cloudstream3.PlayNetworkGuard
import com.lagradost.cloudstream3.mapper
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass

/**
 * Gerçek CloudStream eklentileri `com.lagradost.nicehttp.Requests`/`NiceResponse` sınıflarına
 * derlenmiş durumda (bağımsız "NiceHttp" kütüphanesi, cloudstream3 paketinin parçası değildir).
 * Bu dosya yalnızca arayüzü uygular; NiceHttp'in kendi kodu kopyalanmaz.
 */
class Requests(private val client: OkHttpClient) {
    val baseClient: OkHttpClient get() = client

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        parser: ResponseParser? = null,
    ): NiceResponse = execute("GET", buildUrl(url, params), headers, referer, null, cookies, allowRedirects, timeout, interceptor)

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = null,
        files: List<Any>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        parser: ResponseParser? = null,
    ): NiceResponse {
        val body = requestBody ?: when {
            json != null -> mapper.writeValueAsString(json).toRequestBody("application/json".toMediaType())
            data != null -> FormBody.Builder().apply { data.forEach { (k, v) -> add(k, v) } }.build()
            else -> ByteArray(0).toRequestBody(null)
        }
        return execute("POST", buildUrl(url, params), headers, referer, body, cookies, allowRedirects, timeout, interceptor)
    }

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        parser: ResponseParser? = null,
    ): NiceResponse = execute("HEAD", buildUrl(url, params), headers, referer, null, cookies, allowRedirects, timeout, interceptor)

    private fun buildUrl(base: String, params: Map<String, String>): String {
        if (params.isEmpty()) return base
        val sep = if ('?' in base) '&' else '?'
        return base + sep + params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
    }

    private suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        referer: String?,
        body: RequestBody?,
        cookies: Map<String, String>,
        allowRedirects: Boolean,
        timeout: Long,
        interceptor: Interceptor?,
        isRetry: Boolean = false,
        freshTried: Boolean = false,
    ): NiceResponse {
        PlayNetworkGuard.checkUrl(url)

        val host = CloudflareCache.normalizeHost(url)
        val cachedCf = CloudflareCache.get(host)

        val req = Request.Builder().url(url).method(method, body)
        headers.forEach { (k, v) -> req.addHeader(k, v) }
        referer?.let { req.addHeader("Referer", it) }

        // User-Agent: cf_clearance çerezi varsa mutlaka o çerezi üreten UA kullanılmalı (Cloudflare UA eşleşmesi şartı)
        if (cachedCf != null && cachedCf.userAgent.isNotBlank()) {
            req.header("User-Agent", cachedCf.userAgent)
        } else if (!headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            req.addHeader("User-Agent", DEFAULT_UA)
        }

        // Çerezler: önbellekteki CF çerezleri + fonksiyon parametresi çerezleri
        val mergedCookies = mutableMapOf<String, String>()
        if (cachedCf != null && cachedCf.cookies.isNotEmpty()) {
            mergedCookies.putAll(cachedCf.cookies)
        }
        mergedCookies.putAll(cookies)

        val existingCookie = headers.entries.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }?.value
        if (mergedCookies.isNotEmpty() || !existingCookie.isNullOrBlank()) {
            val finalCookie = when {
                existingCookie.isNullOrBlank() -> mergedCookies.entries.joinToString("; ") { (k, v) -> "$k=$v" }
                mergedCookies.isEmpty() -> existingCookie
                else -> {
                    val extra = mergedCookies.entries
                        .filter { (k, _) -> !existingCookie.contains("$k=") }
                        .joinToString("; ") { (k, v) -> "$k=$v" }
                    if (extra.isNotEmpty()) "$existingCookie; $extra" else existingCookie
                }
            }
            if (!finalCookie.isNullOrBlank()) {
                req.header("Cookie", finalCookie)
            }
        }

        var c = client
        if (!allowRedirects) c = c.newBuilder().followRedirects(false).followSslRedirects(false).build()
        if (timeout > 0) c = c.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build()
        if (interceptor != null) c = c.newBuilder().addInterceptor(interceptor).build()

        val resp: Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val call = c.newCall(req.build())
            call.enqueue(object : okhttp3.Callback {
                override fun onResponse(call: okhttp3.Call, response: Response) { cont.resume(response) }
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { cont.resumeWithException(e) }
            })
            cont.invokeOnCancellation { call.cancel() }
        }

        Log.d("PlayCs", "[NiceHttp] $method $url -> HTTP ${resp.code}")

        // Global Cloudflare challenge kontrolü ve otomatik çözümleme (yalnızca 1 kez tekrar denenir)
        val peek = runCatching { resp.peekBody(4096).string() }.getOrNull()
        Log.d("PlayCs", "[NiceHttp] body peek (length=${peek?.length}): ${peek?.take(250)}")

        val isCfChallenge = CloudflareDetector.isChallenge(resp.code, resp.headers, peek)
        if (isCfChallenge) {
            Log.d("PlayCs", "[NiceHttp] Cloudflare challenge detected! host=$host, code=${resp.code}, isRetry=$isRetry, wasBlocked=${CloudflareSolver.wasBlocked(host)}")
        }

        if (!isRetry && isCfChallenge && !CloudflareSolver.wasBlocked(host)) {
            Log.d("PlayCs", "[NiceHttp] Challenge algılandı ($host, HTTP ${resp.code}), çözücü başlatılıyor...")
            val solveResult = CloudflareSolver.solve(url)
            if (solveResult is CloudflareResult.Success) {
                Log.d("PlayCs", "[NiceHttp] Başarıyla çözüldü ($host), istek tekrarlanıyor...")
                resp.close()
                return execute(
                    method = method,
                    url = url,
                    headers = headers,
                    referer = referer,
                    body = body,
                    cookies = cookies,
                    allowRedirects = allowRedirects,
                    timeout = timeout,
                    interceptor = interceptor,
                    isRetry = true,
                )
            } else {
                Log.w("PlayCs", "[NiceHttp] Çözülemedi ($host)")
            }
        } else if (isRetry && isCfChallenge) {
            // Çözücü "başarılı" dedi ama site hâlâ engel sayfası veriyor: elimizdeki çerez geçersiz
            // (çoğunlukla uygulama yeniden açılınca WebView deposunda kalan eski cf_clearance).
            // Eski çerezi her yerden sil ve bir kez gerçekten taze çöz; o da olmazsa kullanıcıya "Doğrula".
            Log.w("PlayCs", "[NiceHttp] Çerezle de engel sayfası geldi ($host); eski çerez siliniyor (freshTried=$freshTried)")
            CloudflareSolver.invalidate(host, url)
            if (!freshTried) {
                val fresh = CloudflareSolver.solve(url)
                if (fresh is CloudflareResult.Success) {
                    resp.close()
                    return execute(
                        method = method,
                        url = url,
                        headers = headers,
                        referer = referer,
                        body = body,
                        cookies = cookies,
                        allowRedirects = allowRedirects,
                        timeout = timeout,
                        interceptor = interceptor,
                        isRetry = true,
                        freshTried = true,
                    )
                }
            }
            CloudflareSolver.markBlocked(host, url)
        }

        if (isCfChallenge) CloudflareSolver.noteUnresolved(host, url)
        return NiceResponse(resp)
    }

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

/** `app.get()`/`app.post()` yanıtı. Eklentiler `.text`, `.document` (Jsoup) ve `.code` okur. */
class NiceResponse(private val resp: Response) : AutoCloseable {

    val url: String get() = resp.request.url.toString()
    val code: Int get() = resp.code
    val isSuccessful: Boolean get() = resp.isSuccessful
    val headers: Headers get() = resp.headers
    val cookies: Map<String, String> by lazy {
        headers.values("Set-Cookie").mapNotNull { raw ->
            val part = raw.substringBefore(';')
            val idx = part.indexOf('=')
            if (idx > 0) part.take(idx).trim() to part.substring(idx + 1).trim() else null
        }.toMap()
    }

    private val bodyBytes: ByteArray by lazy {
        try { resp.body?.bytes() ?: ByteArray(0) } finally { resp.close() }
    }
    private val charset: java.nio.charset.Charset by lazy {
        val headerCharset = runCatching {
            resp.body?.contentType()?.charset()
        }.getOrNull()
        if (headerCharset != null) return@lazy headerCharset

        val peek = String(bodyBytes.take(2048).toByteArray(), Charsets.ISO_8859_1)
        val match = Regex("""charset=["']?([a-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE).find(peek)
        val name = match?.groupValues?.get(1)
        if (name != null) {
            runCatching { java.nio.charset.Charset.forName(name) }.getOrNull() ?: Charsets.UTF_8
        } else {
            Charsets.UTF_8
        }
    }

    val text: String get() {
        val raw = try { bodyBytes.toString(charset) } catch (_: Exception) { bodyBytes.toString(Charsets.UTF_8) }
        return if (raw.contains("poster-article")) {
            raw.replace("poster-article", "poster-article article-series-poster detailed-article")
        } else {
            raw
        }
    }

    val document: Document get() {
        val doc = try {
            Jsoup.parse(java.io.ByteArrayInputStream(bodyBytes), charset.name(), url)
        } catch (_: Exception) {
            Jsoup.parse(text, url)
        }
        val posterArticles = doc.select("article.poster-article")
        if (posterArticles.isNotEmpty()) {
            posterArticles.addClass("article-series-poster detailed-article")
            for (art in posterArticles) {
                val a = art.selectFirst("a[href]")
                if (a != null && a.attr("title").isBlank()) {
                    val fallbackTitle = art.selectFirst(".archive, figcaption, img")?.let { el ->
                        el.attr("title").takeIf { it.isNotBlank() }
                            ?: el.attr("alt").takeIf { it.isNotBlank() }
                            ?: el.text().takeIf { it.isNotBlank() }
                    }
                    if (!fallbackTitle.isNullOrBlank()) {
                        a.attr("title", fallbackTitle.trim())
                    }
                }
            }
        }
        return doc
    }
    val parser: ResponseParser get() = DefaultResponseParser

    fun text(): String = text

    override fun close() { try { resp.close() } catch (_: Exception) {} }
    override fun toString(): String = text
}

/** Eklentiler bazen `response.parser.parseSafe<T>(...)` ile özel ayrıştırma yapar. */
interface ResponseParser {
    fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T?
}

object DefaultResponseParser : ResponseParser {
    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? =
        try {
            mapper.readValue(text, kClass.java)
        } catch (e: Exception) {
            Log.w("PlayCs", "parseSafe primary parser failed for ${kClass.java.name}: ${e.message}")
            tryFallback(text, kClass)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> tryFallback(text: String, kClass: KClass<T>): T? {
        return try {
            val root = mapper.readTree(text)
            val ctor = kClass.java.constructors.firstOrNull() ?: return null
            val paramTypes = ctor.parameterTypes
            if (paramTypes.size == 1 && paramTypes[0] == String::class.java) {
                val html = root.get("html")?.asText() ?: root.asText()
                ctor.newInstance(html) as? T
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e("PlayCs", "parseSafe fallback also failed for ${kClass.java.name}: ${e.message}")
            null
        }
    }
}
