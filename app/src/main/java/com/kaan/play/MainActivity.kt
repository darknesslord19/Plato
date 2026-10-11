package com.kaan.play

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kaan.play.ui.nav.PlayRoot
import com.kaan.play.ui.theme.PlayTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Koyu tema varsayılan: sistem açık modda olsa bile çubuk ikonları açık renk olsun.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Tema ayarı DataStore ile 7. adımda bağlanacak; çubuk stili de o zaman temaya uyacak.
            PlayTheme(darkTheme = true) {
                PlayRoot()
            }
        }
    }
}
