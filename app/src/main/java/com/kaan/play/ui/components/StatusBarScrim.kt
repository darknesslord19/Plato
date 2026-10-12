package com.kaan.play.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Durum çubuğunun arkasına uzanan ekranlarda (Ana sayfa kapağı, Detay) içerik kayarken saat/simgeler okunsun diye
 * sayfa renginde ince geçiş. Durum çubuğu payı Scaffold tarafından zaten tüketilmişse yüksekliği sıfırdır.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(0f to page.copy(alpha = 0.95f), 0.6f to page.copy(alpha = 0.85f), 1f to Color.Transparent)),
    )
}
