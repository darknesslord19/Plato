package com.lagradost.api

object Log {
    @JvmStatic
    fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
    }

    @JvmStatic
    fun i(tag: String, msg: String) {
        android.util.Log.i(tag, msg)
    }

    @JvmStatic
    fun w(tag: String, msg: String) {
        android.util.Log.w(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        android.util.Log.e(tag, msg)
    }
}
