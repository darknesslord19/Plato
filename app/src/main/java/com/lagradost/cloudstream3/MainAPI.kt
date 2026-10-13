package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * CloudStream eklenti temel sınıfı. Tüm CS scraper'ları bu sınıftan türetilir.
 * Play bu sınıfı yükleyerek [search], [load] ve [loadLinks] işlevlerini çağırır.
 */
abstract class MainAPI {
    open val name: String = ""
    open val mainUrl: String = ""
    open val lang: String = "en"

    /** Eklentinin desteklediği içerik türleri. */
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)

    open val hasMainPage: Boolean = false
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
     * Ana sayfa bölümleri (opsiyonel). Play tarafından kullanılmaz; eklenti uyumluluğu için stub.
     */
    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null

    open val mainPage: List<MainPageData> get() = emptyList()
}

data class MainPageRequest(val name: String, val data: String, val horizontalImages: Boolean = false)
data class MainPageData(val name: String, val data: String, val horizontalImages: Boolean = false)
data class HomePageResponse(val items: List<HomePageList>, val hasNext: Boolean = false)
data class HomePageList(val name: String, val list: List<SearchResponse>, val isHorizontalImages: Boolean = false)

/** `newMovieLoadResponse` ve `newTvSeriesLoadResponse` yardımcıları. Eklentiler kısayol olarak kullanır. */
fun newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    data: String? = null,
    posterUrl: String? = null,
    plot: String? = null,
    year: Int? = null,
    rating: Int? = null,
    tags: List<String>? = null,
    duration: Int? = null,
): MovieLoadResponse = MovieLoadResponse(name = name, url = url, apiName = "", type = type, dataUrl = data ?: url, posterUrl = posterUrl, plot = plot, year = year, rating = rating, tags = tags, duration = duration)

fun newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    posterUrl: String? = null,
    plot: String? = null,
    year: Int? = null,
    rating: Int? = null,
    tags: List<String>? = null,
): TvSeriesLoadResponse = TvSeriesLoadResponse(name = name, url = url, apiName = "", type = type, episodes = episodes, posterUrl = posterUrl, plot = plot, year = year, rating = rating, tags = tags)
