package com.kaan.play.data.stream

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Birden çok kaynak sağlayıcıyı (Faz 1: addon'lar, Faz 2: eklenti kazıyıcıları) tek sağlayıcı olarak birleştirir: aynı Flow,
 * aynı kaynak listesi. Sağlayıcılar birbirini beklemeden paralel çalışır; sonuçlar hazır oldukça akar; akış hepsi bitince biter.
 */
class CompositeStreamProvider(private val providers: List<StreamProvider>) : StreamProvider {

    constructor(vararg providers: StreamProvider) : this(providers.toList())

    override fun streams(request: StreamRequest): Flow<StreamResult> = channelFlow {
        for (provider in providers) {
            launch { provider.streams(request).collect { send(it) } }
        }
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> = coroutineScope {
        providers.map { async { it.sources(request) } }.awaitAll().flatten()
    }
}
