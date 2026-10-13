package com.kaan.play

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.data.plugin.DeepLink
import com.kaan.play.ui.components.SplashOverlay
import com.kaan.play.ui.nav.PlayRoot
import com.kaan.play.ui.theme.PlayTheme
import com.kaan.play.ui.addons.DeepLinkHost
import com.kaan.play.ui.update.UpdateDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// AppCompatActivity: uygulama başına dil (AppCompatDelegate.setApplicationLocales) için gerekli.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PlayApp).container
        val settings = container.settings
        // Arka planda, oturumda bir kez; hata/zaman aşımı sessiz (bkz. UpdateChecker).
        container.updateChecker.checkOnce(container.appScope)
        // Açılışta tek seferlik okuma (milisaniyeler): kayıtlı tema ilk karede doğru çıksın, yanıp sönmesin.
        val initialTheme = runBlocking { settings.themeMode.first() }

        // Yalnızca ilk açılışta: dil değişimi gibi yeniden oluşturmalarda aynı bağlantı tekrar işlenmesin.
        if (savedInstanceState == null) handleLink(intent)

        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = initialTheme)
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // Durum/gezinme çubuğu ikonları temaya uysun.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            PlayTheme(darkTheme = dark) {
                Box {
                    PlayRoot()
                    SplashOverlay()
                    UpdateDialog(container.updateChecker)
                    DeepLinkHost(container.deepLinks)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /**
     * Tarayıcıdan/başka uygulamadan gelen bağlantı: https manifest bağlantısı "eklenti ekle" akışını başlatır,
     * `play://add?repo=` / `play://add?nuvio=` önce onay penceresi açar, onaylanırsa ekler (sonuç Snackbar ile).
     */
    private fun handleLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        val container = (application as PlayApp).container
        when (uri.scheme) {
            "https" -> container.pendingAddonUrl.value = uri.toString()
            DeepLink.SCHEME -> {
                // Opak ya da hiyerarşik olmayan bağlantıda (play:add?...) getQueryParameter fırlatır.
                val repo = if (uri.isHierarchical) uri.getQueryParameter("repo") else null
                // nuvio_plugin: Repo Manager'ın Nuvio plugin reposu bağlantısı; tür yine içerikten anlaşılır.
                val nuvio = if (uri.isHierarchical) uri.getQueryParameter("nuvio") ?: uri.getQueryParameter("nuvio_plugin") else null
                container.deepLinks.handle(uri.scheme, uri.host, repo, nuvio)
            }
        }
    }
}
