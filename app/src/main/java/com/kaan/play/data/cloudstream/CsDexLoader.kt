package com.kaan.play.data.cloudstream

import android.content.Context
import android.util.Log
import com.kaan.play.data.net.PlayJson
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity
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

interface PluginDexLoader {
    suspend fun loadPlugin(dexFile: File, className: String): List<MainAPI>
}

class CsDexLoader(private val context: Context) : PluginDexLoader {

    private val loadLock = Mutex()

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

        return@withLock try {
            val beforeCount = APIHolder.allProviders.size
            val clazz = cl.loadClass(className)
            CommonActivity.context = context

            val apis = when {
                BasePlugin::class.java.isAssignableFrom(clazz) -> {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as BasePlugin
                    if (plugin is Plugin) {
                        try {
                            plugin.load(context)
                        } catch (_: NoSuchMethodError) {
                            plugin.load()
                        }
                    } else {
                        plugin.load()
                    }
                    APIHolder.allProviders.drop(beforeCount).distinctBy { it.name }
                }
                MainAPI::class.java.isAssignableFrom(clazz) -> {
                    val api = clazz.getDeclaredConstructor().newInstance() as MainAPI
                    APIHolder.allProviders.add(api)
                    listOf(api)
                }
                else -> {
                    Log.d(TAG, "loadPlugin: bilinmeyen eklenti tipi (${clazz.name})")
                    emptyList()
                }
            }

            Log.d(TAG, "loadPlugin: $className yüklendi -> ${apis.size} MainAPI (${apis.map { it.name }})")
            apis
        } catch (t: Throwable) {
            Log.d(TAG, "loadPlugin: sınıf yükleme/çalıştırma hatası ($className) ${t.javaClass.simpleName}: ${t.message}", t)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "PlayCs"
    }
}
