package com.kaan.play.data.provider

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * Sağlayıcıların (yalnızca kataloğu olan CloudStream ve Nuvio sağlayıcıları) listesini ve
 * seçili/sabitlenmiş durumlarını DataStore üzerinden yöneten depo.
 */
class ProviderRepository(
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val dataStore: DataStore<Preferences>,
    private val csRepository: CsRepository? = null,
) {

    val pinnedProviders: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PINNED_PROVIDERS] ?: emptySet()
    }.distinctUntilChanged()

    val selectedProviders: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[SELECTED_PROVIDERS] ?: emptySet()
    }.distinctUntilChanged()

    private val csHasCatalogCache = ConcurrentHashMap<Long, Boolean>()

    private suspend fun csPluginHasCatalog(pluginId: Long): Boolean {
        val repo = csRepository ?: return true
        csHasCatalogCache[pluginId]?.let { return it }
        val apis = runCatching { repo.loadApis(pluginId) }.getOrDefault(emptyList())
        val hasCatalog = apis.any { api ->
            api.hasMainPage || runCatching { !api.mainPage.isNullOrEmpty() }.getOrDefault(false)
        }
        csHasCatalogCache[pluginId] = hasCatalog
        return hasCatalog
    }

    private val csProviders: Flow<List<ProviderItem>> = combine(
        pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
        addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
    ) { plugins, repos ->
        plugins to repos
    }.map { (plugins, repos) ->
        val repoMap = repos.associateBy { it.id }
        plugins.filter { it.enabled && csPluginHasCatalog(it.id) }.map { p ->
            val repoName = repoMap[p.repoId]?.name.orEmpty()
            val sub = if (repoName.isNotBlank()) "CloudStream · $repoName" else "CloudStream"
            val id = "cs:${p.id}"
            ProviderItem(id = id, name = p.name, subtitle = sub, kind = ProviderKind.CloudStream)
        }
    }

    private val nuvioProviders: Flow<List<ProviderItem>> = addonDao.observeOfType(AddonType.NUVIO_ADDON).map { addons ->
        addons.filter { a ->
            if (!a.enabled) return@filter false
            val manifest = runCatching { PlayJson.decodeFromString(AddonManifest.serializer(), a.manifestJson) }.getOrNull()
            manifest != null && manifest.catalogs.isNotEmpty()
        }.map { a ->
            val id = "nuvio_addon:${a.id}"
            ProviderItem(id = id, name = a.name, subtitle = "Nuvio · ${a.name}", kind = ProviderKind.NuvioAddon)
        }
    }

    /**
     * Açık (enabled=true) olan tüm CloudStream ve Nuvio sağlayıcılarının birleşik listesi.
     * Sabitlenenler en üstte, ardından alfabetik sıralı gelir.
     */
    val allProviders: Flow<List<ProviderItem>> = combine(
        csProviders,
        nuvioProviders,
        pinnedProviders,
    ) { cs, nuvio, pinned ->
        val combined = cs + nuvio
        ProviderRules.applyPinningAndSort(combined, pinned)
    }

    suspend fun setSelectedProviders(selected: Set<String>) {
        dataStore.edit { prefs ->
            val filtered = selected - ProviderConstants.ALL_PROVIDERS_ID
            if (filtered.isEmpty()) {
                prefs.remove(SELECTED_PROVIDERS)
            } else {
                prefs[SELECTED_PROVIDERS] = filtered
            }
        }
    }

    suspend fun togglePin(id: String) {
        if (id == ProviderConstants.ALL_PROVIDERS_ID) return
        dataStore.edit { prefs ->
            val current = prefs[PINNED_PROVIDERS] ?: emptySet()
            val next = if (id in current) current - id else current + id
            if (next.isEmpty()) {
                prefs.remove(PINNED_PROVIDERS)
            } else {
                prefs[PINNED_PROVIDERS] = next
            }
        }
    }

    companion object {
        private val SELECTED_PROVIDERS = stringSetPreferencesKey("selected_providers")
        private val PINNED_PROVIDERS = stringSetPreferencesKey("pinned_providers")
    }
}
