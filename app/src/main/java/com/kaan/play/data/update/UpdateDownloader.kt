package com.kaan.play.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
 *
 * **Kaldığı yerden devam:** ağ kesilir ya da süreç ölürse `.part` dosyası saklanır; sonraki denemede `Range: bytes=N-` ile
 * yalnızca kalan kısım istenir (var olan baytlar özete yeniden katılır). Sunucu Range'i yok sayarsa (200) ya da tutarsız yanıt
 * verirse (416, yanlış `Content-Range`) baştan başlanır. Devam edilen dosyanın özeti tutmazsa bir kez baştan denenir; özet yine
 * tutmazsa dosya silinir ve [UpdateException.Reason.CHECKSUM] fırlatılır.
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

    private sealed interface Transfer {
        data class Done(val sha256: String) : Transfer

        /** Devam edilemedi (sunucu Range'i uygulamadı); `.part` silinip baştan başlanmalı. */
        data object Restart : Transfer
    }

    private suspend fun run(apk: UpdateApk, dir: File, onProgress: (Long, Long) -> Unit): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("no cache dir")
        val expected = fetchExpectedHash(apk)
        val target = File(dir, apk.name)
        if (target.isFile && sha256(target) == expected) return target

        // Başka sürüme/APK'ya ait eski dosyalar ve bu APK'nın özeti tutmayan kopyası temizlenir (yalnızca bu klasör;
        // yalnızca APK ve .part). Bu APK'nın .part dosyası kaldığı yerden devam için korunur.
        val part = File(dir, apk.name + ".part")
        dir.listFiles()?.forEach { if ((it.name.endsWith(".apk") || it.name.endsWith(".part")) && it != part) it.delete() }
        target.delete()

        var restarted = false
        while (true) {
            val resumeFrom = usablePartLength(part, apk)
            when (val result = transfer(apk, part, resumeFrom, onProgress)) {
                is Transfer.Done -> {
                    if (result.sha256 == expected) {
                        if (!part.renameTo(target)) throw IOException("rename failed")
                        return target
                    }
                    part.delete() // özeti tutmayan dosya hiçbir zaman kalmaz
                    if (resumeFrom > 0 && !restarted) {
                        restarted = true // devam edilen kısım bozuk olabilir: bir kez baştan
                        continue
                    }
                    throw UpdateException(UpdateException.Reason.CHECKSUM, "checksum mismatch")
                }
                Transfer.Restart -> {
                    part.delete()
                    if (restarted) throw IOException("range not honoured")
                    restarted = true
                }
            }
        }
    }

    /** Devam edilebilecek `.part` uzunluğu; dosya yok, boş ya da beklenen boyuta ulaşmış/aşmışsa (bozuk) silinir ve 0 döner. */
    private fun usablePartLength(part: File, apk: UpdateApk): Long {
        if (!part.isFile) return 0
        val length = part.length()
        if (length <= 0 || (apk.size > 0 && length >= apk.size)) {
            part.delete()
            return 0
        }
        return length
    }

    /** [resumeFrom] > 0 ise `.part` sonuna eklenir; değilse sıfırdan yazılır. Yarım kalırsa `.part` saklanır. */
    private suspend fun transfer(
        apk: UpdateApk,
        part: File,
        resumeFrom: Long,
        onProgress: (Long, Long) -> Unit,
    ): Transfer {
        val builder = Request.Builder().url(apk.url).header("User-Agent", USER_AGENT)
        if (resumeFrom > 0) builder.header("Range", "bytes=$resumeFrom-")
        return client.newCall(builder.build()).execute().use { response ->
            var offset = resumeFrom
            val append: Boolean
            val total: Long
            when (response.code) {
                206 -> {
                    val range = ContentRange.parse(response.header("Content-Range"))
                    if (resumeFrom == 0L || range == null || range.start != resumeFrom) return Transfer.Restart
                    append = true
                    total = range.total ?: apk.size
                }
                200 -> {
                    // Sunucu Range'i yok saydı (ya da bu ilk indirme): baştan.
                    offset = 0
                    append = false
                    total = response.body.contentLength().takeIf { it > 0 } ?: apk.size
                }
                416 -> return Transfer.Restart
                else -> throw IOException("HTTP ${response.code}")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            if (append) {
                part.inputStream().use { existing -> feed(existing, digest, limit = offset) }
            }
            var done = offset
            FileOutputStream(part, append).use { out ->
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
            if (total > 0 && done != total) throw IOException("truncated") // .part saklanır: sonraki deneme devam eder
            Transfer.Done(digest.digest().toHex())
        }
    }

    private fun feed(input: java.io.InputStream, digest: MessageDigest, limit: Long) {
        val buffer = ByteArray(BUFFER)
        var left = limit
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) throw IOException("part shorter than expected")
            digest.update(buffer, 0, n)
            left -= n
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
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** `Content-Range: bytes 100-199/200` (toplam `*` olabilir). */
    internal data class ContentRange(val start: Long, val end: Long, val total: Long?) {
        companion object {
            fun parse(header: String?): ContentRange? {
                val match = Regex("""^\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*$""", RegexOption.IGNORE_CASE).matchEntire(header ?: return null)
                    ?: return null
                val (start, end, total) = match.destructured
                return ContentRange(start.toLong(), end.toLong(), total.toLongOrNull())
            }
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val USER_AGENT = "Play-Android-UpdateDownload"
    }
}
