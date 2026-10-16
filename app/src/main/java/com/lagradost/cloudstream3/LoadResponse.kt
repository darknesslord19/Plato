package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * CloudStream yük yanıtı (detay sayfası içeriği). Derlenmiş eklentiler alanlara `invoke-interface`
 * ile eriştiği için (örn. `LoadResponse.setPlot`) bir **arayüz** olmalıdır; sınıf olursa çağrı
 * `IncompatibleClassChangeError` verir. Alanlar [LoadResponseBase] içinde tutulur.
 */
interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var year: Int?
    var plot: String?
    var score: Score?
    var tags: List<String>?
    var duration: Int?
    var trailers: MutableList<TrailerData>
    var recommendations: List<SearchResponse>?
    var actors: List<ActorData>?
    var comingSoon: Boolean
    var contentRating: String?
    var backgroundPosterUrl: String?
    var logoUrl: String?
    var imdbId: String?

    companion object {
        /** Oyuncu listesini yanıta ekler. */
        fun addActors(responseBuilder: LoadResponse, actors: List<ActorData>?) {
            responseBuilder.actors = actors
        }

        fun addActorNames(responseBuilder: LoadResponse, actors: List<String>?) {
            responseBuilder.actors = actors?.map { ActorData(Actor(it)) }
        }

        fun addActorsOnly(responseBuilder: LoadResponse, actors: List<String>?) {
            responseBuilder.actors = actors?.map { ActorData(Actor(it)) }
        }

        fun addImdbId(responseBuilder: LoadResponse, imdbId: String?) {
            responseBuilder.imdbId = imdbId
        }

        /** Fragman adresi ekler (bu stub'da ek bir ağ isteği yapmaz; adres doğrudan saklanır). */
        suspend fun addTrailer(
            responseBuilder: LoadResponse,
            trailerUrl: String?,
            mirrorUrl: String? = null,
            addRaw: Boolean = false,
        ) {
            if (trailerUrl.isNullOrBlank() && mirrorUrl.isNullOrBlank()) return
            responseBuilder.trailers += TrailerData(extractorUrl = trailerUrl ?: mirrorUrl)
        }
    }
}

/** Somut yük yanıtlarının ortak alanları; eklentiler bunları builder lambda'sı içinde atar. */
abstract class LoadResponseBase(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
) : LoadResponse {
    override var posterUrl: String? = null
    override var posterHeaders: Map<String, String>? = null
    override var imdbId: String? = null
    override var year: Int? = null
    override var plot: String? = null
    override var score: Score? = null
    override var tags: List<String>? = null
    override var duration: Int? = null
    override var trailers: MutableList<TrailerData> = mutableListOf()
    override var recommendations: List<SearchResponse>? = null
    override var actors: List<ActorData>? = null
    override var comingSoon: Boolean = false
    override var contentRating: String? = null
    override var backgroundPosterUrl: String? = null
    override var logoUrl: String? = null
}

data class TrailerData(val extractorUrl: String?, val raw: List<ExtractorLink>? = null)

class Episode(val data: String) {
    var name: String? = null
    var season: Int? = null
    var episode: Int? = null
    var posterUrl: String? = null
    var description: String? = null
    var date: String? = null
    var rating: Int? = null
    var runTime: Int? = null
}

data class SeasonData(
    val season: Int,
    val name: String? = null,
    val displaySeason: Int? = null,
)

/** 0-10 aralığındaki puanı CloudStream'in yeni `Score` türüne sarar. */
class Score private constructor(private val tenScale: Double) {
    fun toDouble(): Double = tenScale
    override fun toString(): String = tenScale.toString()

    companion object {
        fun from10(value: Double?): Score? = value?.let { Score(it) }
        fun from10(value: String?): Score? = value?.replace(",", ".")?.toDoubleOrNull()?.let { Score(it) }
    }
}

enum class ActorRole { Main, Supporting, Background }

data class ActorData(
    val actor: Actor,
    val roleString: ActorRole? = null,
    val role: String? = null,
    val voiceActor: Actor? = null,
)

data class Actor(val name: String, val image: String? = null)

class MovieLoadResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType = TvType.Movie,
    var dataUrl: String = url,
) : LoadResponseBase(name, url, apiName, type)

class TvSeriesLoadResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType = TvType.TvSeries,
    var episodes: List<Episode> = emptyList(),
) : LoadResponseBase(name, url, apiName, type) {
    var seasonNames: List<SeasonData>? = null
}

class AnimeLoadResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType = TvType.Anime,
    var episodes: Map<String, List<Episode>> = emptyMap(),
) : LoadResponseBase(name, url, apiName, type) {
    var japName: String? = null
    var engName: String? = null
}

class LiveStreamLoadResponse(
    name: String,
    url: String,
    apiName: String,
    type: TvType = TvType.Live,
    var dataUrl: String = url,
) : LoadResponseBase(name, url, apiName, type)

// Eklentilerde kullanılan kısayol uzantı işlevi
fun List<Episode>.getEpisodesForSeason(season: Int): List<Episode> = filter { it.season == season }
