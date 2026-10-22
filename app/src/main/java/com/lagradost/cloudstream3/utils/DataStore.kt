package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral

/*
 * CloudStream uygulama katmanındaki `com.lagradost.cloudstream3.utils.DataStore` ile ikili (ABI) uyumlu köprü.
 * Eklentiler bu nesneye adla ve imzayla bağlanır: `CloudStreamApp.getKey/setKey` satır içi (inline) olduğu için
 * eklentinin içine derlenir ve doğrudan `DataStore.getSharedPrefs(Context)` çağırır. Değerler gerçek CloudStream'deki
 * gibi `rebuild_preference` dosyasında JSON metni olarak tutulur; böylece eklentinin kendi yazdığını kendisi okur.
 * Kaynak: recloudstream/cloudstream (GPL-3.0), app/.../utils/DataStore.kt — Play'in ihtiyacına göre sadeleştirildi.
 */

const val PREFERENCES_NAME = "rebuild_preference"

class Editor(private val editor: SharedPreferences.Editor) {
    fun <T> setKeyRaw(path: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        if (isStringSet(value)) {
            editor.putStringSet(path, value as Set<String>)
        } else {
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
            }
        }
    }

    private fun isStringSet(value: Any?): Boolean =
        value is Set<*> && value.filterIsInstance<String>().size == value.size

    fun apply() {
        editor.apply()
        System.gc()
    }
}

object DataStore {

    private fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun Context.getSharedPrefs(): SharedPreferences = getPreferences(this)

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun editor(context: Context, isEditingAppSettings: Boolean = false): Editor {
        val editor: SharedPreferences.Editor =
            if (isEditingAppSettings) context.getDefaultSharedPrefs().edit() else context.getSharedPrefs().edit()
        return Editor(editor)
    }

    /** CloudStream'de uygulama ayarlarıdır; Play'de eklentilere ayrı bir dosya verilir, Play'in ayarlarına dokunulmaz. */
    fun Context.getDefaultSharedPrefs(): SharedPreferences =
        getSharedPreferences("${PREFERENCES_NAME}_default", Context.MODE_PRIVATE)

    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return getSharedPrefs().all.keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean = containsKey(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) prefs.edit { remove(path) }
        } catch (_: Exception) {
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        return try {
            getSharedPrefs().edit { keys.forEach { remove(it) } }
            keys.size
        } catch (_: Exception) {
            0
        }
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPrefs().edit { putString(path, value?.toJsonLiteral()) }
        } catch (_: Exception) {
        }
    }

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? = try {
        val json: String = getSharedPrefs().getString(path, null) ?: return null
        parseJson(json, valueType.kotlin)
    } catch (_: Exception) {
        null
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> String.toKotlinObject(): T = parseJson(this)

    fun <T : Any> String.toKotlinObject(valueType: Class<T>): T = parseJson(this, valueType.kotlin)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? = try {
        val json: String = getSharedPrefs().getString(path, null) ?: return defVal
        parseJson<T>(json)
    } catch (_: Exception) {
        null
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal
}
