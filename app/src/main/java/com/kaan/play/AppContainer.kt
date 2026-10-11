package com.kaan.play

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Elle kurulan bağımlılık kabı. DI framework'ü yok; katmanlar eklendikçe
 * (TMDB, Room, DataStore, StreamProvider) buraya tembel alanlar olarak eklenir.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** Tüm ağ katmanının tek JSON ayarı. Addon'lar alan ekleyebilir; bilinmeyenleri yok say. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}
