package com.kaan.play.ui.player

import android.content.Context
import android.net.Uri
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
import androidx.media3.common.util.Util
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kaan.play.data.stream.PlayableStream
import okhttp3.OkHttpClient
import java.util.Locale

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

/** Altyazı seçeneği; [index] null ise "Kapalı". [ordinal]: aynı dildeki 1'den başlayan sıra. */
data class SubtitleOption(val index: Int?, val language: String?, val ordinal: Int, val selected: Boolean)

/**
 * Media3 ExoPlayer'ı sarar: kaynağı yükler (proxyHeaders + altyazılar), durumu Compose'a açar.
 * Başlıklar yalnızca akış isteklerine eklenir; altyazı adreslerine (üçüncü taraf sunucular) gönderilmez.
 */
@OptIn(UnstableApi::class)
class PlaybackController(context: Context, client: OkHttpClient) {

    @Volatile private var headers: Map<String, String> = emptyMap()
    @Volatile private var subtitleUrls: Set<String> = emptySet()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                ResolvingDataSource.Factory(OkHttpDataSource.Factory(client)) { spec ->
                    if (headers.isEmpty() || spec.uri.toString() in subtitleUrls) spec else spec.withAdditionalHeaders(headers)
                },
            ),
        )
        .build()
        .also { exo ->
            // Altyazı varsayılan olarak kapalıdır; kullanıcı seçince açılır.
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

    var ui by mutableStateOf(PlayerUiState())
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui = ui.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                ui = ui.copy(buffering = state == Player.STATE_BUFFERING, ended = state == Player.STATE_ENDED)
            }

            override fun onPlayerError(error: PlaybackException) {
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
        subtitleUrls = stream.subtitles.map { it.url }.toSet()

        val item = MediaItem.Builder()
            .setUri(stream.url)
            .apply { streamMimeType(stream.url)?.let(::setMimeType) }
            .setSubtitleConfigurations(
                stream.subtitles.mapIndexed { i, s ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                        .setId("addon-$i")
                        .setMimeType(subtitleMimeType(s.url))
                        .setLanguage(s.language?.let(Util::normalizeLanguageCode))
                        .build()
                },
            )
            .build()

        ui = ui.copy(error = null, ended = false, buffering = true)
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0L))

    fun positionMs(): Long = player.currentPosition

    /** Süre bilinmiyorsa 0. */
    fun durationMs(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    // ---- altyazı ----

    private fun textGroups(): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }

    /** "Kapalı" + akıştaki/addon'daki tüm altyazılar. */
    fun subtitleOptions(): List<SubtitleOption> {
        val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val seen = HashMap<String?, Int>()
        val tracks = textGroups().mapIndexed { i, group ->
            val language = group.mediaTrackGroup.getFormat(0).language
            val ordinal = (seen[language] ?: 0) + 1
            seen[language] = ordinal
            SubtitleOption(index = i, language = language, ordinal = ordinal, selected = !textOff && group.isSelected)
        }
        return listOf(SubtitleOption(null, null, 1, selected = textOff || tracks.none { it.selected })) + tracks
    }

    fun selectSubtitle(option: SubtitleOption) {
        val builder = player.trackSelectionParameters.buildUpon()
        val group = option.index?.let { textGroups().getOrNull(it) }
        if (group == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = builder.build()
        ui = ui.copy(tracksVersion = ui.tracksVersion + 1)
    }

    fun release() = player.release()

    companion object {
        /** `https://x/y.m3u8?token` gibi adreslerde ya da uzantısız HLS/DASH'te Media3 türü kendi çıkaramaz. */
        internal fun streamMimeType(url: String): String? {
            val lower = url.lowercase(Locale.ROOT)
            return when {
                ".m3u8" in lower -> MimeTypes.APPLICATION_M3U8
                ".mpd" in lower -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        }

        internal fun subtitleMimeType(url: String): String {
            val path = url.substringBefore('?').lowercase(Locale.ROOT)
            return when {
                path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP // addon altyazıları çoğunlukla .srt
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
