package com.kaan.play.data.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Ana sayfa düzeni DataStore'da tek JSON metin olarak (`home_layout_v1`). Okunamazsa varsayılan düzen. */
class HomeLayoutStore(private val store: DataStore<Preferences>) {

    val layout: Flow<List<HomeSection>> = store.data
        .map { HomeLayoutRules.decode(it[KEY]) }
        .distinctUntilChanged()

    suspend fun update(transform: (List<HomeSection>) -> List<HomeSection>) {
        store.edit { prefs ->
            val current = HomeLayoutRules.decode(prefs[KEY])
            prefs[KEY] = HomeLayoutRules.encode(HomeLayoutRules.normalize(transform(current)))
        }
    }

    suspend fun reset() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("home_layout_v1")
    }
}
