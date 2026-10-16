package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * CloudStream eklenti temel sınıfı. Tüm CS scraper'ları bu sınıftan türetilir.
 * Play bu sınıfı yükleyerek [search], [load] ve [loadLinks] işlevlerini çağırır.
 */
abstract class MainAPI {
    open var name: String = ""
    open var mainUrl: String = ""
    open var lang: String = "en"

    /** Eklentinin desteklediği içerik türleri. */
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)

    open val hasMainPage: Boolean get() = mainPage.isNotEmpty()
    open val hasChromecastSupport: Boolean = true
    open val hasDownloadSupport: Boolean = false
    open val hasQuickSearch: Boolean = false

    /**
     * Başlık araması. Yanıt listesi doğrudan CS'in `SearchResponse` nesnelerinden oluşur.
     * Play bu listeyi başlık eşleşmesi için tarar.
     */
    open suspend fun search(query: String): List<SearchResponse>? = null

    /**
     * Bir arama sonucunun detay URL'sinden tam bilgileri yükler.
     * Film → [MovieLoadResponse], dizi → [TvSeriesLoadResponse].
     */
    open suspend fun load(url: String): LoadResponse? = null

    /**
     * Bir film/bölüm için oynatılabilir bağlantılar. Bulunan bağlantılar [callback] ile bildirilir;
     * altyazılar [subtitleCallback] ile bildirilir. `true` döndürmek başarı anlamına gelir.
     */
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = false

    /**
     * Ana sayfa bölümleri. Play sağlayıcı ana sayfasında bu akışı gösterir.
     */
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    open suspend fun getMainPage(page: Int, request: MainPageData): HomePageResponse? = getMainPage(page, request.toRequest())

    open val mainPage: List<MainPageData> get() = emptyList()
}

data class MainPageData(val name: String, val data: String, val horizontalImages: Boolean = false) {
    fun toRequest(): MainPageRequest = MainPageRequest(name, data, horizontalImages)
}
data class MainPageRequest(val name: String, val data: String, val horizontalImages: Boolean = false) {
    fun toData(): MainPageData = MainPageData(name, data, horizontalImages)
}
data class HomePageResponse(val items: List<HomePageList>, val hasNext: Boolean = false)
data class HomePageList(val name: String, val list: List<SearchResponse>, val isHorizontalImages: Boolean = false)

/** Basit istisna türü; bazı eklentiler yükleme hatasını bununla bildirir. */
class ErrorLoadingException(message: String? = null) : Exception(message)

/**
 * Gerçek CloudStream kütüphanesi Jackson + `jackson-module-kotlin` kullanır (kotlinx.serialization
 * DEĞİL); derlenmiş eklentiler doğrudan Jackson bytecode'una bağlıdır, bu yüzden eklenti uyumluluğu
 * için gerçek Jackson çalışma zamanında bulunur (Play'in kendi kodu hâlâ kotlinx.serialization kullanır).
 */
val mapper: JsonMapper = jacksonMapperBuilder()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

// ---------------------------------------------------------------------------
// Ana sayfa yardımcıları — eklentiler `override val mainPage = mainPageOf(...)` biçiminde kullanır.
// ---------------------------------------------------------------------------

fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> =
    elements.map { (url, name) -> MainPageData(name = name, data = url) }

fun mainPageOf(vararg elements: MainPageData): List<MainPageData> = elements.toList()

fun mainPage(url: String, name: String, horizontalImages: Boolean = false): MainPageData =
    MainPageData(name = name, data = url, horizontalImages = horizontalImages)

fun newHomePageResponse(name: String, list: List<SearchResponse>, hasNext: Boolean? = null): HomePageResponse =
    HomePageResponse(listOf(HomePageList(name, list)), hasNext == true)

fun newHomePageResponse(request: MainPageRequest, list: List<SearchResponse>, hasNext: Boolean? = null): HomePageResponse =
    HomePageResponse(listOf(HomePageList(request.name, list, request.horizontalImages)), hasNext == true)

fun newHomePageResponse(request: MainPageData, list: List<SearchResponse>, hasNext: Boolean? = null): HomePageResponse =
    HomePageResponse(listOf(HomePageList(request.name, list, request.horizontalImages)), hasNext == true)

fun newHomePageResponse(items: List<HomePageList>, hasNext: Boolean? = null): HomePageResponse =
    HomePageResponse(items, hasNext == true)

@JvmName("newHomePageResponsePrimitive")
fun newHomePageResponse(name: String, list: List<SearchResponse>, hasNext: Boolean): HomePageResponse =
    HomePageResponse(listOf(HomePageList(name, list)), hasNext)

@JvmName("newHomePageResponseRequestPrimitive")
fun newHomePageResponse(request: MainPageRequest, list: List<SearchResponse>, hasNext: Boolean): HomePageResponse =
    HomePageResponse(listOf(HomePageList(request.name, list, request.horizontalImages)), hasNext)

