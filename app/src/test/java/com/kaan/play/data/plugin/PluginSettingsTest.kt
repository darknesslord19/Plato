package com.kaan.play.data.plugin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.plugin.runtime.PluginSettingField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Eklenti ayarlarının saklanması ve `onSettings()` düzeninin ayrıştırılması. Not: Windows'ta DataStore ikinci yazmada
 * dosyayı yeniden adlandıramaz (Android'de sorun yok); bu yüzden her testte dosyaya en çok bir kez yazılır.
 */
class PluginSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) { java.io.File(tmp.root, "plugin_settings.preferences_pb") } }
    private val repo by lazy { PluginSettingsRepository(store) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun nothingSavedMeansEmptySettings() = runBlocking {
        assertTrue(repo.get(1).isEmpty())
        assertTrue(repo.get(999).isEmpty())
    }

    @Test
    fun valuesAreStoredPerPluginAndKeepTheirTypes() = runBlocking {
        repo.set(7, "enable4K", JsonPrimitive(false))
        assertEquals(mapOf("enable4K" to JsonPrimitive(false)), repo.get(7))
        assertTrue("başka eklentiye geçmez", repo.get(8).isEmpty())
        // bool bool, metin metin kalır (eklenti `s.sortBySize === true` ile karşılaştırıyor)
        assertEquals(false, (repo.get(7).getValue("enable4K") as JsonPrimitive).content.toBooleanStrict())
    }

    @Test
    fun corruptRecordFallsBackToDefaults() = runBlocking {
        store.edit { it[stringPreferencesKey("plugin_5")] = "{not json" }
        assertTrue(repo.get(5).isEmpty())
    }

    @Test
    fun onlyPrimitivesAreKept() = runBlocking {
        store.edit { it[stringPreferencesKey("plugin_6")] = """{"a":"x","b":[1],"c":{"d":1},"e":true,"f":3}""" }
        assertEquals(mapOf("a" to JsonPrimitive("x"), "e" to JsonPrimitive(true), "f" to JsonPrimitive(3)), repo.get(6))
    }

    @Test
    fun clearingAPluginWithNothingSavedIsHarmless() = runBlocking {
        repo.clear(9)
        assertTrue(repo.get(9).isEmpty())
    }

    // ---- onSettings() düzeni ----

    @Test
    fun layoutParsesTheDocumentedFieldTypes() {
        val json = """
          {"ok":true,"layout":[
            {"type":"header","label":"Sort Order"},
            {"type":"info","label":"Max streams\nDefault: Unlimited"},
            {"type":"toggle","key":"sortBySize","label":"Sort by Size","defaultValue":false,"description":"ON — Largest first"},
            {"type":"toggle","key":"enable4K","label":"4K","defaultValue":true},
            {"type":"select","key":"max4K","label":"4K limit","defaultValue":"unlimited","options":[{"value":"1","label":"1"},{"value":"unlimited","label":"Unlimited"}]},
            {"type":"text","key":"token","isPassword":true,"placeholder":"Paste here...","description":"from the site"},
            {"type":"text","key":"name","label":"Name"},
            {"type":"toggle","label":"anahtarsız"},
            {"type":"slider","key":"x"},
            {"type":"select","key":"empty","options":[]},
            {"type":"header"},
            "metin", null
          ]}
        """
        val fields = PluginSettingField.parseLayout(json)
        assertEquals(7, fields.size) // anahtarsız toggle, bilinmeyen tür, seçeneksiz select, etiketsiz header atıldı
        assertEquals(PluginSettingField.Header("Sort Order"), fields[0])
        assertEquals(PluginSettingField.Info("Max streams\nDefault: Unlimited"), fields[1])
        assertEquals(PluginSettingField.Toggle("sortBySize", "Sort by Size", false, "ON — Largest first"), fields[2])
        assertEquals(true, (fields[3] as PluginSettingField.Toggle).default)
        val select = fields[4] as PluginSettingField.Select
        assertEquals("unlimited", select.default)
        assertEquals(listOf("1", "unlimited"), select.options.map { it.value })
        val text = fields[5] as PluginSettingField.Text
        assertTrue(text.isPassword)
        assertEquals("Paste here...", text.placeholder)
        assertEquals(null, text.label)
        assertEquals(false, (fields[6] as PluginSettingField.Text).isPassword)
    }

    @Test
    fun badLayoutOutputGivesEmptyList() {
        assertTrue(PluginSettingField.parseLayout("nope").isEmpty())
        assertTrue(PluginSettingField.parseLayout("""{"ok":true}""").isEmpty())
        assertTrue(PluginSettingField.parseLayout("""{"ok":true,"layout":"x"}""").isEmpty())
        assertTrue(PluginSettingField.parseLayout("[]").isEmpty())
    }
}
