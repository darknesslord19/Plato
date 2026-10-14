package com.kaan.play.data.subtitle

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Altyazı sitelerinin (SubDL …) verdiği zip paketinden altyazı dosyasını çıkarır (yalnızca bellekte; diske zip yazılmaz).
 * `.srt`, `.vtt`, `.ass`, `.ssa` kabul edilir (bu sırayla tercih; `.sub`/`.idx` ayrıştırılamaz, atlanır). Zip bombasına karşı girdi
 * sayısı ve her girdinin açılmış boyutu sınırlıdır. Birden çok uygun dosya varsa arşivdeki ilki alınır.
 */
object SubtitleZip {
    private val EXTENSIONS = listOf("srt", "vtt", "ass", "ssa")
    private const val MAX_ENTRIES = 64

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2].toInt() == 3 && bytes[3].toInt() == 4

    /** Uygun girdinin açılmış baytları; yoksa, bozuksa ya da [maxEntryBytes]'ı aşıyorsa null. */
    fun extract(bytes: ByteArray, maxEntryBytes: Long): ByteArray? {
        val found = HashMap<String, ByteArray>() // uzantı -> ilk uygun dosya
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var count = 0
                while (count++ < MAX_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/').lowercase()
                    if (entry.name.startsWith("__MACOSX") || name.startsWith("._")) continue
                    val ext = name.substringAfterLast('.', "")
                    if (ext !in EXTENSIONS || ext in found) continue
                    val data = readLimited(zip, maxEntryBytes) ?: continue
                    found[ext] = data
                }
            }
        } catch (_: IOException) {
            // bozuk arşiv: o ana kadar bulunanlarla devam
        } catch (_: IllegalArgumentException) { // geçersiz girdi adı
        }
        return EXTENSIONS.firstNotNullOfOrNull { found[it] }
    }

    private fun readLimited(zip: ZipInputStream, limit: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
