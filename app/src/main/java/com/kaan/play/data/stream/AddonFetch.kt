package com.kaan.play.data.stream

import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Addon cevabı alınamadı; [reason] "yanıt vermedi" bilgisinin nedenidir. */
internal class AddonFetchFailure(val reason: FailureReason) : Exception()

/**
 * Addon'a tek GET atar: çağrının tamamı için [limit] zaman aşımı, cevap en çok [maxBytes] bayt. Akış ve altyazı
 * sağlayıcıları aynı kuralı paylaşır. Hata [AddonFetchFailure] olarak atılır (zaman aşımı / ağ / HTTP / çok büyük).
 */
internal suspend fun fetchAddonText(client: OkHttpClient, url: okhttp3.HttpUrl, limit: Duration, maxBytes: Long): String {
    val http = client.newBuilder().callTimeout(limit.inWholeMilliseconds, TimeUnit.MILLISECONDS).build()
    val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
    return withContext(Dispatchers.IO) {
        try {
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw AddonFetchFailure(FailureReason.Http)
                val body = response.body.source()
                body.request(maxBytes + 1)
                if (body.buffer.size > maxBytes) throw AddonFetchFailure(FailureReason.InvalidResponse)
                body.readUtf8()
            }
        } catch (_: InterruptedIOException) { // SocketTimeoutException da bunun alt türü
            throw AddonFetchFailure(FailureReason.Timeout)
        } catch (_: IOException) {
            throw AddonFetchFailure(FailureReason.Network)
        }
    }
}
