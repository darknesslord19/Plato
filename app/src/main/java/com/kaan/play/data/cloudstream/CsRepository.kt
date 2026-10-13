package com.kaan.play.data.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Kurulu bir CloudStream deposu özeti. */
data class CsRepo(
    val id: Long,
    val sourceUrl: String,
    val name: String,
    val description: String?,
    val addedAt: Long,
    val pluginCount: Int,
)

/** Kurulu tek bir CS eklentisi. [enabled] varsayılan KAPALIDIR. */
data class InstalledCsPlugin(
    val id: Long,
    val repoId: Long,
    val pluginId: String,     // internalName
    val name: String,
    val version: String?,
    val description: String?,
    val language: String,
    val tvTypes: List<String>,
    val enabled: Boolean,
)

data class CsRepoAdded(val repo: CsRepo, val installed: Int, val skipped: Int)

/**
 * CloudStream eklenti depolarının tek kapısı (Faz 3). Depo satırı `addons` tablosunda
 * ([AddonType.CLOUDSTREAM_REPO]), eklentiler `plugins` tablosunda durur.
 * DEX dosyaları [CsDexStore] üzerinden `filesDir/cloudstream/` içinde saklanır.
 */
class CsRepository(
    private val context: Context,
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val fetcher: ManifestFetcher,
    private val httpClient: OkHttpClient,
    private val store: CsDexStore,
    private val loader: CsDexLoader,
    private val clock: () -> Long = System::currentTimeMillis,
    private val parallelDownloads: Int = 2,
) {

    val repos: Flow<List<CsRepo>> = combine(
        addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
        pluginDao.observeAll(),
    ) { rows, plugins ->
        val counts = plugins.groupingBy { it.repoId }.eachCount()
        rows.map { it.toCsRepo(counts[it.id] ?: 0) }
    }

    val plugins: Flow<List<InstalledCsPlugin>> =
        pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO)
            .map { rows -> rows.map { it.toInstalled() } }

    // ---------------------------------------------------------------------------
    // Ekle
    // ---------------------------------------------------------------------------

    /**
     * Zaten indirilmiş [raw] repo.json içeriğinden depoyu kurar.
     * Her CS eklentisi indirilip DEX ayıklanır; başarısızlar atlanır.
     * @throws AddonException
     */
    suspend fun addFetched(url: String, raw: String): CsRepoAdded {
        if (addonDao.countBySourceUrl(url) > 0) throw AddonException(Kind.AlreadyAdded)
        val root = CsRepoManifestParser.parseRoot(raw)

        // pluginLists adreslerinden eklenti listelerini paralel indir
        val allEntries = fetchAllPluginLists(root.pluginLists, url)
        if (allEntries.isEmpty()) throw AddonException(Kind.NoPlugins)

        // Depo satırını ekle
        val repoEntity = AddonEntity(
            type = AddonType.CLOUDSTREAM_REPO,
            sourceUrl = url,
            addonId = url,
            name = root.name,
            version = null,
            description = root.description,
            logoUrl = null,
            enabled = true,
            manifestJson = raw.take(8000), // manifest çok büyük olabilir
            addedAt = clock(),
        )
        val repoId = addonDao.insert(repoEntity)
        if (repoId == -1L) throw AddonException(Kind.AlreadyAdded)

        // .cs3 dosyalarını paralel indir
        val downloads = downloadAll(allEntries, repoId)
        val good = downloads.filter { it.success }

        if (good.isEmpty()) {
            addonDao.delete(repoId)
            throw AddonException(Kind.PluginFileFailed)
        }

        return CsRepoAdded(
            repo = repoEntity.copy(id = repoId).toCsRepo(good.size),
            installed = good.size,
            skipped = downloads.size - good.size,
        )
    }

    // ---------------------------------------------------------------------------
    // Kaldır
    // ---------------------------------------------------------------------------

    suspend fun remove(repoId: Long) {
        pluginDao.ofRepo(repoId).forEach { store.delete(it.id) }
        addonDao.delete(repoId)
    }

    // ---------------------------------------------------------------------------
    // Açık/kapalı
    // ---------------------------------------------------------------------------

    suspend fun setEnabled(pluginId: Long, enabled: Boolean) = pluginDao.setEnabled(pluginId, enabled)

    // ---------------------------------------------------------------------------
    // Çalışma zamanı
    // ---------------------------------------------------------------------------

    /**
     * Açık eklentiler için [MainAPI] listesini döner.
     * Her çağrıda DEX'i [PathClassLoader] ile yükler (Android sınıf yükleyici önbelleği bunu ucuzlatır).
     */
    suspend fun loadApis(pluginRowId: Long): List<MainAPI> {
        val row = pluginDao.byId(pluginRowId) ?: return emptyList()
        val dexFile = store.dexFile(pluginRowId)
        if (!dexFile.exists()) return emptyList()
        return loader.loadPlugin(dexFile, row.pluginId) // pluginId = internalName
    }

    suspend fun enabledPlugins(): List<InstalledCsPlugin> =
        pluginDao.enabledOfType(AddonType.CLOUDSTREAM_REPO).map { it.toInstalled() }

    // ---------------------------------------------------------------------------
    // İç yardımcılar
    // ---------------------------------------------------------------------------

    private suspend fun fetchAllPluginLists(listUrls: List<String>, baseUrl: String): List<CsPluginEntry> =
        withContext(Dispatchers.IO) {
            listUrls.flatMap { listUrl ->
                try {
                    val raw = httpClient.newCall(Request.Builder().url(listUrl).build()).execute()
                        .use { it.body?.string() ?: "" }
                    CsRepoManifestParser.parsePluginList(raw, listUrl)
                } catch (_: Exception) {
                    emptyList()
                }
            }.distinctBy { it.internalName }.take(CsRepoManifestParser.MAX_PLUGINS)
        }

    private class Dl(val entry: CsPluginEntry, val success: Boolean)

    private suspend fun downloadAll(entries: List<CsPluginEntry>, repoId: Long): List<Dl> {
        val gate = Semaphore(parallelDownloads)
        return coroutineScope {
            entries.map { entry ->
                async(Dispatchers.IO) {
                    gate.withPermit { downloadOne(entry, repoId) }
                }
            }.awaitAll()
        }
    }

    private suspend fun downloadOne(entry: CsPluginEntry, repoId: Long): Dl {
        return try {
            // Geçici bir id ile geçici dosya yolu belirle; eklenti satırı eklendikten sonra gerçek id alınır.
            val tempId = System.nanoTime()
            val contents = downloadCs3(entry.url, entry.fileHash, httpClient, store, tempId)

            // Veritabanı satırını ekle
            val pluginEntity = PluginEntity(
                repoId = repoId,
                pluginId = entry.internalName,    // DEX'te yüklenecek sınıf adı
                name = entry.name,
                version = entry.version.toString(),
                description = entry.description,
                filename = entry.url.substringAfterLast('/'),
                scriptUrl = entry.url,
                supportedTypes = entry.tvTypes.joinToString(","),
                contentLanguage = entry.language,
                hasSettings = false,
                sha256 = contents.sha256,
                sizeBytes = contents.sizeBytes,
                enabled = false,
                installedAt = clock(),
            )
            val rowId = pluginDao.insert(pluginEntity)
            if (rowId == -1L) {
                store.delete(tempId)
                return Dl(entry, false)
            }
            // Geçici dosyaları gerçek id ile yeniden adlandır
            val tmpDex = store.dexFile(tempId)
            val tmpMf = store.manifestFile(tempId)
            if (tmpDex.exists()) tmpDex.renameTo(store.dexFile(rowId))
            if (tmpMf.exists()) tmpMf.renameTo(store.manifestFile(rowId))
            store.setReadOnly(rowId)

            Dl(entry, true)
        } catch (_: Exception) {
            Dl(entry, false)
        }
    }

    private fun AddonEntity.toCsRepo(count: Int) = CsRepo(id, sourceUrl, name, description, addedAt, count)

    private fun PluginEntity.toInstalled() = InstalledCsPlugin(
        id = id, repoId = repoId, pluginId = pluginId, name = name,
        version = version, description = description,
        language = contentLanguage, tvTypes = supportedTypes.split(',').filter { it.isNotBlank() },
        enabled = enabled,
    )
}
