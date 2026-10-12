package com.kaan.play.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.kaan.play.R
import kotlinx.coroutines.delay

private const val SplashHoldMillis = 700L

/**
 * Açılış ekranı: koyu zeminde tam logo, genişliğin %60'ı, ortada. Kısa süre durup solar; dil değişimi gibi yeniden
 * oluşturmalarda tekrar çıkmaz. Altındaki ekran bu sırada zaten kurulur, yani açılışı geciktirmez.
 */
@Composable
fun SplashOverlay() {
    var visible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SplashHoldMillis)
        visible = false
    }
    AnimatedVisibility(visible = visible, exit = fadeOut()) {
        Box(
            modifier = Modifier.fillMaxSize().background(colorResource(R.color.play_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
        }
    }
}
