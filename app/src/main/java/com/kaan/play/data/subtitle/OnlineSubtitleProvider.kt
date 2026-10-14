package com.kaan.play.data.subtitle

import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.AddonFetchFailure
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.fetchAddonText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Yerleşik çevrimiçi altyazı araması (kullanıcının isteğiyle; altyazıyı kendi aramak zorunda kalmasın): OpenSubtitles'ın herkese
 * açık addon uç noktasına `GET {BASE}/subtitles/{movie|series}/{imdb}[:sezon:bölüm].json`. Dosya özetiyle (hash) arama yapılmaz:
 * torrentte dosya tam inmeden özet çıkmaz. Kimlik yalnızca IMDb + sezon/bölümdür.
 *
 * IMDb kimliği istekte yoksa [imdbFor] ile (TMDB `external_ids`) bulunur. Sonuçlar [languages] sırasıyla (tercih edilen dil,
 * sonra İngilizce) süzülür ve sıralanır; diğer diller atılır. Ağ hatası, zaman aşımı ya da bozuk cevap oynatmayı etkilemez:
 * boş liste döner ("yanıt vermedi" gösterilmez).
 */
class OnlineSubtitleProvider(
    private val client: OkHttpClient,
    private val languages: suspend () -> List<String>,
    private val imdbFor: suspend (StreamRequest) -> String? = { null },
    private val timeout: Duration = 15.seconds,
    private val baseUrl: String = BASE_URL,
    /** Arama sonuçları film/bölüm bazında önbelleğe alınır (verilmezse her seferinde sorulur). */
    private val cache: SubtitleSearchCache? = null,
) : SubtitleProvider {

    override fun subtitles(request: StreamRequest): Flow<SubtitleResult> = channelFlow {
        val tracks = lookup(request)
        // Ağ hatası: kısa "yanıt vermedi" bilgisi (oynatmayı etkilemez); boş sonuç hata değildir.
        send(if (tracks == null) SubtitleResult.Failed(SOURCE, com.kaan.play.data.stream.FailureReason.Network) else SubtitleResult.Found(SOURCE, tracks))
    }

    /** Bulunan parçalar (tercih sırasıyla); bulunamazsa ya da hata olursa boş. */
    suspend fun search(request: StreamRequest): List<SubtitleTrack> = lookup(request).orEmpty()

    /** Arama yapılamadıysa (ağ/HTTP hatası) null; sonuç yoksa boş liste. */
    private suspend fun lookup(request: StreamRequest): List<SubtitleTrack>? {
        val resolved = if (request.imdbId != null) request else {
            val imdb = try {
                imdbFor(request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            request.copy(imdbId = imdb?.takeIf { it.startsWith("tt") })
        }
        val contentId = resolved.contentId ?: return emptyList()
        val wanted = languages()
        // Debug derlemede sahte sunucuya yönlenebilir (yayın derlemesinde DebugEndpoints boştur).
        val base = DebugEndpoints.subsBase?.let { "$it/opensubtitles" } ?: baseUrl
        val fetch: suspend () -> List<SubtitleTrack>? = {
            val url = "$base/subtitles/${resolved.type.addonType}/$contentId.json".toHttpUrlOrNull()
            if (url == null) {
                null
            } else {
                try {
                    parse(fetchAddonText(client, url, timeout, MAX_BYTES), wanted)
                } catch (_: AddonFetchFailure) {
                    null // hata: önbelleğe yazılmaz
                }
            }
        }
        val key = searchKey("opensubtitles", resolved.type.addonType, contentId, null, null, wanted)
        return if (cache != null) cache.getOrFetch(key, fetch) else fetch()
    }

    companion object {
        const val BASE_URL = "https://opensubtitles-v3.strem.io"
        const val ORIGIN = "OpenSubtitles"
        val SOURCE = StreamSource(-1L, ORIGIN)
        const val MAX_BYTES = 5L * 1024 * 1024

        /** Dil başına en çok bu kadar sonuç (popüler filmlerde yüzlercesi gelir). */
        const val MAX_PER_LANGUAGE = 15

        /**
         * `{"subtitles":[{"url","lang","subtitleFileName"?}]}` -> [languages] sırasıyla süzülmüş parçalar. Biçim tutmazsa boş.
         * Aynı adres tek kez alınır; dil sırası içinde cevabın sırası korunur.
         */
        fun parse(body: String, languages: List<String>): List<SubtitleTrack> {
            val root = try {
                PlayJson.parseToJsonElement(body)
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
            val array = (root as? JsonObject)?.get("subtitles") as? JsonArray ?: return emptyList()
            val wanted = languages.distinct()
            val tracks = array.asSequence()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { obj ->
                    val url = obj.string("url")?.trim()
                        ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
                        ?: return@mapNotNull null
                    val lang = SubtitleLanguages.normalize(obj.string("lang"))?.takeIf { it in wanted } ?: return@mapNotNull null
                    val name = obj.string("subtitleFileName")?.trim()?.takeIf { it.isNotEmpty() }
                    SubtitleTrack(url, lang, ORIGIN, SubtitleKind.Online, name)
                }
                .distinctBy { it.url }
                .toList()
            return wanted.flatMap { lang -> tracks.filter { it.language == lang }.take(MAX_PER_LANGUAGE) }
        }

        private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    }
}

/** Birden çok altyazı sağlayıcısını tek akışta birleştirir (cevaplar geldikçe). */
class CompositeSubtitleProvider(private val providers: List<SubtitleProvider>) : SubtitleProvider {
    override fun subtitles(request: StreamRequest): Flow<SubtitleResult> = providers.map { it.subtitles(request) }.merge()

    override suspend fun sources(request: StreamRequest): List<StreamSource> = providers.flatMap { it.sources(request) }
}
