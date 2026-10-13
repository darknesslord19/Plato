package com.lagradost.cloudstream3

/**
 * CloudStream API kayıt deposu. CS eklentileri `load()` içinde
 * `APIHolder.addAPI(mainApi)` ya da doğrudan `allProviders += mainApi` çağırabilir.
 *
 * Play bu nesneyi her eklenti yüklemesinde sıfırlayarak kayıtları toplar.
 * Kapsam: [com.kaan.play.data.cloudstream.CsDexLoader] sıfırlar ve okur.
 */
object APIHolder {
    /**
     * Kayıtlı API listesi. Her yükleme öncesi [clear] çağrılır, yükleme sonrası içerik [PlayPluginManager]'a aktarılır.
     * Thread-safety: her eklenti kendi `withLock` bloğunda çalışır ([CsDexLoader]).
     */
    @JvmField
    val allProviders: MutableList<MainAPI> = mutableListOf()

    @JvmStatic
    fun addAPI(mainApi: MainAPI) {
        allProviders += mainApi
    }

    @JvmStatic
    fun removeAPI(mainApi: MainAPI) {
        allProviders -= mainApi
    }

    /** Her eklenti yüklemesinde [CsDexLoader] çağırır. */
    internal fun clear() = allProviders.clear()

    /** Belirli isme göre bulur — CS'in dahili kullanımı; Play tarafından gerekmez. */
    @JvmStatic
    fun getApiFromName(name: String?): MainAPI? = allProviders.find { it.name == name }

    @JvmStatic
    fun getApiFromNameNull(name: String?): MainAPI? = getApiFromName(name)
}
