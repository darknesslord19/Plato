package com.kaan.play.data.health

import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Denetlenen kaynağın türü (arayüzde kısa etiket). */
enum class HealthKind { Addon, NuvioRepo, CsRepo, CsPlugin }

/**
 * Sonuç: Çalışıyor; Yavaş (yanıt verdi ama geç); Yanıt vermiyor (ağ hatası, zaman aşımı, HTTP 4xx/5xx);
 * Yüklenemedi (CloudStream eklentisi bu sürümde açılamadı).
 */
enum class HealthStatus { Checking, Ok, Slow, Unreachable, LoadFailed }

data class HealthItem(
    val key: String,
    val name: String,
    val kind: HealthKind,
    val status: HealthStatus = HealthStatus.Checking,
    /** Yanıt süresi (ms); denetlenmediyse null. */
    val millis: Long? = null,
    /** HTTP durum kodu (varsa). */
    val code: Int? = null,
)

/** Android'den bağımsız kurallar (birim testli). */
object HealthRules {
    const val SLOW_MS = 4_000L
    const val TIMEOUT_MS = 10_000L
    const val PARALLEL = 6

    fun status(code: Int?, millis: Long): HealthStatus = when {
        code == null || code !in 200..399 -> HealthStatus.Unreachable
        millis > SLOW_MS -> HealthStatus.Slow
        else -> HealthStatus.Ok
    }

    /** Sorunlular üstte, sonra yavaşlar, sonra çalışanlar; aynı grupta ada göre. */
    fun sorted(items: List<HealthItem>): List<HealthItem> = items.sortedWith(
        compareBy<HealthItem> {
            when (it.status) {
                HealthStatus.LoadFailed, HealthStatus.Unreachable -> 0
                HealthStatus.Slow -> 1
                HealthStatus.Checking -> 2
                HealthStatus.Ok -> 3
            }
        }.thenBy { it.name.lowercase() },
    )

    fun problemCount(items: List<HealthItem>): Int =
        items.count { it.status == HealthStatus.Unreachable || it.status == HealthStatus.LoadFailed }
}

/**
 * Açık kaynakları denetler: Nuvio addon'ları (manifest), Nuvio ve CloudStream depoları (depo adresi) ve açık
 * CloudStream eklentileri (önce bu sürümde yükleniyor mu, sonra sitesi yanıt veriyor mu). Sonuçlar geldikçe akar.
 * Yalnızca herkese açık bir GET gider; çerez/başlık/adres günlüğe yazılmaz.
 */
class SourceHealthChecker(
    private val addonDao: AddonDao,
    private val csRepos: CsRepository,
    httpClient: OkHttpClient,
) {
    private val client = httpClient.newBuilder()
        .callTimeout(HealthRules.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    fun check(): Flow<List<HealthItem>> = channelFlow {
        val items = LinkedHashMap<String, HealthItem>()
        val targets = ArrayList<Pair<HealthItem, suspend () -> HealthItem>>()

        listOf(
            AddonType.NUVIO_ADDON to HealthKind.Addon,
            AddonType.NUVIO_PLUGIN_REPO to HealthKind.NuvioRepo,
            AddonType.CLOUDSTREAM_REPO to HealthKind.CsRepo,
        ).forEach { (type, kind) ->
            addonDao.enabledOfType(type).forEach { a ->
                val item = HealthItem("a-${a.id}", a.name, kind)
                targets += item to { probe(item, a.sourceUrl) }
            }
        }
        csRepos.enabledPlugins().forEach { p ->
            val item = HealthItem("p-${p.id}", p.name, HealthKind.CsPlugin)
            targets += item to {
                when (val r = runCatching { csRepos.loadApis(p.id) }.getOrNull()) {
                    is CsApiResult.Success -> {
                        val url = r.apis.firstOrNull()?.mainUrl
                        if (url.isNullOrBlank() || !url.startsWith("http", ignoreCase = true)) {
                            item.copy(status = HealthStatus.Ok)
                        } else {
                            probe(item, url)
                        }
                    }
                    else -> item.copy(status = HealthStatus.LoadFailed)
                }
            }
        }

        targets.forEach { (item, _) -> items[item.key] = item }
        send(HealthRules.sorted(items.values.toList()))

        val gate = Semaphore(HealthRules.PARALLEL)
        coroutineScope {
            targets.map { (item, run) ->
                async {
                    val done = gate.withPermit { runCatching { run() }.getOrElse { item.copy(status = HealthStatus.Unreachable) } }
                    synchronized(items) { items[done.key] = done }
                    send(HealthRules.sorted(synchronized(items) { items.values.toList() }))
                }
            }.awaitAll()
        }
    }

    private suspend fun probe(item: HealthItem, url: String): HealthItem = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        val code = runCatching {
            client.newCall(Request.Builder().url(url).get().build()).await().use { it.code }
        }.getOrNull()
        val ms = (System.nanoTime() - start) / 1_000_000
        item.copy(status = HealthRules.status(code, ms), millis = ms, code = code)
    }
}
