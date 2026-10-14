package com.kaan.play.ui.player

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.kaan.play.PlayApp
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.download.DownloadedSubtitleLoader
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.TorrentLinks
import androidx.compose.runtime.snapshotFlow
import com.kaan.play.data.subtitle.SubtitleDiag
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLoader
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.TrackLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emptyFlow
import com.kaan.play.ui.theme.PlayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tam ekran yatay oynatıcı. Ekran açık kalır. Konum her 10 sn'de bir, duraklatınca, kaynak değişince,
 * bitince ve etkinlik durunca (Geri dahil) geçmişe yazılır; kurallar [WatchRules]'tadır.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var session: PlaySession
    private lateinit var controller: PlaybackController
    private lateinit var subtitles: SubtitleController
    private lateinit var autoNext: AutoNextController

    /** Şu an oynayan akış (kaynak değişince güncellenir); sonraki bölümde "aynı eklenti" bundan bulunur. */
    private lateinit var currentStream: PlayableStream

    /** Otomatik sonraki bölümde [PlaySession.stream] açılmazsa sırayla denenecek yedekler. */
    private val fallbacks = ArrayDeque<PlayableStream>()

    /** Sonraki bölüme geçiliyor: bu bölüm "izlendi" yazılır, oynatıcı hataları (kapanan torrent vb.) gösterilmez. */
    private var leaving = false
    private var finishedForAdvance = false

    /** Dikey kaydırmayla ses/parlaklık; dokunmatik olmayan cihazlarda (TV/kumanda) null. */
    private var levels: DeviceLevels? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = PlaySession.from(intent)
        if (parsed == null) {
            finish()
            return
        }
        session = parsed
        currentStream = parsed.stream
        fallbacks.addAll(parsed.fallbacks)
        if (parsed.fromAutoNext) noTransition(open = true)
        val container = (application as PlayApp).container

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (DeviceLevels.supportsTouch(this)) {
            levels = DeviceLevels(window, getSystemService(AUDIO_SERVICE) as AudioManager, contentResolver)
        }
        enterImmersive()

        val offline = session.offline
        // Torrent motoru bu oynatıcı kapanana dek açık kalır (kapanınca geçici dosyalar silinir).
        container.torrentEngine.retain()
        controller = PlaybackController(
            this, container.httpClient,
            offlineCache = offline?.let { container.downloads.cache },
            torrentUrl = container.torrentServer::urlFor,
        )
        controller.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress() // duraklatma
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    saveProgress() // konum süreye eşitlenir: izlendi
                    // Dizide sonraki bölüm varsa geçişi kart üstlenir (kapanmaz); yoksa eskisi gibi kapan.
                    if (!autoNext.onEnded()) finish()
                }
            }
        })
        autoNext = AutoNextController(
            scope = lifecycleScope,
            target = session.target,
            config = { AutoNextConfig(container.settings.autoNext.first(), container.settings.autoNextSeconds.first()) },
            findNext = ::findNextEpisode,
            prepare = ::prepareNext,
            onAdvance = ::advanceTo,
        )
        autoNext.start()
        controller.onFailure = { leaving || tryFallback() }
        subtitles = SubtitleController(
            scope = lifecycleScope,
            playback = controller,
            request = session.target.request,
            // İndirilmiş kopya: altyazılar yalnızca indirilenler (yerel dosya), altyazı addon'larına ve çevrimiçi aramaya
            // sorulmaz; kullanıcının dosyadan eklediği altyazı yine okunur.
            loader = SubtitleDiag.loader(
                if (offline != null) {
                    val downloaded = DownloadedSubtitleLoader(offline.subtitleFiles)
                    TrackLoader { track ->
                        if (SubtitleLoader.isLocal(track.url)) container.subtitleLoader.load(track) else downloaded.load(track)
                    }
                } else {
                    container.subtitleLoader
                },
            ),
            memory = container.subtitleMemory,
            provider = if (offline != null) SubtitleProvider { emptyFlow() } else SubtitleDiag.provider(container.subtitleProvider),
            preferredLanguage = { container.settings.subtitleLanguage.first() },
            persist = container.appScope,
        )
        subtitles.setStream(session.stream)
        subtitles.start()
        // Teşhis (yalnızca debug): videonun içindeki parçalar ve geçerli seçim.
        lifecycleScope.launch {
            snapshotFlow { subtitles.items.filterIsInstance<SubtitleItem.Embedded>().map { it.language } }
                .collect { SubtitleDiag.d("videonun içindeki altyazılar: $it") }
        }
        lifecycleScope.launch {
            snapshotFlow { subtitles.selected }.collect { item ->
                SubtitleDiag.d(
                    when (item) {
                        null -> "seçim: kapalı"
                        is SubtitleItem.Embedded -> "seçim: videonun içindeki parça ${item.index} dil=${item.language}"
                        is SubtitleItem.External -> {
                            val origin = if (item.track.kind == SubtitleKind.Local) "dosya" else item.track.origin
                            "seçim: ${item.track.kind} $origin dil=${item.language}"
                        }
                    },
                )
            }
        }
        if (offline == null) {
            controller.load(session.stream, session.startPositionMs)
        } else {
            lifecycleScope.launch {
                val item = container.downloads.mediaItem(offline.downloadId)
                if (item == null) controller.markUnavailable() else controller.loadDownloaded(item, session.startPositionMs)
            }
        }

        setContent {
            PlayTheme(darkTheme = true) {
                PlayerScreen(
                    controller = controller,
                    subtitles = subtitles,
                    target = session.target,
                    showResumeNotice = session.autoResumed && session.startPositionMs > 0,
                    canSwitchSource = offline == null,
                    levels = levels,
                    autoNext = autoNext,
                    onAutoNextCancel = ::cancelAutoNext,
                    onBack = ::finish,
                    onSourcePicked = ::switchSource,
                )
            }
        }

        // Oynatma sürerken konumu düzenli kaydet; sadece ekran görünürken çalışır.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(WatchRules.SAVE_INTERVAL_MS)
                    if (controller.ui.isPlaying) saveProgress()
                }
            }
        }
        // Sonraki bölüm kartı: yarım saniyede bir konuma bakar (geri sayım yalnızca oynarken işler).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(AUTO_NEXT_TICK_MS)
                    autoNext.tick(
                        nowMs = SystemClock.elapsedRealtime(),
                        positionMs = controller.positionMs(),
                        durationMs = controller.durationMs(),
                        playing = controller.ui.isPlaying,
                        ended = controller.ui.ended,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Dönüşte (ör. başka uygulamadan) kontroller açık gelir; oynatma kullanıcıya bırakılır.
        enterImmersive()
    }

    override fun onStop() {
        super.onStop()
        if (!::controller.isInitialized) return
        controller.player.pause()
        saveProgress()
        subtitles.flush()
    }

    /** Çıkışta pencere parlaklığı bırakılır; sistem değeri geçerli olur. */
    override fun finish() {
        levels?.restoreBrightness()
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        levels?.restoreBrightness()
        if (::controller.isInitialized) {
            controller.release()
            (application as PlayApp).container.torrentEngine.release()
        }
    }

    private fun switchSource(stream: PlayableStream) {
        saveProgress()
        // Kullanıcı kendisi seçti: otomatik geçişin yedekleri artık anlamsız.
        fallbacks.clear()
        currentStream = stream
        subtitles.setStream(stream)
        controller.load(stream, controller.positionMs())
    }

    // ---- otomatik sonraki bölüm ----

    private val appContainer get() = (application as PlayApp).container

    /**
     * Bu bölümden sonraki (sezon, bölüm). Çevrimiçi: TMDB bölüm listesinden (aynı sezonun sonraki bölümü, bitince sonraki
     * sezonun ilki; yayımlanmamış/yok = son bölüm, null). İndirilmiş kopya: internet olmadığı için yalnızca indirilmiş bölümlerden.
     */
    private suspend fun findNextEpisode(): Pair<Int, Int>? {
        val r = session.target.request
        val season = r.season ?: return null
        val episode = r.episode ?: return null
        if (session.offline == null) return appContainer.nextEpisodes.next(r.tmdbId, season, episode)
        return appContainer.downloads.rows.first()
            .filter {
                it.tmdbId == r.tmdbId && it.type == MediaType.Tv && it.status == DownloadStatus.Completed &&
                    (it.season > season || (it.season == season && it.episode > episode))
            }
            .minWithOrNull(compareBy({ it.season }, { it.episode }))
            ?.let { it.season to it.episode }
    }

    /**
     * Sonraki bölümü oynatmaya hazırlar: indirilmişse o kopya; yoksa izlenen eklentiyle aynısı önce olacak biçimde sıralı kaynaklar
     * ([AutoNextRules.order]). Hiç kaynak yoksa null (kart "Başka kaynak seç" der).
     */
    private suspend fun prepareNext(season: Int, episode: Int): NextPlayback? {
        val request = session.target.request.copy(season = season, episode = episode, refresh = false)
        val target = session.target.copy(request = request)
        appContainer.downloads.completedFor(request)?.let { return NextPlayback.Offline(target, it) }
        if (session.offline != null) return null
        val found = AutoNextRules.collect(appContainer.streams.streams(request), currentStream.source.addonId)
        val ordered = AutoNextRules.order(found, currentStream)
        return if (ordered.isEmpty()) null else NextPlayback.Online(target, ordered)
    }

    /**
     * Sonraki bölümü yeni bir oynatıcı etkinliğinde açar (altyazı, başlık, geçmiş hepsi o bölüm için sıfırdan kurulur) ve bunu
     * kapatır. Bu bölüm "izlendi" yazılır; önceki torrent oturumu kapatılır; kayıtlı konumu varsa oradan devam edilir.
     */
    private fun advanceTo(next: NextPlayback) {
        if (leaving) return
        leaving = true
        lifecycleScope.launch {
            val container = appContainer
            val saved = container.library.progress(next.target)
            val resumeAt = saved?.takeIf {
                WatchRules.isResumable(it.positionMs, it.durationMs) && container.settings.alwaysResume.first()
            }?.positionMs ?: 0L
            val nextSession = when (next) {
                is NextPlayback.Online -> PlaySession(
                    target = next.target,
                    stream = next.streams.first(),
                    startPositionMs = resumeAt,
                    autoResumed = resumeAt > 0,
                    fallbacks = next.streams.drop(1),
                    fromAutoNext = true,
                )
                is NextPlayback.Offline -> PlaySession.offline(next.download, resumeAt, autoResumed = resumeAt > 0, fromAutoNext = true)
            }
            finishedForAdvance = true
            saveProgress() // bu bölüm izlendi
            if (TorrentLinks.isMagnet(currentStream.url)) container.torrentEngine.closeActive()
            startActivity(nextSession.toIntent(this@PlayerActivity))
            noTransition(open = false)
            finish()
        }
    }

    /** Hata ekranından önce: sıradaki yedek kaynak varsa ona geçilir (true). Hepsi tükenince false: her zamanki hata ekranı. */
    private fun tryFallback(): Boolean {
        val next = fallbacks.removeFirstOrNull() ?: return false
        currentStream = next
        subtitles.setStream(next)
        controller.load(next, controller.positionMs())
        return true
    }

    /** Kartta "İptal"/Geri: kart kapanır; video zaten bitmişse eskisi gibi oynatıcı da kapanır. */
    private fun cancelAutoNext() {
        autoNext.cancel()
        if (controller.ui.ended) finish()
    }

    /** Etkinlik geçişi animasyonsuz: bölümden bölüme geçiş kesintisiz görünür. */
    private fun noTransition(open: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                if (open) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE,
                0,
                0,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /** Duraklatma/bitiş anlarında da çağrılır: bitişte (ya da sonraki bölüme geçerken) konum süreye eşitlenir ("izlendi"). */
    private fun saveProgress() {
        val duration = controller.durationMs()
        if (duration <= 0 || controller.ui.error != null) return
        val position = if (controller.ui.ended || finishedForAdvance) duration else controller.positionMs()
        val container = (application as PlayApp).container
        // Etkinlik kapanırken de tamamlansın diye uygulama kapsamında.
        container.appScope.launch { container.library.saveProgress(session.target, position, duration) }
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val AUTO_NEXT_TICK_MS = 500L
    }
}
