package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.MainAPI

/**
 * CloudStream eklenti temel sınıfı. DEX içindeki giriş sınıfı bunu türetir.
 * Play bu sınıfı `PathClassLoader` ile yükler ve [load] çağırır.
 *
 * `load` içinde eklenti [registerMainAPI] ile [MainAPI] örneklerini kaydeder.
 * Play bu kayıtları [PlayPluginManager] üzerinden toplar.
 */
abstract class Plugin {
    /**
     * Eklentinin enjekte ettiği bağlam. CS uygulamasında `CommonActivity.activity` üzerinden gelir;
     * Play'de [PlayPluginManager] tarafından ayarlanır.
     */
    var __context: Context? = null
    protected val context: Context? get() = __context

    /** Eklenti yüklendiğinde çağrılır. [manager] üzerinden [MainAPI] nesneleri kaydedilir. */
    abstract fun load(manager: PluginManager)

    /** Kaynaklar serbest bırakılırken çağrılır (opsiyonel). */
    open fun beforeUnload() {}
}

/** Play'in yakalamak için kullandığı `PluginManager` stub'ı. */
interface PluginManager {
    fun registerMainAPI(mainAPI: MainAPI)
}

/**
 * Plugin yüklenirken [MainAPI] kayıtlarını toplayan uygulama.
 * Her eklenti yüklemesinde sıfırdan oluşturulur; kaydedilen API'ler [registrations]'ta tutulur.
 */
class PlayPluginManager(private val context: Context) : PluginManager {
    val registrations = mutableListOf<MainAPI>()

    override fun registerMainAPI(mainAPI: MainAPI) {
        registrations += mainAPI
    }
}
