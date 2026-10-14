package com.kaan.play.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import com.kaan.play.data.torrent.TorrentDiag
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamLog
import com.kaan.play.data.stream.TorrentLinks
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Oynatma hatasının kullanıcıya gösterilen üç sınıfı. */
enum class PlaybackError { Network, Unavailable, Format }

/** Arayüzün okuduğu anlık durum (Compose anlık görüntü durumu). */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val buffering: Boolean = true,
    val ended: Boolean = false,
    val error: PlaybackError? = null,
    /** Altyazı parçaları değişince artar; seçenek listesi buna göre yeniden okunur. */
    val tracksVersion: Int = 0,
)

/** Videonun içindeki (Media3'ün okuduğu) altyazı parçası; [index] parça listesindeki sıradır. */
data class EmbeddedSubtitle(val index: Int, val language: String?, val selected: Boolean, val label: String? = null)

/** Videonun ses parçası; [index] ses parçaları listesindeki sıradır, [label] parçanın kendi adı (yoksa null). */
data class AudioTrack(val index: Int, val language: String?, val label: String?, val selected: Boolean)

/**
 * Media3 ExoPlayer'ı sarar: kaynağı yükler (proxyHeaders), durumu Compose'a açar. Başlıklar yalnızca akış isteklerine eklenir.
 * Harici altyazılar (addon / eklenti / akış adresleri) Media3'e verilmez: [SubtitleController] indirir, ayrıştırır ve satırları
 * kendisi gösterir (kaydırma anında uygulansın diye). Burada yalnızca videonun kendi altyazı parçaları seçilir.
 */
