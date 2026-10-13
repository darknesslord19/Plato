package com.lagradost.cloudstream3

import okhttp3.Response
import okhttp3.ResponseBody

/**
 * CloudStream'in HTTP yanıtı sarmalayıcısı. Eklentiler bu sınıfı `app.get()` / `app.post()` yanıtı olarak alır.
 */
class NiceResponse(private val resp: Response) : AutoCloseable {

    val url: String get() = resp.request.url.toString()
    val code: Int get() = resp.code
    val isSuccessful: Boolean get() = resp.isSuccessful
    val headers: Map<String, String> by lazy {
        resp.headers.toMultimap().entries.associate { (k, v) -> k to v.joinToString("; ") }
    }

    private val bodyBytes: ByteArray by lazy {
        try { resp.body?.bytes() ?: ByteArray(0) } finally { resp.close() }
    }
    val text: String get() = bodyBytes.toString(Charsets.UTF_8)
    val body: ResponseBody? get() = resp.body

    /** HTML/JSON ayrıştırma için; `text` ile aynı. */
    fun text(): String = text

    /** Yanıt metnini döner ya da null (başarısız istek). */
    fun textOrNull(): String? = if (isSuccessful) text else null

    override fun close() {
        try { resp.close() } catch (_: Exception) {}
    }

    override fun toString(): String = text
}
