package com.kaan.play

import android.content.Context
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.i18n.AppLanguage
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Elle kurulan bağımlılık kabı. DI framework'ü yok; katmanlar eklendikçe
 * (Room, DataStore, StreamProvider) buraya tembel alanlar olarak eklenir.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val json: Json = PlayJson

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Dil her istekte [AppLanguage.effective] ile okunur: Ayarlar'da dil değişince yeni istekler yeni dilde gider. */
    val tmdb: TmdbRepository by lazy {
        TmdbRepository(httpClient, json, BuildConfig.TMDB_API_KEY, language = AppLanguage::effective)
    }
}
