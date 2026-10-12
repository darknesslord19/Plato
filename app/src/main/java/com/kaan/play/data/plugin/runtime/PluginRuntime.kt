package com.kaan.play.data.plugin.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.plugin.NetworkPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/** Eklentiye verilen çağrı: TMDB kimliği (metin), tür ve (dizide) sezon/bölüm. */
data class PluginRequest(
    val tmdbId: String,
    /** `movie`, `tv` ya da (manifest `series` diyorsa ve sezon+bölüm varsa) `series`. */
    val mediaType: String,
    val season: Int? = null,
    val episode: Int? = null,
)

/** Bir çalıştırmanın sonucu. Hata ya da boş sonuç eklentiyi çağıran tarafta "boş liste"dir; ayrıntı tanı içindir. */
sealed interface PluginOutcome {
    val streams: List<PluginStream>

    data class Success(override val streams: List<PluginStream>) : PluginOutcome

    data class Failure(val reason: Reason, val message: String? = null) : PluginOutcome {
        override val streams: List<PluginStream> get() = emptyList()
    }

    enum class Reason {
        /** Süre doldu (sonsuz döngü, takılan istek...). */
        Timeout,

        /** Betik hatası: söz dizimi, `getStreams` yok, atılan istisna, bellek sınırı. */
        Script,
    }
}

