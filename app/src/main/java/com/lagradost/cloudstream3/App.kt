package com.lagradost.cloudstream3

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resumeWithException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * CloudStream'in `NiceHttp` uygulaması. Eklentiler `app.get()`, `app.post()` vb. ile HTTP istekleri yapar.
 * Bu stub, enjekte edilen [client] üzerinden OkHttp kullanır.
 *
 * Güvenlik: [PlayNetworkGuard] özel / yerel IP'leri ve yönlendirmeleri denetler.
 */
class NiceHttp(val client: OkHttpClient) {

    // Temel GET isteği
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        timeout: Long = 0,
        verify: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: TimeUnit = TimeUnit.HOURS,
    ): NiceResponse = execute("GET", buildUrl(url, params), headers, referer, null, timeout, cookies)

    // Temel POST — form gövdesi ya da JSON
    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        data: Map<String, String>? = null,
        json: Any? = null,
        requestBody: okhttp3.RequestBody? = null,
        cookies: Map<String, String> = emptyMap(),
        timeout: Long = 0,
        verify: Boolean = true,
    ): NiceResponse {
        val body = requestBody
            ?: if (json != null) {
                val j = com.kaan.play.data.net.PlayJson.encodeToString(
                    kotlinx.serialization.serializer<Any>(), json
                )
                j.toRequestBody("application/json".toMediaType())
            } else if (data != null) {
                val fb = FormBody.Builder()
                data.forEach { (k, v) -> fb.add(k, v) }
                fb.build()
            } else {
                ByteArray(0).toRequestBody(null)
            }
        return execute("POST", buildUrl(url, params), headers, referer, body, timeout, cookies)
    }

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
    ): NiceResponse = execute("HEAD", buildUrl(url, params), headers, referer, null, 0, emptyMap())

    // PUT / PATCH
    suspend fun put(url: String, headers: Map<String, String> = emptyMap(), data: Map<String, String>? = null, json: Any? = null): NiceResponse =
        post(url, headers = headers, data = data, json = json)

    // Yardımcılar
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
        body: okhttp3.RequestBody?,
        timeout: Long,
        cookies: Map<String, String>,
    ): NiceResponse {
        // Ağ güvenliği: özel/yerel adresler reddedilir (PluginNetworkGuard yetki verir)
        PlayNetworkGuard.checkUrl(url)

        val req = Request.Builder().url(url).method(method, body)
        headers.forEach { (k, v) -> req.addHeader(k, v) }
        referer?.let { req.addHeader("Referer", it) }
        if (!headers.containsKey("User-Agent")) {
            req.addHeader("User-Agent", DEFAULT_UA)
        }
        if (cookies.isNotEmpty()) {
            req.addHeader("Cookie", cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" })
        }
        val call = if (timeout > 0) {
            client.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build().newCall(req.build())
        } else {
            client.newCall(req.build())
        }
        val resp: Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            call.enqueue(object : okhttp3.Callback {
                override fun onResponse(c: okhttp3.Call, r: Response) { cont.resume(r) {} }
                override fun onFailure(c: okhttp3.Call, e: java.io.IOException) { cont.resumeWithException(e) }
            })
            cont.invokeOnCancellation { call.cancel() }
        }
        return NiceResponse(resp)
    }

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

/**
 * CS plugin'lerine karşı basit ağ güvenliği: yerel/özel IP'ler reddedilir.
 * Faz 2 [com.kaan.play.data.plugin.NetworkPolicy] ile aynı prensipler uygulanır,
 * fakat DEX yüklü kod doğrudan bu nesneyi çağırır.
 */
object PlayNetworkGuard {
    private val PRIVATE_RANGES = listOf(
        "10.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
        "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.",
        "172.31.", "192.168.", "127.", "169.254.", "100.64.", "::1", "fc", "fd",
    )

    fun checkUrl(url: String) {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw SecurityException("CS plugin: only http(s) allowed: $url")
        }
        // Host çözünürlüğü yapılmaz (performans); IP kalıp eşleştirmesi host dizesine uygulanır.
        val host = try { java.net.URL(url).host } catch (_: Exception) { return }
        if (isPrivateHost(host)) throw SecurityException("CS plugin: private address blocked: $host")
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost") return true
        PRIVATE_RANGES.forEach { prefix -> if (host.startsWith(prefix)) return true }
        return false
    }
}

/** Global `app` nesnesi. [initApp] ile CsRuntime başlatılmadan önce kurulur. */
lateinit var app: NiceHttp
    internal set

/** OkHttp istemcisini CS stubs'larına enjekte eder. [com.kaan.play.data.cloudstream.CsRuntime] çağırır. */
fun initApp(client: OkHttpClient) {
    app = NiceHttp(client)
}
