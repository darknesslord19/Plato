package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey

/**
 * CloudStream'in eski uygulama sınıfı (bugünkü adı [CloudStreamApp]). Eski derlenmiş eklentiler hâlâ
 * `AcraApplication.Companion` üzerinden bağlam ve ayar okur; bu köprü hepsini [CloudStreamApp]'e yönlendirir.
 */
open class AcraApplication : Application() {
    companion object {
        var context: Context?
            get() = CloudStreamApp.context
            set(value) {
                CloudStreamApp.context = value
            }

        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = context?.getKey(path, defVal)

        inline fun <reified T : Any> getKey(path: String): T? = context?.getKey(path)

        inline fun <reified T : Any> getKey(folder: String, path: String): T? = context?.getKey(folder, path)

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            context?.getKey(folder, path, defVal)

        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        fun removeKey(path: String) {
            context?.removeKey(path)
        }
    }
}
