package com.kaan.play.data.addon

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class TestAddonDao : AddonDao {
    private val rows = MutableStateFlow<List<AddonEntity>>(emptyList())
    private var nextId = 1L

    override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> =
        rows.map { list -> list.filter { it.type == type } }

    fun all(): List<AddonEntity> = rows.value

    override suspend fun enabledOfType(type: AddonType) =
        rows.value.filter { it.enabled && it.type == type }

    override suspend fun countOfType(type: AddonType) = rows.value.count { it.type == type }

    override suspend fun countBySourceUrl(url: String) = rows.value.count { it.sourceUrl == url }

    override suspend fun insert(entity: AddonEntity): Long {
        if (rows.value.any { it.sourceUrl == entity.sourceUrl }) return -1
        val id = nextId++
        rows.value += entity.copy(id = id)
        return id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {
        rows.value = rows.value.map { if (it.type == type) it.copy(enabled = enabled) else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(name = name, version = version, description = description, manifestJson = manifestJson) else it }
    }

    override suspend fun byId(id: Long): AddonEntity? = rows.value.firstOrNull { it.id == id }
}

class DefaultSubtitleAddonsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
    }
    private val dao = TestAddonDao()

    @Test
    fun seedsDefaultSubtitleAddonsOnFirstRun() = runTest {
        val seeded = DefaultSubtitleAddons.seedIfNeeded(dao, store, clock = { 5_000L })
        assertTrue(seeded)

        val all = dao.all()
        assertEquals(2, all.size)

        // İki addon da NUVIO_ADDON ve açık olmalı
        assertTrue(all.all { it.type == AddonType.NUVIO_ADDON && it.enabled && it.addedAt == 5_000L })

        val altyaziDb = all.firstOrNull { it.sourceUrl == DefaultSubtitleAddons.ALTYAZIDB_URL }
        assertNotNull(altyaziDb)
        assertEquals("com.blast.altyazidb.subtitles", altyaziDb!!.addonId)
        assertEquals("AltyazıDB", altyaziDb.name)
        val dbManifest = PlayJson.decodeFromString(AddonManifest.serializer(), altyaziDb.manifestJson)
        assertEquals("com.blast.altyazidb.subtitles", dbManifest.id)
        assertEquals("AltyazıDB", dbManifest.name)
        assertEquals(listOf("movie", "series"), dbManifest.types)
        assertEquals(listOf("tt", "tmdb"), dbManifest.idPrefixes)
        assertEquals(listOf(ManifestResource("subtitles")), dbManifest.resources)
        assertTrue(dbManifest.supportsSubtitles("movie", "tt0133093"))
        assertTrue(dbManifest.supportsSubtitles("series", "tt0903747:1:3"))
        assertTrue(dbManifest.supportsSubtitles("movie", "tmdb:603"))
        assertFalse(dbManifest.providesStreams)
        assertFalse(dbManifest.providesCatalogs)
        assertTrue(dbManifest.providesSubtitles)

        val altyaziTr = all.firstOrNull { it.sourceUrl == DefaultSubtitleAddons.ALTYAZITR_URL }
        assertNotNull(altyaziTr)
        assertEquals("com.ege.altyazitr", altyaziTr!!.addonId)
        assertEquals("AltyazıTR", altyaziTr.name)
        val trManifest = PlayJson.decodeFromString(AddonManifest.serializer(), altyaziTr.manifestJson)
        assertEquals("com.ege.altyazitr", trManifest.id)
        assertEquals("AltyazıTR", trManifest.name)
        assertEquals(listOf("movie", "series"), trManifest.types)
        assertEquals(listOf("tt"), trManifest.idPrefixes)
        assertEquals(listOf(ManifestResource("subtitles")), trManifest.resources)
        assertTrue(trManifest.supportsSubtitles("movie", "tt0133093"))
        assertTrue(trManifest.supportsSubtitles("series", "tt0903747:1:3"))
        assertFalse(trManifest.supportsSubtitles("movie", "tmdb:603"))
        assertFalse(trManifest.providesStreams)
        assertFalse(trManifest.providesCatalogs)
        assertTrue(trManifest.providesSubtitles)

        // DataStore bayrağı true olmalı
        val prefs = store.data.first()
        assertEquals(true, prefs[SettingsRepository.DEFAULT_SUBTITLE_ADDONS_SEEDED])
    }

    @Test
    fun secondRunDoesNotReSeed() = runTest {
        assertTrue(DefaultSubtitleAddons.seedIfNeeded(dao, store))
        assertEquals(2, dao.all().size)

        val second = DefaultSubtitleAddons.seedIfNeeded(dao, store)
        assertFalse(second)
        assertEquals(2, dao.all().size)
    }

    @Test
    fun doesNotReSeedWhenUserDeletesAnAddon() = runTest {
        DefaultSubtitleAddons.seedIfNeeded(dao, store)
        assertEquals(2, dao.all().size)

        // Kullanıcı AltyazıDB'yi sildi
        val dbRow = dao.all().first { it.sourceUrl == DefaultSubtitleAddons.ALTYAZIDB_URL }
        dao.delete(dbRow.id)
        assertEquals(1, dao.all().size)

        // Uygulama bir sonraki açılışında silinen addon geri gelmemeli
        val nextRun = DefaultSubtitleAddons.seedIfNeeded(dao, store)
        assertFalse(nextRun)
        assertEquals(1, dao.all().size)
        assertEquals(DefaultSubtitleAddons.ALTYAZITR_URL, dao.all().single().sourceUrl)
    }

    @Test
    fun doesNotDuplicateIfAddressAlreadyExists() = runTest {
        // Kullanıcı önceden AltyazıDB'yi elle eklemiş olsun
        val userRow = AddonEntity(
            type = AddonType.NUVIO_ADDON,
            sourceUrl = DefaultSubtitleAddons.ALTYAZIDB_URL,
            addonId = "custom.id",
            name = "Kullanıcı AltyazıDB",
            version = "1.0",
            description = null,
            logoUrl = null,
            enabled = true,
            manifestJson = "{}",
            addedAt = 100L,
        )
        dao.insert(userRow)
        assertEquals(1, dao.all().size)

        // Tohumlama çalışınca yalnızca eksik olan (AltyazıTR) eklenmeli
        val seeded = DefaultSubtitleAddons.seedIfNeeded(dao, store)
        assertTrue(seeded)
        assertEquals(2, dao.all().size)

        // Kullanıcının eklediği satır bozulmamış olmalı
        val dbEntry = dao.all().first { it.sourceUrl == DefaultSubtitleAddons.ALTYAZIDB_URL }
        assertEquals("Kullanıcı AltyazıDB", dbEntry.name)
        assertEquals(100L, dbEntry.addedAt)
    }
}
