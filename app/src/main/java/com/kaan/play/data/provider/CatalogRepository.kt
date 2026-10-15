package com.kaan.play.data.provider

import android.util.Log
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.CsRuntime
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed interface CatalogResult {
    data class Success(val rows: List<CatalogRow>) : CatalogResult
    data object NoCatalogProvided : CatalogResult
    data class Error(
        val message: String? = null,
        val isBotBlocked: Boolean = false,
        val verifyUrl: String? = null,
        val verifyHost: String? = null,
    ) : CatalogResult
    data object Empty : CatalogResult
}

/**
 * Sağlayıcı merkezli ana sayfa için katalog çekme ve önbellek yöneticisi.
 */
class CatalogRepository(
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val csRepository: CsRepository,
    private val csRuntime: CsRuntime,
    private val httpClient: OkHttpClient,
    val cache: CatalogCache = CatalogCache(),
) {
    /**
     * Seçili sağlayıcıların katalog satırlarını sırayla çeker.
     * [forceRefresh] true ise bellek içi önbellek atlanır ("Yeniden dene").
     */
    suspend fun loadCatalog(
        selectedIds: Set<String>,
        forceRefresh: Boolean = false,
    ): CatalogResult = withContext(Dispatchers.IO) {
        val validIds = selectedIds.filter { it != ProviderConstants.ALL_PROVIDERS_ID }
        if (validIds.isEmpty()) return@withContext CatalogResult.Empty

        // Yalnızca Nuvio JS eklentileri mi seçili?
        val allAreNuvioPlugins = validIds.all { it.startsWith("nuvio_plugin:") }
        if (allAreNuvioPlugins) {
            return@withContext CatalogResult.NoCatalogProvided
        }

        val cacheKey = validIds.sorted().joinToString(",")
        if (!forceRefresh) {
            cache.get(cacheKey)?.let { cachedRows ->
                return@withContext if (cachedRows.isEmpty()) CatalogResult.Empty else CatalogResult.Success(cachedRows)
            }
        }

        val allRows = mutableListOf<CatalogRow>()
        var hasAtLeastOneProvider = false
        var hasError = false
        var hasBotBlocked = false
        var blockedVerifyUrl: String? = null
        var blockedVerifyHost: String? = null

        for (id in validIds) {
            when {
                id.startsWith("cs:") -> {
                    val pluginRowId = id.removePrefix("cs:").toLongOrNull() ?: continue
                    val plugin = pluginDao.byId(pluginRowId) ?: continue
                    hasAtLeastOneProvider = true
                    try {
                        val catalog = CsCatalog(pluginRowId, plugin.name, csRepository, csRuntime)
                        val rows = catalog.rows().first()
                        allRows.addAll(rows)
                    } catch (t: Throwable) {
                        Log.e(TAG, "CS katalog hatası ($id): ${t.message}", t)
                        hasError = true
                    }
                    val apis = csRepository.loadApis(pluginRowId)
                    for (api in apis) {
                        if (com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver.wasBlocked(api.mainUrl)) {
                            hasBotBlocked = true
                            val host = com.kaan.play.data.cloudstream.cloudflare.CloudflareCache.normalizeHost(api.mainUrl)
                            blockedVerifyHost = host
                            blockedVerifyUrl = com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver.getBlockedUrl(host) ?: api.mainUrl
                        }
                    }
                }
                id.startsWith("nuvio_addon:") -> {
                    val addonRowId = id.removePrefix("nuvio_addon:").toLongOrNull() ?: continue
                    val addonEntity = addonDao.byId(addonRowId) ?: continue
                    hasAtLeastOneProvider = true
                    try {
                        val manifest = PlayJson.decodeFromString(AddonManifest.serializer(), addonEntity.manifestJson)
                        val addon = Addon(
                            id = addonEntity.id,
                            type = addonEntity.type,
                            sourceUrl = addonEntity.sourceUrl,
                            manifest = manifest,
                            enabled = addonEntity.enabled,
                            addedAt = addonEntity.addedAt,
                        )
                        val catalog = NuvioAddonCatalog(addon, httpClient)
                        val rows = catalog.rows().first()
                        allRows.addAll(rows)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Nuvio addon katalog hatası ($id): ${t.message}", t)
                        hasError = true
                    }
                }
                id.startsWith("nuvio_plugin:") -> {
                    // Nuvio JS eklentileri katalog sunmaz
                }
            }
        }

        if (allRows.isNotEmpty()) {
            cache.put(cacheKey, allRows)
            CatalogResult.Success(allRows)
        } else if (hasBotBlocked && allRows.isEmpty()) {
            CatalogResult.Error(
                isBotBlocked = true,
                verifyUrl = blockedVerifyUrl,
                verifyHost = blockedVerifyHost,
            )
        } else if (hasError && allRows.isEmpty()) {
            CatalogResult.Error()
        } else {
            CatalogResult.Empty
        }
    }

    private companion object {
        const val TAG = "PlayCatalog"
    }
}
