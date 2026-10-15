package com.kaan.play.data.subtitle.translate

import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.SubtitleParser
import com.kaan.play.data.subtitle.SubtitleTrack
import com.kaan.play.data.subtitle.TrackLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Çevrilmiş altyazıların önbelleği: (içerik + kaynak altyazı adresi) başına `tr-<özet>.srt` (UTF-8). Aynı film tekrar açılınca
 * yeniden çevrilmez. Dizin [MAX_FILES] dosya ve [MAX_AGE_MS] yaşla budanır. [dir] uygulamanın `cacheDir/subtitles` dizinidir
 * (sistem boşaltabilir; boşaltılırsa yeniden çevrilir).
 */
class TranslatedSubtitleStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Çevrilmiş parçanın menüdeki kimliği (dili Türkçe). Özgün altyazı adresi parça adresine girmez, özetlenir. */
    fun trackFor(request: StreamRequest, source: SubtitleTrack): SubtitleTrack =
        SubtitleTrack(
            url = SCHEME + hash(request, source.url),
            language = SubtitleTranslation.TARGET,
            origin = source.origin,
            kind = SubtitleKind.Translated,
        )

    /** Önbellekte çevrilmiş kopyası olan parça; yoksa null. */
    fun cached(request: StreamRequest, source: SubtitleTrack): SubtitleTrack? =
        trackFor(request, source).takeIf { read(it) != null }

    /** Yazılamazsa false (çeviri bu oturumda da kullanılamaz: okuma dosyadan yapılır). */
    fun write(track: SubtitleTrack, cues: List<SubtitleCue>): Boolean {
        val file = fileOf(track) ?: return false
        return try {
            dir.mkdirs()
            val temp = File(dir, file.name + ".tmp")
            temp.writeText(SubtitleSrt.write(cues), Charsets.UTF_8)
            val ok = temp.renameTo(file)
            if (!ok) temp.delete()
            prune()
            ok
        } catch (_: IOException) {
            false
        }
    }

    fun read(track: SubtitleTrack): List<SubtitleCue>? {
        val file = fileOf(track)?.takeIf { it.isFile } ?: return null
        return try {
            SubtitleParser.parse(file.readText(Charsets.UTF_8)).takeIf { it.isNotEmpty() }?.also { file.setLastModified(clock()) }
        } catch (_: IOException) {
            null
        }
    }

    private fun fileOf(track: SubtitleTrack): File? {
        if (track.kind != SubtitleKind.Translated || !track.url.startsWith(SCHEME)) return null
        val id = track.url.removePrefix(SCHEME)
        return if (id.isNotEmpty() && id.all { it.isDigit() || it in 'a'..'f' }) File(dir, "tr-$id.srt") else null
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("tr-") && f.name.endsWith(".srt") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.forEachIndexed { i, f -> if (i >= MAX_FILES || clock() - f.lastModified() > MAX_AGE_MS) f.delete() }
    }

    /** Çevrilmiş parçaları önbellekten okuyan yükleyici; diğer parçaları [inner]'a bırakır. */
    fun loader(inner: TrackLoader): TrackLoader = TrackLoader { track ->
        if (track.kind != SubtitleKind.Translated) {
            inner.load(track)
        } else {
            val cues = withContext(io) { read(track) }
            cues?.let { SubtitleLoad.Loaded(it, "UTF-8", fromCache = true) } ?: SubtitleLoad.Failed(SubtitleLoad.Reason.Invalid)
        }
    }

    companion object {
        const val SCHEME = "translated:"
        const val MAX_FILES = 100
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        internal fun hash(request: StreamRequest, sourceUrl: String): String {
            val key = "${request.type}:${request.tmdbId}:${request.season}:${request.episode}|$sourceUrl"
            return MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
        }
    }
}
