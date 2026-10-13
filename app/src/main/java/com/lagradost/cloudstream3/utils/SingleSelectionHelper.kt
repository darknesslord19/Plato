package com.lagradost.cloudstream3.utils

import android.app.Activity

/**
 * Gerçek CloudStream'de kullanıcıya bir seçim diyalogu (ör. ayna sunucu seçimi) gösterir.
 * Play'in akış toplama çalışma zamanı arka plan havuzunda çalışır ve bir `Activity` vermez
 * ([com.lagradost.cloudstream3.CommonActivity.activity] hep `null`dır); bu yüzden eklentiler bu
 * yolu normalde zaten çağırmaz (null denetimiyle atlarlar). Yine de çağrılırsa hiçbir şey açmadan
 * döner — yalnızca derleme/bağlama uyumluluğu için.
 */
object SingleSelectionHelper {
    fun showDialog(
        activity: Activity,
        list: List<*>,
        startIndex: Int,
        title: String,
        isDubbed: Boolean,
        dismissCallback: () -> Unit = {},
        selectedCallback: (Int) -> Unit,
    ) {
        dismissCallback()
    }
}
