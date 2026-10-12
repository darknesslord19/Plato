package com.kaan.play.data.tmdb

import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.Page
import com.kaan.play.data.model.SeasonDetail
import com.kaan.play.data.model.SeasonInfo
import com.kaan.play.i18n.UiLanguage
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Uygulamanın TMDB kapısı: DTO'ları alan modellerine çevirir ve dil yedeğini uygular.
 *
 * Dil: her istek [language]'ın TMDB etiketiyle (`tr-TR` / `en-US`) gider; her çağrıda yeniden okunur,
 * yani Ayarlar'da dil değişince yeni istekler hemen yeni dilde çıkar.
 * Yedek: Türkçe özet (bölümde de) boşsa aynı içerik `en-US` ile bir kez daha istenip boşluk doldurulur.
 * Listelerde yedek yok: kartlar başlık+poster gösterir, özet gerekmez.
 */
class TmdbRepository(
    client: OkHttpClient,
    json: Json,
    apiKey: String,
    private val language: () -> UiLanguage,
    baseUrl: HttpUrl = TmdbService.DEFAULT_BASE_URL.toHttpUrl(),
) {
    private val service = TmdbService(client, json, apiKey, baseUrl)

    suspend fun trending(page: Int = 1): Page<MediaSummary> =
        service.trending(lang(), page).toPage()

    suspend fun popular(type: MediaType, page: Int = 1): Page<MediaSummary> =
        service.popular(type.tmdbPath, lang(), page).toPage(defaultType = type)

    /** Boş sorgu ağa gitmeden boş sonuç verir. Kişi sonuçları ayıklanır. */
    suspend fun search(query: String, page: Int = 1): Page<MediaSummary> {
        val q = query.trim()
        if (q.isEmpty()) return Page(emptyList(), 1, 1)
        return service.search(q, lang(), page).toPage()
    }

    suspend fun detail(type: MediaType, id: Int): MediaDetail {
        val primary = service.detail(type.tmdbPath, id, lang())
        val merged = if (needsFallback() && primary.overview.isNullOrBlank()) {
            // En-US yedeği en iyi çabadır: başarısız olursa Türkçe sonuç boş özetle yine de döner.
            val english = bestEffort { service.detail(type.tmdbPath, id, UiLanguage.English.tmdbTag) }
            if (english == null) primary else primary.fillFrom(english)
        } else {
            primary
        }
        return merged.toDetail(type)
    }

    suspend fun season(tvId: Int, season: Int): SeasonDetail {
        val primary = service.season(tvId, season, lang())
        val merged = if (needsFallback() && primary.episodes.any { it.overview.isNullOrBlank() }) {
            val english = bestEffort { service.season(tvId, season, UiLanguage.English.tmdbTag) }
            if (english == null) primary else primary.fillFrom(english)
        } else {
            primary
        }
        return SeasonDetail(
            number = merged.seasonNumber,
            episodes = merged.episodes.map {
                Episode(
                    number = it.episodeNumber,
                    name = it.name.orEmpty(),
                    overview = it.overview.orEmpty(),
                    stillPath = it.stillPath,
                    runtimeMinutes = it.runtime,
                    airDate = it.airDate?.takeIf(String::isNotBlank),
                )
            },
        )
    }

    private fun lang(): String = language().tmdbTag

    private fun needsFallback(): Boolean = language() != UiLanguage.English
}

// ---- DTO -> alan modeli ----

private fun ListDto.toPage(defaultType: MediaType? = null): Page<MediaSummary> = Page(
    items = results.mapNotNull { it.toSummary(defaultType) },
    page = page,
    totalPages = totalPages,
)

/** Film/dizi değilse (ör. kişi) null döner. */
private fun ItemDto.toSummary(defaultType: MediaType?): MediaSummary? {
    val type = MediaType.fromTmdb(mediaType) ?: defaultType ?: return null
    return MediaSummary(
        id = id,
        type = type,
        title = (title ?: name).orEmpty(),
        overview = overview.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = yearOf(releaseDate ?: firstAirDate),
        rating = voteAverage?.takeIf { it > 0.0 },
    )
}

private fun DetailDto.toDetail(type: MediaType): MediaDetail = MediaDetail(
    summary = MediaSummary(
        id = id,
        type = type,
        title = (title ?: name).orEmpty(),
        overview = overview.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = yearOf(releaseDate ?: firstAirDate),
        rating = voteAverage?.takeIf { it > 0.0 },
    ),
    imdbId = (externalIds?.imdbId ?: imdbId)?.takeIf { it.startsWith("tt") },
    tagline = tagline.orEmpty(),
    genres = genres.map { it.name }.filter { it.isNotBlank() },
    runtimeMinutes = (runtime ?: episodeRunTime.firstOrNull())?.takeIf { it > 0 },
    // Sezon 0 ("Özel bölümler") addon'larda nadiren karşılık bulur; sade tutmak için dışarıda.
    seasons = seasons.filter { it.seasonNumber > 0 }.map {
        SeasonInfo(
            number = it.seasonNumber,
            name = it.name.orEmpty(),
            episodeCount = it.episodeCount,
            posterPath = it.posterPath,
        )
    },
)

private fun DetailDto.fillFrom(en: DetailDto): DetailDto = copy(
    title = title.takeUnless { it.isNullOrBlank() } ?: en.title,
    name = name.takeUnless { it.isNullOrBlank() } ?: en.name,
    overview = overview.takeUnless { it.isNullOrBlank() } ?: en.overview,
    tagline = tagline.takeUnless { it.isNullOrBlank() } ?: en.tagline,
)

private fun SeasonDetailDto.fillFrom(en: SeasonDetailDto): SeasonDetailDto {
    val byNumber = en.episodes.associateBy { it.episodeNumber }
    return copy(
        episodes = episodes.map { ep ->
            val fallback = byNumber[ep.episodeNumber] ?: return@map ep
            ep.copy(
                name = ep.name.takeUnless { it.isNullOrBlank() } ?: fallback.name,
                overview = ep.overview.takeUnless { it.isNullOrBlank() } ?: fallback.overview,
            )
        },
    )
}

private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()

/** Yedek istekler için: yalnızca TMDB hatasını yutar, iptal gibi diğer istisnalar yukarı çıkar. */
private inline fun <T> bestEffort(block: () -> T): T? =
    try { block() } catch (_: TmdbException) { null }
