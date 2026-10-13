package com.kaan.play.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Güncelleme indirmesi neden bitmedi: ağ/disk ([Reason.DOWNLOAD]) ya da özet doğrulanamadı ([Reason.CHECKSUM]). */
class UpdateException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason { DOWNLOAD, CHECKSUM }
}

/**
 * Seçilen APK'yı indirir ve release'teki SHA-256 dosyasıyla doğrular. Android'den bağımsızdır (birim testli).
 *
 * Her istek (yönlendirmeler dahil) yalnızca https + GitHub ana bilgisayarlarına gider ([UpdateApkRules.isSafeDownload]).
 * Dosya önce `.part` olarak yazılır, özet tutarsa adıyla yeniden adlandırılır: özeti tutmayan dosya hiçbir zaman
 * kurulacak yerde durmaz. Daha önce indirilmiş ve özeti tutan dosya yeniden indirilmez.
 */
class UpdateDownloader(client: OkHttpClient, private val debugHost: () -> String? = { null }) {

    private val client = client.newBuilder()
        .addNetworkInterceptor { chain ->
            if (!UpdateApkRules.isSafeDownload(chain.request().url, debugHost())) throw IOException("blocked host")
            chain.proceed(chain.request())
        }
        .build()

    /** [onProgress]: (indirilen bayt, toplam bayt; toplam bilinmiyorsa 0). Dönen dosya doğrulanmıştır. */
    suspend fun download(apk: UpdateApk, dir: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            try {
                run(apk, dir, onProgress)
            } catch (e: UpdateException) {
                throw e
            } catch (e: IOException) {
                throw UpdateException(UpdateException.Reason.DOWNLOAD, "download failed", e)
            } catch (e: SecurityException) {
                throw UpdateException(UpdateException.Reason.DOWNLOAD, "download failed", e)
            }
        }

    private suspend fun run(apk: UpdateApk, dir: File, onProgress: (Long, Long) -> Unit): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("no cache dir")
        val expected = fetchExpectedHash(apk)
        val target = File(dir, apk.name)
        if (target.isFile && sha256(target) == expected) return target

        // Eski/yarım dosyaları temizle (yalnızca bu klasör; yalnızca APK ve .part).
        dir.listFiles()?.forEach { if (it.name.endsWith(".apk") || it.name.endsWith(".part")) it.delete() }
        val part = File(dir, apk.name + ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val request = Request.Builder().url(apk.url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val total = response.body.contentLength().takeIf { it > 0 } ?: apk.size
                var done = 0L
                part.outputStream().use { out ->
                    val source = response.body.source()
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        done += n
                        if (done > UpdateApkRules.MAX_APK_BYTES) throw IOException("too large")
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                        onProgress(done, total)
                    }
                }
                if (total > 0 && done != total) throw IOException("truncated")
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw UpdateException(UpdateException.Reason.CHECKSUM, "checksum mismatch")
            if (!part.renameTo(target)) throw IOException("rename failed")
            return target
        } finally {
            part.delete() // başarıda zaten taşındı; hata/iptalde yarım dosya kalmaz
        }
    }

    private fun fetchExpectedHash(apk: UpdateApk): String {
        val request = Request.Builder().url(apk.sha256Url).header("User-Agent", USER_AGENT).build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val source = response.body.source()
            source.request(UpdateApkRules.MAX_HASH_FILE_BYTES + 1)
            if (source.buffer.size > UpdateApkRules.MAX_HASH_FILE_BYTES) throw IOException("hash file too large")
            source.readUtf8()
        }
        return UpdateApkRules.parseSha256(text, apk.name)
            ?: throw UpdateException(UpdateException.Reason.CHECKSUM, "no checksum for ${apk.name}")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val USER_AGENT = "Play-Android-UpdateDownload"
    }
}
