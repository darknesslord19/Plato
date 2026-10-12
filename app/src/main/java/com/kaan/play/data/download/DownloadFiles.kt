package com.kaan.play.data.download

import android.content.Context
import java.io.File

/**
 * İndirmelerin diskteki yeri. Uygulamaya özel harici depolama (`Android/data/<paket>/files/downloads`): izin gerektirmez,
 * uygulama silinince kalkar. Harici alan yoksa (nadir) uygulamanın dahili dizinine düşer.
 *
 * - `cache/`: Media3 `SimpleCache` (videonun kendisi; bölünmüş akışlarda liste + parçalar).
 * - `subs/`: indirme sırasında saklanan altyazılar, UTF-8 metin (`{indirmeKimliği}-{sıra}.txt`).
 */
class DownloadFiles(val root: File) {
    val cacheDir: File = File(root, "cache")
    private val subsDir: File = File(root, "subs")

    fun subtitleFile(downloadId: Long, index: Int): File = File(subsDir, "$downloadId-$index.txt")

    fun writeSubtitle(downloadId: Long, index: Int, utf8Text: String): File {
        subsDir.mkdirs()
        val file = subtitleFile(downloadId, index)
        val temp = File(subsDir, file.name + ".tmp")
        temp.writeText(utf8Text, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw java.io.IOException("Altyazı yazılamadı: ${file.name}")
        }
        return file
    }

    /** Bir indirmenin tüm altyazı dosyalarını siler. */
    fun deleteSubtitles(downloadId: Long) {
        subsDir.listFiles { f -> f.name.startsWith("$downloadId-") }?.forEach { it.delete() }
    }

    fun deleteAllSubtitles() {
        subsDir.listFiles()?.forEach { it.delete() }
    }

    /** Kök dizinin bulunduğu bölümde kullanılabilir boş alan (bayt). Dizin henüz yoksa ilk var olan üst dizine bakılır. */
    fun freeBytes(): Long {
        var dir: File? = root
        while (dir != null && !dir.exists()) dir = dir.parentFile
        return dir?.usableSpace ?: 0L
    }

    companion object {
        fun forContext(context: Context): DownloadFiles {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return DownloadFiles(File(base, "downloads").also { it.mkdirs() })
        }
    }
}
