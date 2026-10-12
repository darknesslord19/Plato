package com.kaan.play.data.plugin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Eklenti ayarlarının (docs §2.4) eklenti başına saklandığı yer. Her eklentinin değerleri tek bir JSON nesnesidir
 * (`{anahtar: değer}`); `toggle` bool, `select`/`text` metin. Çalıştırıcıya `SCRAPER_SETTINGS` olarak **yalnızca kaydedilen**
 * değerler verilir; varsayılanları eklenti kendisi bilir. Anahtar `plugin_{satır kimliği}`; kimlikler yeniden kullanılmadığı için
 * kaldırılan eklentinin ayarı başkasına geçmez, yine de [clear] ile silinir.
 */
class PluginSettingsRepository(private val store: DataStore<Preferences>) {

    fun observe(pluginId: Long): Flow<Map<String, JsonElement>> =
        store.data.map { decode(it[key(pluginId)]) }.distinctUntilChanged()

    suspend fun get(pluginId: Long): Map<String, JsonElement> = observe(pluginId).first()

    /** [value] null ise anahtar silinir (varsayılana döner). */
    suspend fun set(pluginId: Long, settingKey: String, value: JsonElement?) {
        store.edit { prefs ->
            val current = decode(prefs[key(pluginId)]).toMutableMap()
            if (value == null) current.remove(settingKey) else current[settingKey] = value
            if (current.isEmpty()) prefs.remove(key(pluginId)) else prefs[key(pluginId)] = JsonObject(current).toString()
        }
    }

    suspend fun clear(pluginId: Long) {
        store.edit { it.remove(key(pluginId)) }
    }

    private fun key(pluginId: Long) = stringPreferencesKey("plugin_$pluginId")

    private fun decode(raw: String?): Map<String, JsonElement> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            (PlayJson.parseToJsonElement(raw) as? JsonObject)?.filterValues { it is JsonPrimitive } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap() // bozuk kayıt varsayılana düşer
        }
    }
}