@JvmName("newHomePageResponseDataPrimitive")
fun newHomePageResponse(request: MainPageData, list: List<SearchResponse>, hasNext: Boolean): HomePageResponse =
    HomePageResponse(listOf(HomePageList(request.name, list, request.horizontalImages)), hasNext)

@JvmName("newHomePageResponseItemsPrimitive")
fun newHomePageResponse(items: List<HomePageList>, hasNext: Boolean): HomePageResponse =
    HomePageResponse(items, hasNext)

// ---------------------------------------------------------------------------
// Metin/URL yardımcıları
// ---------------------------------------------------------------------------

fun base64Decode(text: String): String = String(android.util.Base64.decode(text, android.util.Base64.DEFAULT))
fun base64DecodeArray(text: String): ByteArray = android.util.Base64.decode(text, android.util.Base64.DEFAULT)

/** Göreli adresleri eklentinin `mainUrl`'üne göre mutlaklaştırır. */
fun MainAPI.fixUrl(url: String): String {
    if (url.isBlank()) return url
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "${mainUrl.trimEnd('/')}$url"
        else -> "${mainUrl.trimEnd('/')}/$url"
    }
}

fun MainAPI.fixUrlNull(url: String?): String? = url?.let { fixUrl(it) }

// ---------------------------------------------------------------------------
// Bölüm (Episode) yardımcısı
// ---------------------------------------------------------------------------

fun MainAPI.newEpisode(data: Any?, initializer: Episode.() -> Unit = {}): Episode {
    val dataStr = when (data) {
        is String -> data
        null -> ""
        else -> mapper.writeValueAsString(data)
    }
    return Episode(dataStr).apply(initializer)
}

// ---------------------------------------------------------------------------
// `LoadResponse` kurucuları (suspend; eklentiler bir builder lambda'sı ile alan doldurur)
// ---------------------------------------------------------------------------

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    dataUrl: String = url,
    initializer: suspend MovieLoadResponse.() -> Unit = {},
): MovieLoadResponse {
    val response = MovieLoadResponse(name = name, url = url, apiName = this.name, type = type, dataUrl = dataUrl)
    response.initializer()
    return response
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: Any,
    initializer: suspend MovieLoadResponse.() -> Unit = {},
): MovieLoadResponse = newMovieLoadResponse(name, url, type, dataUrl.toString(), initializer)

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    episodes: List<Episode> = emptyList(),
    initializer: suspend TvSeriesLoadResponse.() -> Unit = {},
): TvSeriesLoadResponse {
    val response = TvSeriesLoadResponse(name = name, url = url, apiName = this.name, type = type, episodes = episodes)
    response.initializer()
    return response
}

suspend fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    initializer: suspend AnimeLoadResponse.() -> Unit = {},
): AnimeLoadResponse {
    val response = AnimeLoadResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name, type = type)
    response.initializer()
    return response
}

// ---------------------------------------------------------------------------
// `SearchResponse` kurucuları (senkron; `fix` doğruysa adres `fixUrl` ile mutlaklaştırılır)
// ---------------------------------------------------------------------------

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    initializer: MovieSearchResponse.() -> Unit = {},
): MovieSearchResponse {
    val response = MovieSearchResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name, type = type)
    response.initializer()
    return response
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    initializer: TvSeriesSearchResponse.() -> Unit = {},
): TvSeriesSearchResponse {
    val response = TvSeriesSearchResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name, type = type)
    response.initializer()
    return response
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    initializer: AnimeSearchResponse.() -> Unit = {},
): AnimeSearchResponse {
    val response = AnimeSearchResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name, type = type)
    response.initializer()
    return response
}

fun MainAPI.newLiveSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    fix: Boolean = true,
    initializer: LiveSearchResponse.() -> Unit = {},
): LiveSearchResponse {
    val response = LiveSearchResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name, type = type)
    response.initializer()
    return response
}

fun AnimeSearchResponse.addSub(sub: Int?) {
    this.sub = sub
}

fun AnimeSearchResponse.addDub(dub: Int?) {
    this.dub = dub
}

fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<Episode>) {
    val cur = this.episodes.toMutableMap()
    cur[status.name] = (cur[status.name] ?: emptyList()) + episodes
    this.episodes = cur
}

fun Episode.addDate(date: String?, format: String? = null) {
    this.date = date
}

val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

// ---------------------------------------------------------------------------
// Altyazı kurucusu
// ---------------------------------------------------------------------------

suspend fun newSubtitleFile(lang: String, url: String, initializer: suspend SubtitleFile.() -> Unit = {}): SubtitleFile {
    val subtitle = SubtitleFile(lang, url)
    subtitle.initializer()
    return subtitle
}
