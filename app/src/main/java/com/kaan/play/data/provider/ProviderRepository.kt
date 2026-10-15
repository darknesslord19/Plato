package com.kaan.play.data.provider

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Sağlayıcıların (CloudStream eklentileri, Nuvio addon ve eklentileri) listesini ve
 * seçili/sabitlenmiş durumlarını DataStore üzerinden yöneten depo.
 */
class ProviderRepository(
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val dataStore: DataStore<Preferences>,
) {

    val pinnedProviders: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PINNED_PROVIDERS] ?: emptySet()
    }.distinctUntilChanged()

    val selectedProviders: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[SELECTED_PROVIDERS] ?: emptySet()
    }.distinctUntilChanged()

    private val csProviders: Flow<List<ProviderItem>> = combine(
        pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
        addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
    ) { plugins, repos ->
        val repoMap = repos.associateBy { it.id }
        plugins.filter { it.enabled }.map { p ->
            val repoName = repoMap[p.repoId]?.name.orEmpty()
            val sub = if (repoName.isNotBlank()) "CloudStream · $repoName" else "CloudStream"
            val id = "cs:${p.id}"
            ProviderItem(id = id, name = p.name, subtitle = sub, kind = ProviderKind.CloudStream)
        }
    }

    private val nuvioProviders: Flow<List<ProviderItem>> = combine(
        addonDao.observeOfType(AddonType.NUVIO_ADDON),
        pluginDao.observeOfType(AddonType.NUVIO_PLUGIN_REPO),
        addonDao.observeOfType(AddonType.NUVIO_PLUGIN_REPO),
    ) { addons, plugins, repos ->
        val repoMap = repos.associateBy { it.id }
        val addonItems = addons.filter { it.enabled }.map { a ->
            val id = "nuvio_addon:${a.id}"
            ProviderItem(id = id, name = a.name, subtitle = "Nuvio · ${a.name}", kind = ProviderKind.NuvioAddon)
        }
        val pluginItems = plugins.filter { it.enabled }.map { p ->
            val repoName = repoMap[p.repoId]?.name.orEmpty()
            val sub = if (repoName.isNotBlank()) "Nuvio · $repoName" else "Nuvio"
            val id = "nuvio_plugin:${p.id}"
            ProviderItem(id = id, name = p.name, subtitle = sub, kind = ProviderKind.NuvioPlugin)
        }
        addonItems + pluginItems
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
