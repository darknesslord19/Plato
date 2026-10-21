package com.kaan.play.data.addon

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.kaan.play.data.addon.DefaultAddons.DefaultAddon
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.settings.SettingsRepository

/**
 * İlk açılışta eklenen varsayılan altyazı addon'ları (Kullanıcı kararı, 2026-10-20).
 *
 * Kurallar:
 * 1. Yalnızca iki altyazı addon'u eklenir: AltyazıDB ve AltyazıTR. Video veya katalog eklenmez.
 * 2. Tohumlama yalnızca bir kez yapılır (DataStore [SettingsRepository.DEFAULT_SUBTITLE_ADDONS_SEEDED] bayrağı).
 * 3. Kullanıcı silerse bayrak açık kaldığı için bir daha geri gelmez.
 * 4. Adres zaten kayıtlıysa yeniden eklenmez.
 * 5. Açılışı ve oynatmayı geciktirmemek için ağa çıkılmaz; manifest alanları sabit yazılır.
 * Ortak düzen [DefaultAddons.seedOnce]'tadır.
 */
object DefaultSubtitleAddons {

    const val ALTYAZIDB_URL = "https://altyazidb.online/manifest.json"
    const val ALTYAZITR_URL = "https://altyazi-stremio.online/manifest.json"

    val DEFAULTS = listOf(
        DefaultAddon(
            url = ALTYAZIDB_URL,
            version = "0.3.8",
            description = "AltyazıDB Türkçe altyazı eklentisi",
            logoUrl = "https://altyazidb.online/public/logo.png",
            manifest = AddonManifest(
                id = "com.blast.altyazidb.subtitles",
                name = "AltyazıDB",
                version = "0.3.8",
                description = "AltyazıDB Türkçe altyazı eklentisi",
                logo = "https://altyazidb.online/public/logo.png",
                types = listOf("movie", "series"),
                resources = listOf(ManifestResource("subtitles")),
                idPrefixes = listOf("tt", "tmdb"),
            ),
        ),
        DefaultAddon(
            url = ALTYAZITR_URL,
            version = "1.0.0",
            description = "AltyazıTR Türkçe altyazı eklentisi",
            logoUrl = "https://altyazi-stremio.online/images/logo-a.svg",
            manifest = AddonManifest(
                id = "com.ege.altyazitr",
                name = "AltyazıTR",
                version = "1.0.0",
                description = "AltyazıTR Türkçe altyazı eklentisi",
                logo = "https://altyazi-stremio.online/images/logo-a.svg",
                types = listOf("movie", "series"),
                resources = listOf(ManifestResource("subtitles")),
                idPrefixes = listOf("tt"),
            ),
        ),
    )

    /**
     * Tohumlama yapılmamışsa varsayılan altyazı addon'larını ekler ve DataStore bayrağını ayarlar.
     * @return Tohumlama yapıldıysa true, zaten yapılmışsa false.
     */
    suspend fun seedIfNeeded(
        dao: AddonDao,
        store: DataStore<Preferences>,
        clock: () -> Long = System::currentTimeMillis,
    ): Boolean = DefaultAddons.seedOnce(dao, store, SettingsRepository.DEFAULT_SUBTITLE_ADDONS_SEEDED, DEFAULTS, clock)
}
