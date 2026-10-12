package com.kaan.play.ui.player

import android.content.Context
import android.content.Intent
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.download.DownloadCodec
import com.kaan.play.data.download.toTarget
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import kotlinx.serialization.Serializable

/**
 * İndirilmiş (çevrimdışı) bir oynatma. [downloadId]: Media3 indirme kimliği (oynatıcı öğeyi buradan kurar);
 * [subtitleFiles]: indirme sırasında saklanan altyazılar, özgün adres -> yerel UTF-8 dosyası.
 */
@Serializable
data class OfflineSession(val downloadId: Long, val subtitleFiles: Map<String, String>)

/** Oynatıcı etkinliğine giden her şey: ne oynanıyor, hangi kaynakla, nereden başlanacak. */
@Serializable
data class PlaySession(
    val target: PlayTarget,
    val stream: PlayableStream,
    val startPositionMs: Long,
    /** Kayıtlı konumdan sormadan devam ediliyorsa true: oynatıcı kısa bir "Baştan başla" bildirimi gösterir. */
    val autoResumed: Boolean = false,
    /** Doluysa video internetten değil, indirilmiş kopyadan oynar ve kaynak değiştirilemez. */
    val offline: OfflineSession? = null,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, PlayerActivity::class.java).putExtra(EXTRA, PlayJson.encodeToString(serializer(), this))

    companion object {
        private const val EXTRA = "session"

        /**
         * İndirilmiş kopyadan oturum. Hedef çevrimiçi oynatmayla birebir aynıdır (aynı `watch_history` satırı: kaldığı yerden
         * devam ve geçmiş ortak); akışın altyazıları indirilenler olur, böylece altyazı menüsü ağsız dolar.
         */
        fun offline(download: DownloadEntity, startPositionMs: Long, autoResumed: Boolean): PlaySession {
            val subtitles = DownloadCodec.decodeSubtitles(download.subtitlesJson)
            return PlaySession(
                target = download.toTarget(),
                stream = PlayableStream(
                    source = StreamSource(addonId = 0, addonName = download.sourceName),
                    url = download.sourceUrl,
                    resolution = null,
                    sizeBytes = download.totalBytes.takeIf { it > 0 },
                    headers = emptyMap(),
                    subtitles = subtitles.map { StreamSubtitle(it.url, it.language) },
                ),
                startPositionMs = startPositionMs,
                autoResumed = autoResumed,
                offline = OfflineSession(download.id, subtitles.associate { it.url to it.file }),
            )
        }

        /** Bozuk/eksik veri null döner (etkinlik kendini kapatır). */
        fun from(intent: Intent): PlaySession? {
            val text = intent.getStringExtra(EXTRA) ?: return null
            return try {
                PlayJson.decodeFromString(serializer(), text)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
