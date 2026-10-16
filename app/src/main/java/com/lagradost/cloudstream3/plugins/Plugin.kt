package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.registerExtractor

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin

open class BasePlugin {
    var filename: String = ""
    val sourceProviders: MutableList<MainAPI> = mutableListOf()

    open fun beforeUnload() {}

    fun registerMainAPI(mainAPI: MainAPI) {
        sourceProviders += mainAPI
    }

    fun registerExtractorAPI(element: ExtractorApi) {
        registerExtractor(element)
    }
}

/**
 * CloudStream eklenti temel sınıfı. DEX içindeki giriş sınıfı bunu türetir.
 * Play bu sınıfı `PathClassLoader` ile yükler ve [load] çağırır.
 *
 * İmza gerçek CloudStream uygulama modülünden doğrulandı (`docs/cloudstream-plugin-research.md` §2.3/3.1):
 * giriş noktası `load(context: Context)`dir (bir `PluginManager` parametresi DEĞİL); eklenti
 * `registerMainAPI(...)`i doğrudan kendi üzerinden (miras alınan metotla) çağırır. Yanlış imza,
 * DEX sınıf doğrulamasında [MainAPI] hiç kaydedilmeden sessizce başarısız olmasına yol açar
 * (bkz. [com.kaan.play.data.cloudstream.CsDexLoader]).
 */
abstract class Plugin : BasePlugin() {
    /**
     * Eklentinin enjekte ettiği bağlam. CS uygulamasında `CommonActivity.activity` üzerinden gelir;
     * Play'de [com.kaan.play.data.cloudstream.CsDexLoader] tarafından ayarlanır.
     */
    var __context: Context? = null
    protected val context: Context? get() = __context

    /** Eklenti yüklendiğinde çağrılır; içinde [registerMainAPI] ile [MainAPI] örnekleri kaydedilir. */
    abstract fun load(context: Context)
}
