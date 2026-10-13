package com.lagradost.cloudstream3

import android.content.Context

/**
 * CloudStream'in aktivite erişimi için stub'ı. CS eklentileri zaman zaman `CommonActivity.activity`
 * üzerinden bağlam ya da intent işlevleri almaya çalışır. Play bu sınıfı stub olarak sağlar;
 * `activity` null'dır ve eklentiler null'a karşı savunmalı yazılmışsa sorun çıkmaz.
 */
object CommonActivity {
    /**
     * Gerçek bir aktivite referansı değildir. `@JvmField` KULLANILMAZ: gerçek eklentiler
     * `CommonActivity.activity` alanına değil `getActivity()` yöntemine derlenmiştir
     * (Kotlin özelliği, alan değil).
     */
    var activity: android.app.Activity? = null

    /** [AcraApplication.context] ile senkronizedir; CS stubs başlatılırken ayarlanır. */
    var context: Context? = null

    // İçerik önerisi (CS eklentileri içerik önerisini bu kanal üzerinden bildirir; Play yok sayar)
    @JvmStatic
    fun showToast(msg: String?, duration: Int = 0) {}

    @JvmStatic
    fun loadResult(url: String?, apiName: String?) {}
}
