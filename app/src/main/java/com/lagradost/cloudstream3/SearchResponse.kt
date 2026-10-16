package com.lagradost.cloudstream3

/**
 * CloudStream arama yanıtı. Derlenmiş eklentiler bu türe `invoke-interface` ile eriştiği için
 * (örn. `SearchResponse.getUrl()`) bir **arayüz** olmalıdır; sınıf olursa çağrı
 * `IncompatibleClassChangeError` verir. Alanlar [SearchResponseBase] içinde tutulur.
 */
interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var score: Score?
}

/** Somut arama yanıtlarının ortak alanları; eklentiler alanları builder lambda'sında `var` olarak değiştirir. */
abstract class SearchResponseBase(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType?,
) : SearchResponse {
    override var posterUrl: String? = null
    override var posterHeaders: Map<String, String>? = null
    override var id: Int? = null
    override var score: Score? = null
    var year: Int? = null
}

class MovieSearchResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType? = TvType.Movie,
) : SearchResponseBase(name, url, apiName, type)

class TvSeriesSearchResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType? = TvType.TvSeries,
) : SearchResponseBase(name, url, apiName, type) {
    var episodes: Int? = null
}

class AnimeSearchResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType? = TvType.Anime,
) : SearchResponseBase(name, url, apiName, type) {
    var dubStatus: Set<String>? = null
    var sub: Int? = null
    var dub: Int? = null
}

class LiveSearchResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType? = TvType.Live,
) : SearchResponseBase(name, url, apiName, type)
