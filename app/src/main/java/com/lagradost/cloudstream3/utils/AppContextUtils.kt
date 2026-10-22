package com.lagradost.cloudstream3.utils

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog

/**
 * CloudStream'in `AppContextUtils` nesnesinden eklentilerin kullandığı küçük parça (ikili uyumlu).
 * Play'de eklentiler sonuç ekranı açamaz ya da pencere gösteremez; bu çağrılar sessizce geçilir.
 */
object AppContextUtils {
    fun loadResult(url: String, apiName: String, name: String = "", startAction: Int = 0, startValue: Int = 0) {}

    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_POSITIVE) {
        try {
            getButton(buttonFocus)?.requestFocus()
        } catch (_: Throwable) {
        }
    }
}
