package com.kaan.play.data.download

import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.SubtitleParser
import com.kaan.play.data.subtitle.SubtitleTrack
import com.kaan.play.data.subtitle.TrackLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Çevrimdışı oynatmanın altyazı yükleyicisi: parçanın özgün adresine karşılık gelen yerel UTF-8 dosyasını okuyup aynı
 * ayrıştırıcıdan ([SubtitleParser]) geçirir. İnternete hiç gitmez; dosyası olmayan parça "yüklenemedi" olur.
 * Adres, parçanın kimliği olarak aynen kalır; böylece senkron kaydırması çevrimiçi/çevrimdışı ortaktır.
 */
class DownloadedSubtitleLoader(private val files: Map<String, String>) : TrackLoader {

    override suspend fun load(track: SubtitleTrack): SubtitleLoad = withContext(Dispatchers.IO) {
        val path = files[track.url] ?: return@withContext SubtitleLoad.Failed(SubtitleLoad.Reason.Network)
        val text = try {
            File(path).readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return@withContext SubtitleLoad.Failed(SubtitleLoad.Reason.Network)
        }
        val cues = SubtitleParser.parse(text)
        if (cues.isEmpty()) SubtitleLoad.Failed(SubtitleLoad.Reason.Invalid) else SubtitleLoad.Loaded(cues, "UTF-8", fromCache = true)
    }
}
