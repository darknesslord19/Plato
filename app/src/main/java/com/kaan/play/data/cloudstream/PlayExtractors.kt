package com.kaan.play.data.cloudstream

import com.kaan.play.data.cloudstream.extractors.ByseJikuar
import com.kaan.play.data.cloudstream.extractors.JapierdoleExtractor
import com.kaan.play.data.cloudstream.extractors.PichiveExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis

/**
 * Play'in düzelttiği çıkarıcılar (Pichive/Hotlinger/DPlayer, Japierdole, ByseJikuar) CloudStream'in genel çıkarıcı
 * listesinin **başına** bir kez konur ve aynı sitelere ait eski sürümler çıkarılır.
 *
 * Eskiden bu iş her `loadLinks` çağrısında yapılıyordu: paralel çalışan eklentiler aynı genel listeyi aynı anda
 * değiştiriyordu. Artık açılışta ve her eklenti yüklendikten sonra (eklenti kendi eski sürümünü kaydetmiş olabilir)
 * çağrılır; çağrılar arasında değişiklik yoksa hiçbir şey yapmaz.
 */
object PlayExtractors {

    private val overriddenHosts = listOf("hotlinger", "pichive", "dplayer", "japierdolevid", "bysejikuar")

    val custom: List<ExtractorApi> by lazy {
        listOf(
            PichiveExtractor("Pichive", "https://four.pichive.online"),
            PichiveExtractor("Pichive", "https://pichive.online"),
            PichiveExtractor("Hotlinger", "https://sn.hotlinger.com"),
            PichiveExtractor("Hotlinger", "https://hotlinger.com"),
            PichiveExtractor("DPlayer", "https://four.dplayer82.site"),
            PichiveExtractor("DPlayer", "https://sn.dplayer82.site"),
            PichiveExtractor("DPlayer", "https://dplayer82.site"),
            JapierdoleExtractor("Japierdole", "https://japierdolevid.com"),
            ByseJikuar(),
        )
    }

    @Synchronized
    fun install() {
        val mine = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ExtractorApi, Boolean>())
        mine.addAll(custom)
        val stale = extractorApis.filter { api ->
            api !in mine && overriddenHosts.any { api.mainUrl.contains(it, ignoreCase = true) }
        }
        val inFront = extractorApis.take(custom.size).let { head -> head.size == custom.size && head.zip(custom).all { (a, b) -> a === b } }
        if (stale.isEmpty() && inFront) return
        extractorApis.removeAll { it in mine || stale.any { s -> s === it } }
        custom.asReversed().forEach { extractorApis.add(0, it) }
    }
}
