package com.kaan.play

import android.content.Context
import android.util.Log
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
import com.kaan.play.data.plugin.FilePluginCodeStore
import com.kaan.play.data.plugin.OkHttpPluginCodeFetcher
import com.kaan.play.data.plugin.PluginRepository
import com.kaan.play.data.plugin.PluginSettingsRepository
import com.kaan.play.data.plugin.PluginStreamProvider
import com.kaan.play.data.plugin.SourceInstaller
import com.kaan.play.data.plugin.runtime.PluginRuntime
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.stream.AddonStreamProvider
import com.kaan.play.data.stream.CompositeStreamProvider
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.data.web.WebSiteRepository
import com.kaan.play.ui.web.AndroidWebDataCleaner
import com.kaan.play.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
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
        Room.databaseBuilder(appContext, PlayDatabase::class.java, "play.db")
            .addMigrations(PlayDatabase.MIGRATION_1_2)
            .build()
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

    /** Eklenti ayarları (`onSettings` ile bildirilen değerler), eklenti başına; ayrı bir DataStore dosyasında. */
    val pluginSettings: PluginSettingsRepository by lazy {
        PluginSettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("plugin_settings") })
    }

    /** Nuvio eklenti depoları (JS kazıyıcılar; Faz 2). Kodlar uygulamaya özel `filesDir/plugins` içinde durur. */
    val plugins: PluginRepository by lazy {
        PluginRepository(
            addonDao = database.addonDao(),
            pluginDao = database.pluginDao(),
            fetcher = OkHttpManifestFetcher(httpClient),
            codeFetcher = OkHttpPluginCodeFetcher(httpClient),
            store = FilePluginCodeStore(File(appContext.filesDir, "plugins")),
            onPluginRemoved = { pluginSettings.clear(it) },
        )
    }

    /** "Eklenti ekle": adresin içeriğine bakıp addon mu eklenti deposu mu olduğunu kendisi anlar. */
    val sourceInstaller: SourceInstaller by lazy { SourceInstaller(addons, plugins, OkHttpManifestFetcher(httpClient)) }

    /**
     * JS kazıyıcı çalıştırıcısı (QuickJS). Gerçek TMDB anahtarı yalnızca ağ köprüsüne verilir, eklentilere yer tutucu görünür.
     * Aynı anda en çok [PluginStreamProvider.MAX_PARALLEL] eklenti çalışır (her biri ayrı bir QuickJS örneği).
     */
    val pluginRuntime: PluginRuntime by lazy {
        PluginRuntime(
            httpClient, BuildConfig.TMDB_API_KEY,
            maxConcurrent = PluginStreamProvider.MAX_PARALLEL,
            log = { level, message -> Log.d("PlayPlugin", "[$level] $message") },
        )
    }

    /** Kullanıcının eklediği web siteleri (reklamsız web görünümü). */
    val webSites: WebSiteRepository by lazy {
        WebSiteRepository(database.addonDao(), AndroidWebDataCleaner(appContext))
    }

    /**
     * Kaynaklar tek birleşik sağlayıcıdan gelir: aktif addon'lar ([AddonStreamProvider]) ve açık eklentiler
     * ([PluginStreamProvider]) aynı akışta, aynı listede. Zaman aşımı her ikisi için de Ayarlar'daki ortak kaynak zaman aşımıdır
     * ve her istekte yeniden okunur.
     */
    val streams: StreamProvider by lazy {
        val timeout: suspend () -> kotlin.time.Duration = { settings.sourceTimeoutSeconds.first().seconds }
        CompositeStreamProvider(
            AddonStreamProvider(addons = addons::enabledAddons, client = httpClient, timeout = timeout),
            PluginStreamProvider(
                plugins = plugins::enabledPlugins,
                code = plugins::loadCode,
                runner = { code, request, scraperId, timeoutMs, saved -> pluginRuntime.run(code, request, scraperId, timeoutMs, saved) },
                settings = pluginSettings::get,
                timeout = timeout,
            ),
        )
    }

    /** Oynatılabilecek en az bir açık kaynak (addon ya da eklenti) var mı? Yoksa Oynat "eklenti ekle"ye götürür. */
    suspend fun hasEnabledSources(): Boolean = addons.enabledAddons().isNotEmpty() || plugins.enabledPlugins().isNotEmpty()

    /** Ana sayfa boş durumu için: hiç addon ve hiç eklenti deposu yok mu? (null: henüz yüklenmedi). */
    val hasAnySource: Flow<Boolean> by lazy {
        combine(addons.addons, plugins.repos) { a, r -> a.isNotEmpty() || r.isNotEmpty() }
    }

    /**
     * Dışarıdan (https eklenti bağlantısı) gelen addon adresi. Etkinlik yazar,
     * [com.kaan.play.ui.addons.AddonsViewModel] alıp sıfırlar.
     */
    val pendingAddonUrl = MutableStateFlow<String?>(null)
}
