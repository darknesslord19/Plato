package com.kaan.play.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaan.play.AppContainer
import com.kaan.play.PlayApp

@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as PlayApp).container }
}

/** Elle kurulan kaptan ViewModel üretir (DI framework'ü yok). */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = rememberContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
