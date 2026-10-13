package com.kaan.play.data.cloudstream

import android.util.Log
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * CloudStream eklentilerini [StreamProvider] arayüzüne bağlar.
 *
 * Her açık eklenti için:
 *  1. [CsRepository.loadApis] ile DEX yüklenir → [MainAPI] listesi alınır.
 *  2. [CsRuntime.streams] ile arama→detay→link akışı çalıştırılır.
 *  3. Sonuçlar tek [StreamResult.Found] olarak akar; hata → [StreamResult.Failed].
 *
 * Eklentiler paralel çalışır (her biri ayrı coroutine). Başlığı [titleResolver] çözer
 * (TMDB başlık → CS eklentisinin arama sorgusuna geçilir).
 */
class CsStreamProvider(
    private val repository: CsRepository,
    private val runtime: CsRuntime,
    private val titleResolver: suspend (request: StreamRequest) -> String?,
    private val timeoutMs: Long = 15_000L,
) : StreamProvider {

    override fun streams(request: StreamRequest): Flow<StreamResult> = channelFlow {
        val title = titleResolver(request)
        if (title == null) {
            Log.d(TAG, "streams: başlık çözülemedi (TMDB), CS atlanıyor")
            return@channelFlow
        }
        val plugins = repository.enabledPlugins()
        Log.d(TAG, "streams: ${plugins.size} açık CS eklentisi")
        if (plugins.isEmpty()) return@channelFlow

        for (plugin in plugins) {
            launch {
                val source = StreamSource(addonId = plugin.id, addonName = plugin.name)
                try {
                    val apis = repository.loadApis(plugin.id)
                    Log.d(TAG, "${plugin.name}: ${apis.size} MainAPI yüklendi")
                    val streams: List<PlayableStream> = if (apis.isEmpty()) {
                        emptyList()
                    } else {
                        runtime.streams(
                            apis = apis,
                            request = request,
                            title = title,
                            pluginRowId = plugin.id,
                            pluginName = plugin.name,
                            timeoutMs = timeoutMs,
                        )
                    }
                    send(StreamResult.Found(source, streams))
                } catch (t: Throwable) {
                    Log.d(TAG, "${plugin.name}: streams hata ${t.javaClass.simpleName}: ${t.message}")
                    send(StreamResult.Failed(source, FailureReason.Network))
                }
            }
        }
    }.flowOn(Dispatchers.IO) // DEX yükleme ve eklentilerin ayrıştırma işi ana iş parçacığını dondurmasın

    override suspend fun sources(request: StreamRequest): List<StreamSource> =
        repository.enabledPlugins().map { StreamSource(addonId = it.id, addonName = it.name) }

    private companion object {
        const val TAG = "PlayCs"
    }
}