@OptIn(UnstableApi::class)
class PlaybackController(
    context: Context,
    client: OkHttpClient,
    offlineCache: Cache? = null,
    /** `magnet:` kaynağın yerleşik torrent motorundaki yerel http adresi (magnet, dosya sırası). */
    private val torrentUrl: (String, Int?) -> String = { _, _ -> throw IllegalStateException("no torrent engine") },
) : SubtitleHost {

    @Volatile private var headers: Map<String, String> = emptyMap()
    @Volatile private var torrent = false

    // Yerel torrent sunucusu üst bilgi ve ilk parçalar gelene dek yanıtı bekletir: okuma zaman aşımı uzun tutulur.
    private val torrentSource = OkHttpDataSource.Factory(client.newBuilder().readTimeout(TORRENT_READ_TIMEOUT_S, TimeUnit.SECONDS).build())
    private val onlineSource = ResolvingDataSource.Factory(OkHttpDataSource.Factory(client)) { spec ->
        if (headers.isEmpty()) spec else spec.withAdditionalHeaders(headers)
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                if (offlineCache != null) {
                    // İndirilmiş kopya: yalnızca önbellekten okunur (üst akış yok); internet hiç kullanılmaz.
                    CacheDataSource.Factory().setCache(offlineCache).setCacheWriteDataSinkFactory(null)
                } else {
                    DataSource.Factory { if (torrent) torrentSource.createDataSource() else onlineSource.createDataSource() }
                },
            ),
        )
        .build()
        .also { exo ->
            // Altyazı varsayılan olarak kapalıdır; seçilince açılır (videonun içindekiler Media3'le, diğerleri kendi katmanımızla).
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

    var ui by mutableStateOf(PlayerUiState())
        private set

    /**
     * Kaynak açılamayınca hata ekranından önce çağrılır; true dönerse (ör. yedek kaynağa geçildi) hata gösterilmez.
     * Yalnızca otomatik sonraki bölümde kullanılır.
     */
    var onFailure: (() -> Boolean)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui = ui.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                ui = ui.copy(buffering = state == Player.STATE_BUFFERING, ended = state == Player.STATE_ENDED)
            }

            override fun onPlayerError(error: PlaybackException) {
                val http = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                TorrentDiag.e(
                    "ExoPlayer hata: ${error.errorCodeName} (${error.errorCode}) → ${errorOf(error)}" +
                        (http?.let { " http=${it.responseCode} ctype=${it.headerFields["Content-Type"]}" } ?: ""),
                    error,
                )
                // Eklentinin verdiği başlıklar (Referer / tarayıcı User-Agent) bazı CDN'lerde tam tersine reddedilir
                // (ör. 428/429). Başlıklı istek 4xx alırsa aynı konumdan bir kez başlıksız denenir.
                if (retryWithoutHeaders(http?.responseCode, headersSent = headers.isNotEmpty())) {
                    TorrentDiag.d("başlıklı istek ${http?.responseCode} aldı: başlıksız yeniden deneniyor")
                    headers = emptyMap()
                    player.prepare()
                    return
                }
                if (onFailure?.invoke() == true) return
                ui = ui.copy(error = errorOf(error), buffering = false)
            }

            override fun onTracksChanged(tracks: Tracks) {
                ui = ui.copy(tracksVersion = ui.tracksVersion + 1)
            }
        })
    }

    /** [startMs] konumundan oynatmaya başlar. Önceki hata temizlenir. */
    fun load(stream: PlayableStream, startMs: Long) {
        headers = stream.headers
        torrent = TorrentLinks.isMagnet(stream.url)
        // Magnet: yerleşik motorun yerel adresi; tür içerikten çıkarılır (dosya adı üst bilgi gelince belli olur).
        val uri = if (torrent) torrentUrl(stream.url, stream.torrentFileIdx) else stream.url

        val mime = if (torrent) null else stream.mimeType ?: streamMimeType(stream.url)
        StreamLog.opening(stream.url, mime)
        // Yalnızca şema/host/uzantı: tam adres, magnet ve yerel sunucu belirteci loglanmaz.
        TorrentDiag.d(
            "oynatıcıya veriliyor: kaynak=${stream.source.addonName} ${StreamLog.describe(stream.url)} torrent=$torrent " +
                "fileIdx=${stream.torrentFileIdx} başlıklar=${stream.headers.keys} mime=$mime",
        )
        val item = MediaItem.Builder()
            .setUri(uri)
            .apply { mime?.let(::setMimeType) }
            .build()

        ui = ui.copy(error = null, ended = false, buffering = true)
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** İndirilmiş kopyayı oynatır ([item] indirme isteğinden gelir; HLS/DASH'te indirilen kaliteyle sınırlıdır). */
    fun loadDownloaded(item: MediaItem, startMs: Long) {
        ui = ui.copy(error = null, ended = false, buffering = true)
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** İndirilen kopya bulunamadı (silinmiş/bozuk): oynatıcı hata ekranını gösterir. */
    fun markUnavailable() {
        ui = ui.copy(error = PlaybackError.Unavailable, buffering = false)
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0L))

    override fun positionMs(): Long = player.currentPosition

    /** Süre bilinmiyorsa 0. */
    fun durationMs(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    // ---- altyazı ----

    private fun textGroups(): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }

    /** Videonun kendi altyazı parçaları (dil etiketi parçanın söylediğidir, çoğu zaman `en`/`tr` gibi kısa kod). */
    override fun embeddedSubtitles(): List<EmbeddedSubtitle> {
        val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        return textGroups().mapIndexed { i, group ->
            val format = group.mediaTrackGroup.getFormat(0)
            EmbeddedSubtitle(i, format.language, selected = !textOff && group.isSelected, label = format.label)
        }
    }

    private fun audioGroups(): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }

    /** Videonun ses parçaları (dil etiketiyle; altyazı menüsünde birden çoksa seçilir). */
    fun audioTracks(): List<AudioTrack> = audioGroups().mapIndexed { i, group ->
        val format = group.mediaTrackGroup.getFormat(0)
        AudioTrack(i, format.language, format.label, group.isSelected)
    }

    fun selectAudio(index: Int) {
        val group = audioGroups().getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    /** Videonun içindeki parçayı açar (Media3 çizer). */
    override fun selectEmbeddedSubtitle(index: Int) {
        val group = textGroups().getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    /** Media3'ün altyazı çizimini kapatır (altyazı kapalıyken ya da harici altyazıyı biz çizerken). */
    override fun disableEmbeddedSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun release() = player.release()

    companion object {
        private const val TORRENT_READ_TIMEOUT_S = 120L

        /** Kaynağın başlıklarıyla gönderilen istek 4xx aldıysa başlıksız bir kez daha denenir (başlıksız istek yeniden denenmez). */
        internal fun retryWithoutHeaders(responseCode: Int?, headersSent: Boolean): Boolean =
            headersSent && responseCode != null && responseCode in 400..499

        /** `https://x/y.m3u8?token` gibi adreslerde ya da uzantısız HLS/DASH'te Media3 türü kendi çıkaramaz. */
        internal fun streamMimeType(url: String): String? {
            val lower = url.lowercase(Locale.ROOT)
            return when {
                ".m3u8" in lower -> MimeTypes.APPLICATION_M3U8
                ".mpd" in lower -> MimeTypes.APPLICATION_MPD
                else -> fileMimeType(lower)
            }
        }

        /** Sorgu/parça atıldıktan sonra yolun uzantısına göre düz dosya türü; uzantı yoksa/bilinmiyorsa null (Media3 içerikten çıkarır). */
        private fun fileMimeType(lowerUrl: String): String? {
            val path = lowerUrl.substringBefore('#').substringBefore('?')
            return when (path.substringAfterLast('/').substringAfterLast('.', "")) {
                "mkv" -> MimeTypes.VIDEO_MATROSKA
                "webm" -> MimeTypes.VIDEO_WEBM
                "mp4", "m4v" -> MimeTypes.VIDEO_MP4
                "avi" -> MimeTypes.VIDEO_AVI
                else -> null
            }
        }

        internal fun errorOf(e: PlaybackException): PlaybackError = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.Network

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackError.Unavailable

            else -> PlaybackError.Format
        }
    }
}
