package com.kaan.play.data.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.PlayPluginManager
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.PathClassLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * CloudStream DEX eklentisi yükleyicisi.
 * `.cs3` ZIP'inden çıkarılan `classes.dex` dosyasını `PathClassLoader` ile yükler,
 * `Plugin` giriş sınıfını bulup `load()` çağırır ve kayıtlı [MainAPI] örneklerini döner.
 *
 * CS eklentileri iki farklı desende yazar:
 *  - A: `internalName` → `Plugin` alt sınıfı → `load()` içinde `registerMainAPI()`
 *  - B: `internalName` → doğrudan `MainAPI` alt sınıfı
 *
 * Her iki durum da ele alınır.
 *
 * Thread-safety: [APIHolder] global durum tuttuğundan aynı anda tek yükleme izin verilir ([loadLock]).
 */
class CsDexLoader(private val context: Context) {

    /** [APIHolder] globali yalnızca tek iş parçacığından değiştirilir. */
    private val loadLock = Mutex()

    /**
     * [dexFile] üzerinden DEX yükler ve kayıtlı [MainAPI] örneklerini döner.
     * [internalName]: `.cs3` manifest'inde belirtilen giriş sınıfı adı.
     * @return Boş liste olabilir (eklenti `MainAPI` kaydetmediyse ya da yükleme başarısızsa).
     */
    suspend fun loadPlugin(dexFile: File, internalName: String): List<MainAPI> = loadLock.withLock {
        if (!dexFile.exists()) return@withLock emptyList()

        val cl = try {
            PathClassLoader(dexFile.absolutePath, context.classLoader)
        } catch (e: Exception) {
            return@withLock emptyList()
        }

        APIHolder.clear()

        return@withLock try {
            val clazz = cl.loadClass(internalName)
            when {
                // Desen A: Plugin alt sınıfı
                Plugin::class.java.isAssignableFrom(clazz) -> {
                    val plugin = clazz.getDeclaredConstructor().newInstance() as Plugin
                    plugin.__context = context
                    val manager = PlayPluginManager(context)
                    plugin.load(manager)
                    // Hem PlayPluginManager hem APIHolder üzerinden kayıtları topla
                    (manager.registrations + APIHolder.allProviders).distinctBy { it.name }
                }
                // Desen B: Doğrudan MainAPI alt sınıfı
                MainAPI::class.java.isAssignableFrom(clazz) -> {
                    listOf(clazz.getDeclaredConstructor().newInstance() as MainAPI)
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            APIHolder.clear()
        }
    }
}
