package com.kaan.play.data.cloudstream

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.kaan.play.data.net.PlayJson
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.PathClassLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal fun resolveMainClassName(manifestJson: String?, fallbackInternalName: String): String {
    if (manifestJson.isNullOrBlank()) return fallbackInternalName
    return try {
        val obj = PlayJson.decodeFromString<JsonElement>(manifestJson) as? JsonObject
        (obj?.get("pluginClassName") as? JsonPrimitive)?.content?.trim()?.ifEmpty { null }
            ?: fallbackInternalName
    } catch (_: Exception) {
        fallbackInternalName
    }
}

/** Manifest'teki `requiresResources` (eklenti kendi Android kaynaklarını kullanır). */
internal fun manifestRequiresResources(manifestJson: String?): Boolean {
    if (manifestJson.isNullOrBlank()) return false
    return try {
        val obj = PlayJson.decodeFromString<JsonElement>(manifestJson) as? JsonObject
        (obj?.get("requiresResources") as? JsonPrimitive)?.content?.trim()?.equals("true", ignoreCase = true) == true
    } catch (_: Exception) {
        false
    }
}

interface PluginDexLoader {
    suspend fun loadPlugin(dexFile: File, className: String): List<MainAPI>

    /** [manifestJson] eklentinin kendi manifest.json'ı; varsayılan uygulama yalnızca sınıfı yükler. */
    suspend fun loadPlugin(file: File, className: String, manifestJson: String?): List<MainAPI> =
        loadPlugin(file, className)

    /** Dosya silindi/değişti: önbellekteki yüklü örnek atılır. */
    fun evict(file: File) {}
}

/**
 * CloudStream eklentisi yükleyici (gerçek CloudStream'deki `PluginManager.loadPlugin` ile aynı adımlar):
 * `PathClassLoader` → manifest'teki sınıf → örnek → `filename` → (gerekirse) kaynaklar → `load(context)`.
 *
 * **Her eklenti dosyası süreç başına bir kez yüklenir** ve örnekleri önbellekte tutulur. Eskiden her arama/katalog
 * çağrısında yeniden yükleniyordu: her seferinde yeni sınıf yükleyici, `load()` yeniden çalışıyor ve
 * `APIHolder.allProviders` ile çıkarıcı listesi şişiyordu; eklentinin kendi durumu (ör. bulduğu güncel alan adı) da
 * her çağrıda kayboluyordu. Başarısız yükleme kısa süre ([FAILURE_RETRY_MS]) sonra yeniden denenir.
 */
class CsDexLoader(private val context: Context) : PluginDexLoader {

    private val loadLock = Mutex()

    private class Loaded(val stamp: String, val apis: List<MainAPI>)
    private val cache = ConcurrentHashMap<String, Loaded>()
    private val failures = ConcurrentHashMap<String, Pair<String, Long>>()

    /** Son yükleme hatası (eklenti dosyası yolu → "Sınıf: ileti"); teşhis ekranı için. */
    val lastErrors: Map<String, String> get() = failures.mapValues { it.value.first }

    override suspend fun loadPlugin(dexFile: File, className: String): List<MainAPI> =
        loadPlugin(dexFile, className, null)

    override fun evict(file: File) {
        cache.remove(file.absolutePath)
        failures.remove(file.absolutePath)
    }

    override suspend fun loadPlugin(file: File, className: String, manifestJson: String?): List<MainAPI> {
        val key = file.absolutePath
        val stamp = "${file.length()}:${file.lastModified()}:$className"
        cache[key]?.takeIf { it.stamp == stamp }?.let { return it.apis }
        failures[key]?.let { (_, at) -> if (System.currentTimeMillis() - at < FAILURE_RETRY_MS) return emptyList() }

        return loadLock.withLock {
            cache[key]?.takeIf { it.stamp == stamp }?.let { return@withLock it.apis }
            val apis = loadUncached(file, className, manifestJson)
            if (apis.isNotEmpty()) {
                cache[key] = Loaded(stamp, apis)
                failures.remove(key)
            }
            apis
        }
    }

    private fun loadUncached(file: File, className: String, manifestJson: String?): List<MainAPI> {
        val key = file.absolutePath
        fun fail(t: Throwable?, what: String): List<MainAPI> {
            val reason = t?.let { "${it.javaClass.name}: ${it.message}" } ?: what
            failures[key] = reason to System.currentTimeMillis()
            Log.w(TAG, "loadPlugin: $className yüklenemedi ($what) $reason", t)
            return emptyList()
        }
        if (!file.exists()) return fail(null, "dosya yok")
        try { file.setReadOnly() } catch (_: Throwable) {} // Android 14+: yazılabilir dex yüklenmez

        val cl = try {
            PathClassLoader(file.absolutePath, context.classLoader)
        } catch (t: Throwable) {
            return fail(t, "PathClassLoader")
        }

        return try {
            val before = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MainAPI, Boolean>())
            before.addAll(APIHolder.allProviders)
            val clazz = cl.loadClass(className)
            CommonActivity.context = context.applicationContext
            if (CloudStreamApp.context == null) CloudStreamApp.context = context.applicationContext

            val apis = when {
                BasePlugin::class.java.isAssignableFrom(clazz) -> {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as BasePlugin
                    plugin.filename = file.absolutePath
                    if (plugin is Plugin) {
                        if (manifestRequiresResources(manifestJson)) attachResources(plugin, file)
                        try {
                            plugin.load(context)
                        } catch (_: NoSuchMethodError) {
                            plugin.load()
                        }
                    } else {
                        plugin.load()
                    }
                    APIHolder.allProviders.filter { it !in before }.distinctBy { it.name }
                }
                MainAPI::class.java.isAssignableFrom(clazz) -> {
                    val api = clazz.getDeclaredConstructor().newInstance() as MainAPI
                    APIHolder.allProviders.add(api)
                    listOf(api)
                }
                else -> return fail(null, "bilinmeyen eklenti tipi ${clazz.name}")
            }
            // Eklenti kendi çıkarıcılarını kaydetmiş olabilir: Play'in düzeltilmiş çıkarıcıları yine önde kalsın.
            PlayExtractors.install()
            // Gerçek CloudStream eklentiler yüklendikten sonra bu olayı tetikler; bazı eklentiler hazırlığını burada yapar.
            try { MainActivity.afterPluginsLoadedEvent.invoke(false) } catch (_: Throwable) {}

            Log.d(TAG, "loadPlugin: $className yüklendi -> ${apis.size} MainAPI (${apis.map { it.name }})")
            if (apis.isEmpty()) fail(null, "eklenti hiç sağlayıcı kaydetmedi") else apis
        } catch (t: Throwable) {
            fail(t, "sınıf yükleme/çalıştırma")
        }
    }

    /**
     * `requiresResources` eklentileri kendi düzen/görsel kaynaklarını kullanır. Gerçek CloudStream'deki gibi .cs3
     * dosyası bir AssetManager'a eklenir. Yalnızca dosyanın tamamı (.cs3) saklanmışsa mümkündür; eski kurulumlarda
     * yalnızca classes.dex vardır, o durumda kaynaklar boş kalır.
     */
    private fun attachResources(plugin: Plugin, file: File) {
        if (!file.name.endsWith(".cs3")) return
        try {
            val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
            AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, file.absolutePath)
            @Suppress("DEPRECATION")
            plugin.resources = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
        } catch (t: Throwable) {
            Log.w(TAG, "attachResources: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "PlayCs"
        const val FAILURE_RETRY_MS = 60_000L
    }
}
