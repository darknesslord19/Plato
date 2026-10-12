package com.kaan.play.data.addon

import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Manifest metnini indirir. Arayüz olarak ayrı: testlerde sahte uygulanır. */
fun interface ManifestFetcher {
    /** @throws AddonException ağ/HTTP/boyut hatalarında. */
    suspend fun fetch(url: String): String
}

class OkHttpManifestFetcher(
    client: OkHttpClient,
    timeoutSeconds: Long = 15,
    private val maxBytes: Long = MAX_BYTES,
) : ManifestFetcher {

    // Tüm çağrı (bağlanma + okuma) için tek üst sınır: takılan addon ekleme ekranını kilitlemesin.
    private val client = client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()

    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw AddonException(Kind.Http, response.code.toString())
                val source = response.body.source()
                source.request(maxBytes + 1)
                if (source.buffer.size > maxBytes) throw AddonException(Kind.TooLarge)
                source.readUtf8()
            }
        } catch (e: IOException) {
            throw AddonException(Kind.Network, cause = e)
        }
    }

    companion object {
        /** Gerçek manifest'ler birkaç KB'tır; 2 MB çok cömert bir üst sınırdır. */
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}
