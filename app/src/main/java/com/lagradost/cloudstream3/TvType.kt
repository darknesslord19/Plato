package com.lagradost.cloudstream3

/**
 * CloudStream içerik türleri. Eklentiler bu enum'ı `supportedTypes` bildirmek için kullanır.
 * Play yalnızca Movie ve TvSeries/Live kullanır; diğerleri ayrıştırma sırasında yok sayılır.
 */
enum class TvType {
    Movie,
    AnimeMovie,
    TvSeries,
    Cartoon,
    Anime,
    OVA,
    Others,
    DocumentaryMovie,
    Documentary,
    AsianDrama,
    Live,
    NSFW,
    Music,
    AudioBook,
    Torrent,
}
