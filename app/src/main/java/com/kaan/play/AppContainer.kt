package com.kaan.play

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.OkHttpManifestFetcher
import com.kaan.play.data.db.PlayDatabase
import com.kaan.play.data.library.ContinueWatching
import com.kaan.play.data.library.LibraryRepository
import com.kaan.play.data.library.NextEpisodeResolver
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.stream.AddonStreamProvider
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.data.web.WebSiteRepository
import com.kaan.play.ui.web.AndroidWebDataCleaner
import com.kaan.play.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Elle kurulan bağımlılık kabı. DI framework'ü yok; katmanlar eklendikçe
 * (DataStore, StreamProvider) buraya tembel alanlar olarak eklenir.
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

    val database: PlayDatabase by lazy {
        Room.databaseBuilder(appContext, PlayDatabase::class.java, "play.db").build()
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") })
    }

    val library: LibraryRepository by lazy {
        LibraryRepository(database.watchHistoryDao(), database.favoriteDao())
    }

    /** Etkinliklerden bağımsız işler için (ör. oynatıcı kapanırken konum kaydı tamamlansın). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val continueWatching: ContinueWatching by lazy {
        val resolver = NextEpisodeResolver(
            loadSeason = { tvId, season -> tmdb.season(tvId, season) },
            today = { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
        )
        ContinueWatching(library) { row -> resolver.next(row.tmdbId, row.season, row.episode) }
    }

    val addons: AddonRepository by lazy {
        AddonRepository(database.addonDao(), OkHttpManifestFetcher(httpClient))
    }

    /** Kullanıcının eklediği web siteleri (reklamsız web görünümü). */
    val webSites: WebSiteRepository by lazy {
        WebSiteRepository(database.addonDao(), AndroidWebDataCleaner(appContext))
    }

    /** Faz 1'de kaynaklar yalnızca aktif addon'lardan gelir; zaman aşımı her istekte Ayarlar'dan okunur. */
    val streams: StreamProvider by lazy {
        AddonStreamProvider(
            addons = addons::enabledAddons,
            client = httpClient,
            timeout = { settings.sourceTimeoutSeconds.first().seconds },
        )
    }

    /**
     * Dışarıdan (https eklenti bağlantısı) gelen addon adresi. Etkinlik yazar,
     * [com.kaan.play.ui.addons.AddonsViewModel] alıp sıfırlar.
     */
    val pendingAddonUrl = MutableStateFlow<String?>(null)
}
