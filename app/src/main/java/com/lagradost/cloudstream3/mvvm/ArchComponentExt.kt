package com.lagradost.cloudstream3.mvvm

import android.util.Log

fun logError(e: Throwable?) {
    if (e != null) {
        Log.w("PlayCs", "Plugin logError", e)
    }
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): T? = try {
    apiCall()
} catch (e: Throwable) {
    logError(e)
    null
}