/**
 * Nuvio JS kazıyıcısını (docs/nuvio-plugin-format.md) QuickJS içinde çalıştırır: dosyayı yükler, `getStreams`'i belgedeki
 * imzayla çağırır, sonucu [PluginStream]'e çevirir. Yalnızca motor ve köprüler; kaynak listesine bağlama sonraki turdadır.
 *
 * **Güvenlik/kaynak sınırları**
 * - Her çağrı yeni, yalıtılmış bir QuickJS örneğidir (çağrılar ve eklentiler arası durum/çerez paylaşımı yok).
 * - Dosya sistemi, veritabanı, Intent hiçbir şekilde köprülenmez; ağ yalnızca [PluginHttp] üzerinden ve [NetworkPolicy] ile.
 * - Süre ([run] `timeoutMs`), bellek ([PluginLimits.memoryLimitBytes]), yığın, istek/bayt bütçeleri sınırlıdır.
 * - Gerçek TMDB anahtarı eklentiye verilmez ([PluginPrelude.TMDB_PLACEHOLDER], bkz. [PluginHttp]).
 * - Çalıştırma arka plan iş parçacığı havuzunda yapılır; en çok [maxConcurrent] eklenti aynı anda koşar.
 *
 * @param tmdbApiKey gerçek anahtar; yalnızca ağ köprüsünde değişim için tutulur, hiçbir yere yazılmaz.
 * @param log (düzey, **maskelenmiş** metin) eklentinin `console` çıktısı.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginRuntime(
    private val client: OkHttpClient,
    private val tmdbApiKey: String,
    private val policy: NetworkPolicy = NetworkPolicy(),
    private val limits: PluginLimits = PluginLimits(),
    maxConcurrent: Int = 4,
    /** Yalnızca testler: TMDB ana makinesi ve https zorunluluğu (yerel sahte sunucu için). */
    private val tmdbHost: String = PluginHttp.TMDB_HOST,
    private val tmdbHttpsOnly: Boolean = true,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(maxConcurrent),
    private val log: (level: String, message: String) -> Unit = { _, _ -> },
) {
    private val gate = Semaphore(maxConcurrent)

    /** Boş/hatalı durumda boş liste. */
    suspend fun getStreams(code: String, request: PluginRequest, scraperId: String = "", timeoutMs: Long = DEFAULT_TIMEOUT_MS): List<PluginStream> =
        run(code, request, scraperId, timeoutMs).streams

    suspend fun run(
        code: String,
        request: PluginRequest,
        scraperId: String = "",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): PluginOutcome = gate.withPermit {
        withContext(dispatcher) {
            try {
                withTimeout(timeoutMs) { execute(code, request, scraperId, timeoutMs) }
            } catch (e: TimeoutCancellationException) {
                PluginOutcome.Failure(PluginOutcome.Reason.Timeout)
            }
        }
    }

    private suspend fun execute(code: String, request: PluginRequest, scraperId: String, timeoutMs: Long): PluginOutcome {
        val http = PluginHttp(client, policy, tmdbApiKey, limits, tmdbHost, tmdbHttpsOnly)
        val dom = DomBridge(limits)
        val captured = CompletableDeferred<String>()
        val qjs = QuickJs.create(jobDispatcher = dispatcher)
        try {
            qjs.memoryLimit = limits.memoryLimitBytes
            qjs.maxStackSize = limits.maxStackBytes
            // Asıl süre sınırı [run] içindeki withTimeout'tur; bu, o iptal edilemeyen bir yerde takılırsa son güvencedir.
            qjs.evaluationTimeoutMillis = timeoutMs + 3_000
            bind(qjs, http, dom, request, captured)

            // Başlangıç ve eklenti kodu: bir hata (söz dizimi...) burada çıkarsa betik hatasıdır.
            try {
                qjs.evaluate<Any?>(PluginPrelude.source(scraperId, "{}"), "prelude.js")
                qjs.evaluate<Any?>(PluginPrelude.wrap(code), "plugin.js")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return PluginOutcome.Failure(PluginOutcome.Reason.Script, shorten(e.message))
            }

            val json = coroutineScope {
                val job = launch {
                    try {
                        qjs.evaluate<Any?>(PluginPrelude.CALL, "call.js")
                        // Normal biterse ama sonuç bildirmediyse (söz hiç çözülmediyse) boş say.
                        captured.complete(NO_RESULT)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        captured.complete(errorJson(shorten(e.message)))
                    }
                }
                val first = captured.await()
                job.cancel() // sarkan zamanlayıcıları/istekleri bırak
                first
            }
            return PluginResultParser.parse(json, limits.maxStreams)
        } finally {
            try {
                qjs.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun bind(qjs: QuickJs, http: PluginHttp, dom: DomBridge, request: PluginRequest, captured: CompletableDeferred<String>) {
        val args = buildJsonObject {
            put("tmdbId", request.tmdbId)
            put("mediaType", request.mediaType)
            put("season", request.season?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            put("episode", request.episode?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        }.toString()

        qjs.function("__host_log") { a -> log(a.s(0), LogMasker.mask(a.s(1), tmdbApiKey)); null }
        qjs.function("__host_args") { args }
        qjs.function("__host_capture") { a -> captured.complete(a.s(0)); null }
        qjs.asyncFunction("__host_fetch") { a -> http.fetch(a.s(0)) }
        qjs.asyncFunction("__host_sleep") { a -> delay(a.s(0).toLongOrNull()?.coerceIn(0, MAX_SLEEP_MS) ?: 0L); null }
        qjs.function("__host_url") { a -> UrlBridge.parse(a.s(0), a.s(1)) }

        qjs.function("__host_dom_load") { a -> dom.load(a.s(0)) }
        qjs.function("__host_dom_select") { a -> dom.select(a.i(0), a.i(1), a.s(2)) }
        qjs.function("__host_dom_text") { a -> dom.text(a.i(0), a.i(1)) }
        qjs.function("__host_dom_html") { a -> dom.html(a.i(0), a.i(1), a.s(2) == "1") }
        qjs.function("__host_dom_attr") { a -> dom.attr(a.i(0), a.i(1), a.s(2)) }
        qjs.function("__host_dom_is") { a -> if (dom.matches(a.i(0), a.i(1), a.s(2))) "1" else "0" }
        qjs.function("__host_dom_rel") { a -> dom.relatives(a.i(0), a.i(1), a.s(2), a.s(3)) }

        qjs.function("__host_digest") { a -> CryptoBridge.digest(a.s(0), a.s(1)) }
        qjs.function("__host_hmac") { a -> CryptoBridge.hmac(a.s(0), a.s(1), a.s(2)) }
        qjs.function("__host_cipher") { a ->
            CryptoBridge.cipher(a.s(0), a.s(1), a.s(2), a.s(3) == "1", a.s(4), a.s(5), a.s(6))
        }
        qjs.function("__host_random") { a -> CryptoBridge.randomHex(a.i(0)) }
    }

    private fun Array<Any?>.s(i: Int): String = getOrNull(i)?.toString().orEmpty()

    private fun Array<Any?>.i(i: Int): Int = s(i).toDoubleOrNull()?.toInt() ?: -1

    private fun errorJson(message: String?): String =
        buildJsonObject { put("ok", false); put("error", message ?: "error") }.toString()

    private fun shorten(message: String?): String? = message?.take(300)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        private const val MAX_SLEEP_MS = 60_000L
        private const val NO_RESULT = """{"ok":false,"error":"no result"}"""
    }
}

/** Günlük metninden gizli değerleri siler: `api_key=...` benzeri sorgu değerleri, yer tutucu ve (varsa) gerçek anahtar. */
internal object LogMasker {
    private val QUERY_SECRET = Regex("""(?i)\b(api[_-]?key|apikey|access[_-]?token|token|key)=([^&\s"'<>]+)""")

    fun mask(text: String, realKey: String): String {
        var out = QUERY_SECRET.replace(text) { "${it.groupValues[1]}=***" }
        if (realKey.length >= 4) out = out.replace(realKey, "***")
        return out.replace(PluginPrelude.TMDB_PLACEHOLDER, "***")
    }
}

/** JS'in döndürdüğü sonuç JSON'unu ([PluginStream] listesi ya da hata) ayrıştırır. */
internal object PluginResultParser {
    fun parse(json: String, maxStreams: Int): PluginOutcome {
        val root = try {
            PlayJson.parseToJsonElement(json) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return PluginOutcome.Failure(PluginOutcome.Reason.Script, "bad result")
        if ((root["ok"] as? JsonPrimitive)?.content != "true") {
            return PluginOutcome.Failure(PluginOutcome.Reason.Script, (root["error"] as? JsonPrimitive)?.content)
        }
        val array = root["streams"] as? JsonArray ?: return PluginOutcome.Success(emptyList())
        val seen = HashSet<String>()
        val streams = array.asSequence()
            .mapNotNull { (it as? JsonObject)?.let(PluginStream::fromJson) }
            .filter { seen.add(it.url) }
            .take(maxStreams)
            .toList()
        return PluginOutcome.Success(streams)
    }
}
