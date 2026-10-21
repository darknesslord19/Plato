package com.kaan.play.data.addon

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bellek içi DataStore: Windows'ta dosyalı DataStore aynı dosyaya art arda yazarken yeniden adlandıramıyor. */
private class MemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class DefaultCatalogAddonsTest {

    private val store = MemoryPreferencesStore()
    private val dao = TestAddonDao()

    private fun catalogRows() = dao.all().filter { it.sourceUrl == DefaultCatalogAddons.PLAY_CATALOGS_URL }

    @Test
    fun seedsPlayCatalogsEnabledWithFixedManifest() = runTest {
        assertTrue(DefaultCatalogAddons.seedIfNeeded(dao, store, clock = { 7_000L }))

        val row = catalogRows().single()
        assertEquals(AddonType.NUVIO_ADDON, row.type)
        assertTrue(row.enabled)
        assertEquals("com.play.custom.catalogs", row.addonId)
        assertEquals("Play Özel Kataloglar", row.name)
        assertEquals(7_000L, row.addedAt)

        val manifest = PlayJson.decodeFromString(AddonManifest.serializer(), row.manifestJson)
        assertEquals(listOf("catalog"), manifest.resources.map { it.name })
        assertEquals(listOf("movie", "series"), manifest.types)
        assertEquals(listOf("tt", "tmdb"), manifest.idPrefixes)
        assertEquals(
            listOf(
                "play_new_movies", "play_new_series", "play_trending_movies", "play_trending_series",
                "play_tr_movies", "play_tr_series", "play_popular_movies", "play_popular_series",
                "play_top", "play_categories",
            ),
            manifest.catalogs.map { it.id },
        )
        assertEquals("Popüler Filmler", manifest.catalogs.first { it.id == "play_popular_movies" }.name)
        // Yalnızca katalog: akış ve altyazı listelerine girmez, ana sayfada sağlayıcı olarak görünür.
        assertTrue(manifest.providesCatalogs)
        assertFalse(manifest.providesStreams)
        assertFalse(manifest.providesSubtitles)

        assertEquals(true, store.data.first()[SettingsRepository.DEFAULT_CATALOG_ADDON_SEEDED])
    }

    @Test
    fun usersWhoAlreadyGotSubtitleAddonsStillGetCatalogOnce() = runTest {
        // Önceki sürümde altyazı addon'ları tohumlanmış (onların bayrağı açık).
        DefaultSubtitleAddons.seedIfNeeded(dao, store)
        assertNull(store.data.first()[SettingsRepository.DEFAULT_CATALOG_ADDON_SEEDED])

        assertTrue(DefaultCatalogAddons.seedIfNeeded(dao, store))
        assertEquals(1, catalogRows().size)
        assertEquals(3, dao.all().size)

        // Yeniden açılış: ne katalog ne altyazı tekrar eklenir.
        assertFalse(DefaultCatalogAddons.seedIfNeeded(dao, store))
        assertFalse(DefaultSubtitleAddons.seedIfNeeded(dao, store))
        assertEquals(3, dao.all().size)
    }

    @Test
    fun deletedCatalogDoesNotComeBack() = runTest {
        DefaultCatalogAddons.seedIfNeeded(dao, store)
        dao.delete(catalogRows().single().id)

        assertFalse(DefaultCatalogAddons.seedIfNeeded(dao, store))
        assertTrue(catalogRows().isEmpty())
    }

    @Test
    fun doesNotDuplicateWhenSameAddressExists() = runTest {
        dao.insert(
            AddonEntity(
                type = AddonType.NUVIO_ADDON,
                sourceUrl = DefaultCatalogAddons.PLAY_CATALOGS_URL,
                addonId = "com.play.custom.catalogs",
                name = "Elle eklenen",
                version = null,
                description = null,
                logoUrl = null,
                enabled = false,
                manifestJson = "{}",
                addedAt = 100L,
            ),
        )

        assertTrue(DefaultCatalogAddons.seedIfNeeded(dao, store))
        val row = catalogRows().single()
        // Kullanıcının satırı (kapalı hâli dahil) değişmez.
        assertEquals("Elle eklenen", row.name)
        assertFalse(row.enabled)
    }
}
