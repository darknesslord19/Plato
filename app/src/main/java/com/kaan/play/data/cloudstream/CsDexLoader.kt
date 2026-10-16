package com.kaan.play.data.cloudstream

import android.content.Context
import android.util.Log
import com.kaan.play.data.net.PlayJson
import com.lagradost.cloudstream3.APIHolder
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

/**
 * `.cs3` içindeki `manifest.json`'da yüklenecek sınıfın gerçek adı `pluginClassName` alanındadır;
 * depo düzeyindeki (`plugins.json`) `internalName` yalnızca bir kimliktir ve sınıf adıyla
 * aynı olmak zorunda değildir (gerçek depolarda genelde farklıdır, örn. `DailymotionProvider` →
 * `recloudstream.DailymotionPlugin`). `pluginClassName` yoksa/okunamazsa [fallbackInternalName]'e döner.
 */
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

/**
 * CloudStream DEX eklentisi yükleyicisi.
 * `.cs3` ZIP'inden çıkarılan `classes.dex` dosyasını `PathClassLoader` ile yükler,
 * `Plugin` giriş sınıfını bulup `load(context)` çağırır ve kayıtlı [MainAPI] örneklerini döner.
 *
 * CS eklentileri iki farklı desende yazar:
 *  - A: `internalName` → `Plugin` alt sınıfı → `load(context)` içinde `registerMainAPI()`
 *  - B: `internalName` → doğrudan `MainAPI` alt sınıfı
 *
 * Her iki durum da ele alınır.
 *
 * Thread-safety: [APIHolder] global durum tuttuğundan aynı anda tek yükleme izin verilir ([loadLock]).
 * Gerçek eklentiler host'ta olmayan sınıf/yöntemlere başvurabilir; bu durumda ART `Exception` değil
 * `Error` (ör. `NoSuchMethodError`, `NoClassDefFoundError`, `VerifyError`) fırlatır, bu yüzden
 * `Throwable` yakalanır.
 */
interface PluginDexLoader {
    suspend fun loadPlugin(dexFile: File, className: String): List<MainAPI>
}

class CsDexLoader(private val context: Context) : PluginDexLoader {

    /** [APIHolder] globali yalnızca tek iş parçacığından değiştirilir. */
    private val loadLock = Mutex()

    /**
     * [dexFile] üzerinden DEX yükler ve kayıtlı [MainAPI] örneklerini döner.
     * [className]: yüklenecek giriş sınıfının tam adı (`.cs3` manifest'indeki `pluginClassName`;
     * bkz. [resolveMainClassName] — repo düzeyindeki `internalName` ile aynı olmak zorunda değildir).
     * @return Boş liste olabilir (eklenti `MainAPI` kaydetmediyse ya da yükleme başarısızsa).
     */
    override suspend fun loadPlugin(dexFile: File, className: String): List<MainAPI> = loadLock.withLock {
        if (!dexFile.exists()) {
            Log.d(TAG, "loadPlugin: dex yok ($className)")
            return@withLock emptyList()
        }

        val cl = try {
            PathClassLoader(dexFile.absolutePath, context.classLoader)
        } catch (t: Throwable) {
            Log.d(TAG, "loadPlugin: PathClassLoader hata ($className) ${t.javaClass.simpleName}: ${t.message}")
            return@withLock emptyList()
        }

        APIHolder.clear()

        return@withLock try {
            val clazz = cl.loadClass(className)
            val apis = when {
                // Desen A: BasePlugin (veya Plugin) alt sınıfı
                BasePlugin::class.java.isAssignableFrom(clazz) -> {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as BasePlugin
                    if (plugin is Plugin) {
                        plugin.__context = context
                    }
                    runCatching {
                        clazz.getMethod("load", Context::class.java).invoke(plugin, context)
                    }.recoverCatching {
                        clazz.getMethod("load").invoke(plugin)
                    }
                    // Hem plugin.sourceProviders hem APIHolder üzerinden kayıtları topla
                    (plugin.sourceProviders + APIHolder.allProviders).distinctBy { it.name }
                }
                // Desen B: Doğrudan MainAPI alt sınıfı
                MainAPI::class.java.isAssignableFrom(clazz) -> {
                    listOf(clazz.getDeclaredConstructor().newInstance() as MainAPI)
                }
                else -> emptyList()
            }
            Log.d(TAG, "loadPlugin: $className -> ${apis.size} MainAPI")
            apis
        } catch (t: Throwable) {
            Log.d(TAG, "loadPlugin: $className yükleme hatası ${t.javaClass.simpleName}: ${t.message}", t)
            emptyList()
        } finally {
            APIHolder.clear()
        }
    }

    private companion object {
        const val TAG = "PlayCs"
    }
}
