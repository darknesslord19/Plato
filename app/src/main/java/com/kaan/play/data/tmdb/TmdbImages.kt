package com.kaan.play.data.tmdb

/** TMDB görsel adresleri. Yol (`/abc.jpg`) boş/null ise null döner; arayüz yer tutucu gösterir. */
object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"

    enum class Size(val segment: String) {
        Poster("w342"),
        PosterLarge("w500"),
        Backdrop("w780"),
        BackdropLarge("w1280"),
        Still("w300"),
        Logo("w500"),
        Profile("w185"),
    }

    fun url(path: String?, size: Size): String? =
        path?.takeIf { it.startsWith("/") }?.let { BASE + size.segment + it }
}
