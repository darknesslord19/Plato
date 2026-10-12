package com.kaan.play.data.stream

import com.kaan.play.data.addon.Addon
import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Aktif addon'lara paralel `GET {base}/stream/{movie|series}/{id}.json` atar.
 *
 * - Manifest'e uymayan addon'a (tür / `idPrefixes`) istek gitmez ve sonuç da üretilmez.
 * - Her addon kendi zaman aşımıyla çalışır ([timeout], çağrının tamamı için); biri takılınca diğerleri beklemez,
 *   sonuçlar hazır oldukça akar. Hata alan addon [StreamResult.Failed] olarak bildirilir, yutulmaz.
 * - [addons]: istek anında sorgulanacak aktif addon'lar.
 */
class AddonStreamProvider(
    private val addons: suspend () -> List<Addon>,
    private val client: OkHttpClient,
    private val timeout: suspend () -> Duration = { 15.seconds },
    private val maxBytes: Long = MAX_BYTES,
) : StreamProvider {

    override fun streams(request: StreamRequest): Flow<StreamResult> = channelFlow {
        val contentId = request.contentId ?: return@channelFlow
        val type = request.type.addonType
        val limit = timeout()
        for (addon in addons()) {
            if (!addon.manifest.supportsStream(type, contentId)) continue
            launch { send(query(addon, type, contentId, limit)) }
        }
    }

    private suspend fun query(addon: Addon, type: String, contentId: String, limit: Duration): StreamResult {
        val source = StreamSource(addon.id, addon.manifest.name)
        val url = "${addon.baseUrl}/stream/$type/$contentId.json".toHttpUrlOrNull()
            ?: return StreamResult.Failed(source, FailureReason.Network)
        val http = client.newBuilder().callTimeout(limit.inWholeMilliseconds, TimeUnit.MILLISECONDS).build()
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()

        return try {
            StreamResult.Found(source, StreamParser.parse(source, download(http, request)))
        } catch (_: InvalidStreamResponse) {
            StreamResult.Failed(source, FailureReason.InvalidResponse)
        } catch (e: Failure) {
            StreamResult.Failed(source, e.reason)
        }
    }

    private suspend fun download(http: OkHttpClient, request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw Failure(FailureReason.Http)
                val body = response.body.source()
                body.request(maxBytes + 1)
                if (body.buffer.size > maxBytes) throw Failure(FailureReason.InvalidResponse)
                body.readUtf8()
            }
        } catch (_: InterruptedIOException) { // SocketTimeoutException da bunun alt türü
            throw Failure(FailureReason.Timeout)
        } catch (_: IOException) {
            throw Failure(FailureReason.Network)
        }
    }

    private class Failure(val reason: FailureReason) : Exception()

    companion object {
        /** Akış cevapları manifest'ten büyük olabilir; yine de makul bir üst sınır. */
        const val MAX_BYTES = 5L * 1024 * 1024
    }
}
