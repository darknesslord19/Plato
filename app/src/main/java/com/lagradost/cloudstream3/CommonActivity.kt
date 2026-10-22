package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.utils.Event
import java.lang.ref.WeakReference

/**
 * CloudStream'in `CommonActivity` nesnesiyle ikili (ABI) uyumlu köprü (227 gerçek eklentinin 89'u kullanır).
 * Play eklentilere kendi ekranını vermez: [activity] boş kalır, böylece eklentilerin açmaya çalıştığı pencereler
 * (bağış, duyuru vb.) ve arka planda çalışırken gönderdikleri bildirimler (toast) Play'in arayüzüne karışmaz.
 * Kaynak: recloudstream/cloudstream (GPL-3.0), app/.../CommonActivity.kt — arayüz kısmı sadeleştirildi.
 */
object CommonActivity {
    private var _activity: WeakReference<Activity>? = null

    var activity: Activity?
        get() = _activity?.get()
        set(value) {
            _activity = value?.let { WeakReference(it) }
        }

    /** Play'e özgü: uygulama bağlamı (toast için). */
    var context: Context? = null

    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
        if (newActivity != null) {
            context = newActivity.applicationContext
            CloudStreamApp.context = newActivity.applicationContext
        }
    }

    val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics
    val screenWidth: Int get() = displayMetrics.widthPixels.coerceAtLeast(displayMetrics.heightPixels)
    val screenHeight: Int get() = displayMetrics.heightPixels.coerceAtMost(displayMetrics.widthPixels)
    val screenWidthWithOrientation: Int get() = displayMetrics.widthPixels
    val screenHeightWithOrientation: Int get() = displayMetrics.heightPixels

    var isPipDesired: Boolean = false
    var isInPIPMode: Boolean = false

    val onColorSelectedEvent = Event<Pair<Int, Int>>()
    val onDialogDismissedEvent = Event<Int>()

    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null
    var appliedTheme: Int = 0
    var appliedColor: Int = 0

    fun showToast(@StringRes message: Int, duration: Int? = null) {
        val ctx = activity ?: return
        showToastOn(ctx, ctx.getString(message), duration)
    }

    fun showToast(message: String?, duration: Int? = null) {
        val ctx = activity ?: return
        showToastOn(ctx, message, duration)
    }

    fun showToast(act: Activity?, @StringRes message: Int, duration: Int? = null) {
        val ctx = act ?: return
        showToastOn(ctx, ctx.getString(message), duration)
    }

    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        val ctx = act ?: return
        showToastOn(ctx, message, duration)
    }

    private fun showToastOn(ctx: Context, message: String?, duration: Int?) {
        if (message.isNullOrBlank()) return
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(ctx.applicationContext, message, duration ?: Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
            }
        }
    }

    /** Play'in dilini eklentiler değiştiremez (dil Ayarlar > Dil'den yönetilir); çağrı sessizce geçilir. */
    @Suppress("UNUSED_PARAMETER")
    fun setLocale(context: Context?, languageTag: String?) {}

    /** Play'e özgü eski köprü: sonuç ekranı açmak (Play'de yok, sessizce geçilir). */
    @JvmStatic
    fun loadResult(url: String?, apiName: String?) {}
}
