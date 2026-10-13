package com.lagradost.cloudstream3.mvvm

/** mvvm paket stub'ları. CS eklentileri bu paketten yardımcı sınıflar kullanabilir. */

/** Yeniden denenebilir sonuç. */
sealed class Resource<T> {
    data class Success<T>(val value: T) : Resource<T>()
    data class Failure<T>(val isNetworkError: Boolean, val errorCode: Int?, val errorResponse: Any?) : Resource<T>()
    class Loading<T> : Resource<T>()
}

/** Hata bilgisi iletimi için yardımcı — no-op. */
fun logError(e: Throwable?) {
    // Play'de eklenti hatası CsRuntime tarafından yakalanıp yutulur
}

/** Sonucu ya boşsa uyarır ya da döndürür. */
fun <T> Resource<T>.some(): T? = (this as? Resource.Success)?.value

/** Hatayı konsoluna yazar — no-op. */
object DebugException : Exception("DebugException")
