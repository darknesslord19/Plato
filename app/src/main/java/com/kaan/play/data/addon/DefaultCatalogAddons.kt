package com.kaan.play.data.addon

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.kaan.play.data.addon.DefaultAddons.DefaultAddon
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.settings.SettingsRepository

/**
 * İlk açılışta eklenen varsayılan katalog addon'u: kullanıcının kendi "Play Özel Kataloglar"ı
 * (Kullanıcı kararı, 2026-10-21). Yalnızca katalog verir, akış vermez.
 *
 * Kurallar:
 * 1. Bayrağı altyazı addon'larından AYRIdır ([SettingsRepository.DEFAULT_CATALOG_ADDON_SEEDED]):
 *    altyazı addon'larını önceden almış kullanıcılar da bunu bir kez alır.
 * 2. Kullanıcı silerse bayrak açık kaldığı için geri gelmez; aynı adres kayıtlıysa eklenmez.
 * 3. Ağa çıkılmaz; manifest alanları (katalog adları dahil) sabit yazılır. Raflar ana sayfada her zamanki gibi
 *    addon'dan çekilir; sunucu kapalıysa sessizce gizlenir.
 */
object DefaultCatalogAddons {

    const val PLAY_CATALOGS_ID = "com.play.custom.catalogs"
    const val PLAY_CATALOGS_URL = "https://play-nuvio-zel-katalog-st-dyosu.ai.studio/manifest.json"

    private const val DESCRIPTION =
        "TMDB ile canlı ve güncel yeni filmler, diziler, haftanın popülerleri ve yerli yapımlar."

    val DEFAULTS = listOf(
        DefaultAddon(
            url = PLAY_CATALOGS_URL,
            version = "1.1.0",
            description = DESCRIPTION,
            logoUrl = null,
            manifest = AddonManifest(
                id = PLAY_CATALOGS_ID,
                name = "Play Özel Kataloglar",
                version = "1.1.0",
                description = DESCRIPTION,
                types = listOf("movie", "series"),
                resources = listOf(ManifestResource("catalog")),
                idPrefixes = listOf("tt", "tmdb"),
                catalogs = listOf(
                    ManifestCatalog("movie", "play_new_movies", "Yeni Eklenen Filmler"),
                    ManifestCatalog("series", "play_new_series", "Yeni Eklenen Diziler"),
                    ManifestCatalog("movie", "play_trending_movies", "Haftanın Filmleri"),
                    ManifestCatalog("series", "play_trending_series", "Haftanın Dizileri"),
                    ManifestCatalog("movie", "play_tr_movies", "Yerli Filmler"),
                    ManifestCatalog("series", "play_tr_series", "Yerli Diziler"),
                    ManifestCatalog("movie", "play_popular_movies", "Popüler Filmler"),
                    ManifestCatalog("series", "play_popular_series", "Popüler Diziler"),
                    ManifestCatalog("movie", "play_top", "Sizin İçin Önerilenler"),
                    ManifestCatalog("movie", "play_categories", "Kategoriler"),
                ),
            ),
        ),
    )

    /** Tohumlama yapılmamışsa Play Özel Kataloglar'ı AÇIK ekler. @return Tohumlama yapıldıysa true. */
    suspend fun seedIfNeeded(
        dao: AddonDao,
        store: DataStore<Preferences>,
        clock: () -> Long = System::currentTimeMillis,
    ): Boolean = DefaultAddons.seedOnce(dao, store, SettingsRepository.DEFAULT_CATALOG_ADDON_SEEDED, DEFAULTS, clock)
}
