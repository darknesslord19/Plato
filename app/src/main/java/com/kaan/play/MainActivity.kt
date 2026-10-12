package com.kaan.play

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.kaan.play.ui.nav.PlayRoot
import com.kaan.play.ui.theme.PlayTheme

// AppCompatActivity: uygulama başına dil (AppCompatDelegate.setApplicationLocales) için gerekli.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Koyu tema varsayılan: sistem açık modda olsa bile çubuk ikonları açık renk olsun.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Yalnızca ilk açılışta: dil değişimi gibi yeniden oluşturmalarda aynı bağlantı tekrar işlenmesin.
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            // Tema ayarı DataStore ile 7. adımda bağlanacak; çubuk stili de o zaman temaya uyacak.
            PlayTheme(darkTheme = true) {
                PlayRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** Tarayıcıdan/başka uygulamadan gelen https manifest bağlantısı "eklenti ekle" akışını başlatır. */
    private fun handleLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "https") return
        (application as PlayApp).container.pendingAddonUrl.value = uri.toString()
    }
}
