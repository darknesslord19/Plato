package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.widget.Toast
import java.lang.ref.WeakReference

/**
 * CloudStream'in aktivite erişim sınıfı.
 * Eklentiler `CommonActivity.activity` üzerinden geçerli etkinliğe erişir.
 */
object CommonActivity {
    private var _activity: WeakReference<Activity>? = null

    var activity: Activity?
        get() = _activity?.get()
        set(value) {
            _activity = value?.let { WeakReference(it) }
        }

    var context: Context? = null

    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
        if (newActivity != null) {
            context = newActivity.applicationContext
            AcraApplication.context = newActivity.applicationContext
            CloudStreamApp.context = newActivity.applicationContext
        }
    }

    @JvmStatic
    fun showToast(msg: String?, duration: Int? = null) {
        val act = activity ?: return
        if (msg == null) return
        act.runOnUiThread {
            Toast.makeText(act, msg, duration ?: Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun loadResult(url: String?, apiName: String?) {}
}
