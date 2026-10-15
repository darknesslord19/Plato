package com.kaan.play.data.subtitle.translate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleDiag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google ML Kit cihaz üstü çeviri (İngilizce → Türkçe). Çeviri cihazda yapılır, altyazı metni sunucuya gönderilmez; yalnızca
 * dil modeli (bir kez, ~30 MB) Google'dan iner. Çeviri örneği her iş için açılıp kapatılır (yerel bellek tutulmasın).
 */
class MlKitTranslationEngine(
    context: Context,
    private val enabledSetting: suspend () -> Boolean,
    private val wifiOnlySetting: suspend () -> Boolean,
) : TranslationEngine {

    private val appContext = context.applicationContext

    override suspend fun enabled(): Boolean = enabledSetting()

    override suspend fun isModelReady(): Boolean = try {
        val manager = RemoteModelManager.getInstance()
        listOf(TranslateLanguage.ENGLISH, TranslateLanguage.TURKISH).all { code ->
            manager.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    override suspend fun prepareModel(): ModelPrep {
        val wifiOnly = wifiOnlySetting()
        if (wifiOnly && !isUnmetered()) return ModelPrep.NeedsWifi
        val translator = newTranslator()
        return try {
            val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
            translator.downloadModelIfNeeded(conditions).await()
            ModelPrep.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SubtitleDiag.d("çeviri modeli indirilemedi: ${e::class.java.simpleName}")
            ModelPrep.Failed
        } finally {
            translator.close()
        }
    }

    override suspend fun translate(cues: List<SubtitleCue>, onProgress: (Int) -> Unit): List<SubtitleCue> {
        val translator = newTranslator()
        try {
            return SubtitleTranslation.translateCues(cues, { text -> translator.translate(text).await() }, onProgress = onProgress)
        } finally {
            translator.close()
        }
    }

    private fun newTranslator(): Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build(),
    )

    private fun isUnmetered(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
