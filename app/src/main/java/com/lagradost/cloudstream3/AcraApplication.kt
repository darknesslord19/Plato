package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context

/**
 * CloudStream'in uygulama temel sınıfı stub'ı. Eklentiler bu sınıftan erişim almaya çalışabilir.
 * Play'de gerçek bir işlevi yoktur; yalnızca DEX yükleme sırasında sınıf çözünürlüğü sağlar.
 */
open class AcraApplication : Application() {

    companion object {
        /**
         * Uygulama bağlamı. CS'in dahili kullanımı için; Play, [com.lagradost.cloudstream3.CommonActivity.context]
         * üzerinden gerçek bağlamı sağlar.
         */
        @JvmStatic
        var context: Context? = null
            internal set
    }
}

/** CloudStream uygulama singleton stub'ı. */
object CloudStreamApp {
    @JvmStatic
    var context: Context? = null
        internal set
}
