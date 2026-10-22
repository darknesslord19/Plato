package com.lagradost.cloudstream3

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import com.lagradost.api.setContext
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.lang.ref.WeakReference

/**
 * CloudStream'in uygulama sınıfıyla ikili (ABI) uyumlu köprü. Eklentiler `CloudStreamApp.Companion` alanına ve
 * yardımcı nesnenin üyelerine (`getContext`, `setKey`, satır içi `getKey`) bağlanır; bu yüzden bu sınıf gerçek
 * CloudStream'deki gibi bir `Application` alt sınıfıdır ve API'si **yardımcı nesnededir** (düz `object` olursa
 * eklenti `NoClassDefFoundError: CloudStreamApp$Companion` alır ve hiç yüklenmez).
 *
 * Play bu sınıfı uygulama olarak kullanmaz (Play'in uygulaması `PlayApp`); yalnızca [context] Play tarafından atanır.
 * Kaynak: recloudstream/cloudstream (GPL-3.0), app/.../CloudStreamApp.kt.
 */
class CloudStreamApp : Application() {
    companion object {
        /** Use to get Activity from Context. */
        tailrec fun Context.getActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.getActivity()
            else -> null
        }

        private var _context: WeakReference<Context>? = null

        /** Uygulama bağlamı. Gerçek CloudStream'de ayarlayıcı gizlidir; Play açılışta atar (ABI'yi etkilemez). */
        var context: Context?
            get() = _context?.get()
            set(value) {
                _context = value?.let { WeakReference(it) }
                try {
                    setContext(value)
                } catch (_: Throwable) {
                }
            }

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? = context?.getKey(path, valueType)

        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun removeKeys(folder: String): Int? = context?.removeKeys(folder)

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

        fun getKeys(folder: String): List<String>? = context?.getKeys(folder)

        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        fun removeKey(path: String) {
            context?.removeKey(path)
        }

        /** Bağlantıyı sistem tarayıcısında açar (Play'de WebView yedeği yoktur). */
        fun openBrowser(url: String, fallbackWebView: Boolean = false, fragment: Fragment? = null) {
            val ctx = fragment?.context ?: context ?: return
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }

        fun openBrowser(url: String, activity: Activity?) {
            openBrowser(url, false, null)
        }
    }
}
