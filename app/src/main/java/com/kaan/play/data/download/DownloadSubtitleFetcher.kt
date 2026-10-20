package com.kaan.play.data.download

import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.data.subtitle.SubtitleLoader
import com.kaan.play.data.subtitle.SubtitleMemory
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.data.subtitle.SubtitleTextResult
import com.kaan.play.data.subtitle.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * İndirme sırasında altyazıları da saklar ki çevrimdışı oynatmada menüde görünsün. Hangi altyazılar? Varsayılan kurallar:
 * uygulama dili ve İngilizce, bir de kullanıcı o film/dizide bir dil seçtiyse (kapatmadıysa) o. Her dilden, aday sırasıyla
 * (önce akışın/eklentinin kendi altyazısı, sonra altyazı addon'ları) yüklenebilen **ilk** altyazı alınır. Metin UTF-8'e
 * çevrilmiş hâliyle saklanır ([SubtitleLoader.loadText]); çevrimdışı oynatma aynı ayrıştırıcıyı kullanır.
 *
 * Videonun içine gömülü altyazılar (mkv) zaten dosyanın içindedir, ayrıca indirilmez. Tamamen en iyi çabadır: altyazı
 * bulunamaz/indirilemezse video yine indirilir.
 */
class DownloadSubtitleFetcher(
    private val loader: SubtitleLoader,
    private val provider: SubtitleProvider,
    private val memory: SubtitleMemory,
    private val appLanguage: suspend () -> String,
    private val files: DownloadFiles,
) {

    suspend fun fetch(downloadId: Long, request: StreamRequest, stream: PlayableStream): List<DownloadSubtitle> {
        val languages = languages(memory.language(request), appLanguage())
        val candidates = ArrayList<SubtitleTrack>()
        stream.subtitles.mapTo(candidates) {
            SubtitleTrack(it.url, SubtitleLanguages.normalize(it.language), stream.source.addonName, headers = it.headers)
        }
        // Akışın kendi altyazıları istenen dilleri karşılıyorsa addon'lara sormaya gerek yok (zaman aşımı beklenmez).
        if (!covers(candidates, languages)) {
            provider.subtitles(request).collect { result ->
                if (result is SubtitleResult.Found) candidates += result.tracks
            }
        }
        return pick(downloadId, candidates, languages)
    }

    private suspend fun pick(downloadId: Long, candidates: List<SubtitleTrack>, languages: List<String>): List<DownloadSubtitle> {
        val saved = ArrayList<DownloadSubtitle>()
        for (language in languages) {
            val ofLanguage = candidates.filter { it.language == language }.distinctBy { it.url }.take(MAX_TRIES_PER_LANGUAGE)
            for (track in ofLanguage) {
                val text = (loader.loadText(track) as? SubtitleTextResult.Text)?.utf8 ?: continue
                val file = try {
                    withContext(Dispatchers.IO) { files.writeSubtitle(downloadId, saved.size, text) }
                } catch (_: IOException) {
                    continue
                }
                saved += DownloadSubtitle(track.url, track.language, track.origin, file.path)
                break
            }
        }
        return saved
    }

    companion object {
        /** Bir dilde bu kadar aday denenir (ilk yüklenebilen alınır). */
        const val MAX_TRIES_PER_LANGUAGE = 3

        /**
         * İndirilecek diller, sırayla: kullanıcının bu içerikte seçtiği (kapalı demek diller dışı bırakmaz, yalnızca ek dil
         * eklemez), uygulama dili, İngilizce. Yinelenenler atılır.
         */
        fun languages(remembered: String?, appLanguage: String): List<String> =
            listOfNotNull(remembered?.takeIf { it != SubtitleSelection.REMEMBERED_OFF }, appLanguage, "en").distinct()

        private fun covers(candidates: List<SubtitleTrack>, languages: List<String>): Boolean =
            languages.all { language -> candidates.any { it.language == language } }
    }
}
