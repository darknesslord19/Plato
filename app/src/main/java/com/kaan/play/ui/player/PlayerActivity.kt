package com.kaan.play.ui.player

import android.os.Build
import android.os.Bundle
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
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.i18n.AppLanguage
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = PlaySession.from(intent)
        if (parsed == null) {
            finish()
            return
        }
        session = parsed
        val container = (application as PlayApp).container

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()

        controller = PlaybackController(this, container.httpClient)
        controller.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress() // duraklatma
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    saveProgress() // konum süreye eşitlenir: izlendi
                    finish()
                }
            }
        })
        subtitles = SubtitleController(
            scope = lifecycleScope,
            playback = controller,
            request = session.target.request,
            loader = container.subtitleLoader,
            memory = container.subtitleMemory,
            provider = container.subtitleProvider,
            appLanguage = { AppLanguage.effective().iso },
            persist = container.appScope,
        )
        subtitles.setStream(session.stream)
        subtitles.start()
        controller.load(session.stream, session.startPositionMs)

        setContent {
            PlayTheme(darkTheme = true) {
                PlayerScreen(
                    controller = controller,
                    subtitles = subtitles,
                    target = session.target,
                    showResumeNotice = session.autoResumed && session.startPositionMs > 0,
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

    override fun onDestroy() {
        super.onDestroy()
        if (::controller.isInitialized) controller.release()
    }

    private fun switchSource(stream: PlayableStream) {
        saveProgress()
        subtitles.setStream(stream)
        controller.load(stream, controller.positionMs())
    }

    /** Duraklatma/bitiş anlarında da çağrılır: bitişte konum süreye eşitlenir ("izlendi"). */
    private fun saveProgress() {
        val duration = controller.durationMs()
        if (duration <= 0 || controller.ui.error != null) return
        val position = if (controller.ui.ended) duration else controller.positionMs()
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
}
