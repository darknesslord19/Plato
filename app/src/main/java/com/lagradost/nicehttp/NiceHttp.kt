package com.lagradost.nicehttp

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
    ): NiceResponse {
        PlayNetworkGuard.checkUrl(url)

        val req = Request.Builder().url(url).method(method, body)
        headers.forEach { (k, v) -> req.addHeader(k, v) }
        referer?.let { req.addHeader("Referer", it) }
        if (!headers.containsKey("User-Agent")) req.addHeader("User-Agent", DEFAULT_UA)
        if (cookies.isNotEmpty()) req.addHeader("Cookie", cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" })

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
    val text: String get() = bodyBytes.toString(Charsets.UTF_8)
    val document: Document get() = Jsoup.parse(text, url)
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
        try { mapper.readValue(text, kClass.java) } catch (_: Exception) { null }
}
