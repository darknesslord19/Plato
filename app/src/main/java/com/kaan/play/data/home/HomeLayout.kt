package com.kaan.play.data.home

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Ana sayfadaki bir bölüm. Sıra listedeki sıradır; [visible] false ise gizlidir (silinmez, yeri hatırlanır).
 * Hazır bölümlerde yalnızca [id] dolu; kullanıcının yazdığı kategoride [name] (başlık), [query] (TMDB araması) ve
 * [type] (`movie` / `tv`; null = ikisi) de dolu ve [id] `custom:` ile başlar.
 */
@Serializable
data class HomeSection(
    val id: String,
    val visible: Boolean = true,
    val name: String? = null,
    val query: String? = null,
    val type: String? = null,
    /** Eklenti rafında rafın geldiği eklentinin adı (düzenleme ekranında alt satır). */
    val sub: String? = null,
) {
    val isCustom: Boolean get() = id.startsWith(HomeCatalog.CUSTOM_PREFIX)
    val isSpecial: Boolean get() = id in HomeCatalog.SPECIALS
    /** Eklentinin kendi katalog rafı (`shelf:<sağlayıcı>|<raf>`); adı [name], eklenti adı [sub]. */
    val isShelf: Boolean get() = id.startsWith(HomeCatalog.SHELF_PREFIX)
}

/** Ana sayfada görülen bir eklenti rafı (düzene eklenmek üzere). */
data class ShelfInfo(val key: String, val title: String, val providerName: String)

/** Hazır kategori: kimlik, ekleme ekranındaki grup ve TMDB listesi. Başlık arayüzde kimlikten çevrilir. */
data class HomePreset(val id: String, val group: Group, val query: TmdbQuery) {
    enum class Group { Lists, MovieGenres, SeriesGenres, Regional, Platforms }
}

/** Ana sayfanın yapı taşları: özel bölümler (kapak, devam et, eklenti rafları) + hazır TMDB kategorileri. */
object HomeCatalog {
    const val HERO = "hero"
    const val CONTINUE = "continue"
    const val PROVIDERS = "providers"
    const val CUSTOM_PREFIX = "custom:"
    const val SHELF_PREFIX = "shelf:"

    /** Eklenti rafının düzen kimliği. */
    fun shelfId(providerId: String, rowId: String) = "$SHELF_PREFIX$providerId|$rowId"

    /** `shelf:` kimliğinden (sağlayıcı, raf); bozuksa null. */
    fun parseShelf(id: String): Pair<String, String>? {
        if (!id.startsWith(SHELF_PREFIX)) return null
        val rest = id.removePrefix(SHELF_PREFIX)
        val i = rest.indexOf('|')
        if (i <= 0 || i == rest.lastIndex) return null
        return rest.substring(0, i) to rest.substring(i + 1)
    }
    val SPECIALS = listOf(HERO, CONTINUE, PROVIDERS)

    private val M = MediaType.Movie
    private val T = MediaType.Tv

    private fun genre(prefix: String, type: MediaType, id: Int) =
        HomePreset(
            "${prefix}_$id",
            if (type == M) HomePreset.Group.MovieGenres else HomePreset.Group.SeriesGenres,
            TmdbQuery.discover(type, "with_genres" to id.toString(), "vote_count.gte" to "20"),
        )

    private fun regional(id: String, type: MediaType, vararg params: Pair<String, String>) =
        HomePreset(id, HomePreset.Group.Regional, TmdbQuery.discover(type, *params))

    private fun network(id: String, network: Int) =
        HomePreset(id, HomePreset.Group.Platforms, TmdbQuery.discover(T, "with_networks" to network.toString()))

    private fun lists(id: String, query: TmdbQuery) = HomePreset(id, HomePreset.Group.Lists, query)

    /** Film türü kimlikleri (TMDB `genre/movie/list`). Başlıkları arayüzde: `movie_<kimlik>`. */
    val MOVIE_GENRES = listOf(28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27, 10402, 9648, 10749, 878, 53, 10752, 37)

    /** Dizi türü kimlikleri (TMDB `genre/tv/list`). Başlıkları arayüzde: `tv_<kimlik>`. */
    val SERIES_GENRES = listOf(10759, 16, 35, 80, 99, 18, 10751, 10762, 9648, 10764, 10765, 10768)

    val PRESETS: List<HomePreset> = buildList {
        add(lists("trending_movies", TmdbQuery.trending(M)))
        add(lists("trending_series", TmdbQuery.trending(T)))
        add(lists("popular_movies", TmdbQuery.list(M, "popular")))
        add(lists("popular_series", TmdbQuery.list(T, "popular")))
        add(lists("trending_all", TmdbQuery.trending(null)))
        add(lists("top_rated_movies", TmdbQuery.list(M, "top_rated")))
        add(lists("top_rated_series", TmdbQuery.list(T, "top_rated")))
        add(lists("now_playing_movies", TmdbQuery.list(M, "now_playing")))
        add(lists("upcoming_movies", TmdbQuery.list(M, "upcoming")))
        add(lists("airing_today_series", TmdbQuery.list(T, "airing_today")))
        add(lists("on_air_series", TmdbQuery.list(T, "on_the_air")))
        MOVIE_GENRES.forEach { add(genre("movie", M, it)) }
        SERIES_GENRES.forEach { add(genre("tv", T, it)) }
        add(regional("turkish_movies", M, "with_original_language" to "tr"))
        add(regional("turkish_series", T, "with_original_language" to "tr"))
        add(regional("korean_series", T, "with_original_language" to "ko"))
        add(regional("korean_movies", M, "with_original_language" to "ko"))
        add(regional("anime_series", T, "with_genres" to "16", "with_original_language" to "ja"))
        add(regional("anime_movies", M, "with_genres" to "16", "with_original_language" to "ja"))
        add(regional("indian_movies", M, "with_original_language" to "hi"))
        add(regional("spanish_series", T, "with_original_language" to "es"))
        add(network("netflix_series", 213))
        add(network("disney_series", 2739))
        add(network("hbo_series", 49))
        add(network("prime_series", 1024))
        add(network("apple_series", 2552))
        add(network("hulu_series", 453))
    }

    private val presetById = PRESETS.associateBy { it.id }

    fun preset(id: String): HomePreset? = presetById[id]

    /** Bugünkü ana sayfa düzeni (önceki sürümle aynı): kapak, devam et, eklenti rafları, dört TMDB rafı. */
    val DEFAULT_IDS = listOf(HERO, CONTINUE, PROVIDERS, "trending_movies", "trending_series", "popular_movies", "popular_series")

    fun defaultLayout(): List<HomeSection> = DEFAULT_IDS.map { HomeSection(it) }

    /** Bölümün TMDB listesi; özel bölümlerde ve bozuk özel kategoride null. */
    fun query(section: HomeSection): TmdbQuery? = when {
        section.isCustom -> section.query?.takeIf { it.isNotBlank() }?.let {
            TmdbQuery.search(it, MediaType.entries.firstOrNull { t -> t.tmdbPath == section.type })
        }
        else -> presetById[section.id]?.query
    }
}

/** Düzen işlemleri (saf işlevler; DataStore'a [HomeLayoutStore] yazar). */
object HomeLayoutRules {
    const val MAX_NAME = 40
    const val MAX_QUERY = 80
    const val MAX_CUSTOM = 30

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(HomeSection.serializer())

    fun encode(layout: List<HomeSection>): String = json.encodeToString(serializer, layout)

    /** Bozuk/boş kayıt varsayılan düzene düşer. */
    fun decode(raw: String?): List<HomeSection> {
        if (raw.isNullOrBlank()) return HomeCatalog.defaultLayout()
        val parsed = runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: return HomeCatalog.defaultLayout()
        return normalize(parsed)
    }

    /**
     * Bilinmeyen hazır kimlikleri ve bozuk özel kategorileri atar, tekrarları siler; eksik özel bölümleri (kapak,
     * devam et, eklenti rafları) varsayılan sırayla başa ekler (gizlenebilirler ama kaybolmazlar).
     */
    fun normalize(layout: List<HomeSection>): List<HomeSection> {
        val seen = HashSet<String>()
        val kept = layout.filter { s ->
            val valid = when {
                s.isSpecial -> true
                s.isCustom -> !s.name.isNullOrBlank() && !s.query.isNullOrBlank()
                s.isShelf -> HomeCatalog.parseShelf(s.id) != null && !s.name.isNullOrBlank()
                else -> HomeCatalog.preset(s.id) != null
            }
            valid && seen.add(s.id)
        }
        val missing = HomeCatalog.SPECIALS.filter { it !in seen }.map { HomeSection(it) }
        return missing + kept
    }

    /** [id]'yi [delta] kadar (−1 yukarı, +1 aşağı) taşır; sınırda değişmez. */
    fun move(layout: List<HomeSection>, id: String, delta: Int): List<HomeSection> {
        val from = layout.indexOfFirst { it.id == id }
        if (from < 0) return layout
        val to = (from + delta).coerceIn(0, layout.lastIndex)
        if (to == from) return layout
        return layout.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun setVisible(layout: List<HomeSection>, id: String, visible: Boolean): List<HomeSection> =
        layout.map { if (it.id == id) it.copy(visible = visible) else it }

    /** Hazır kategori zaten listede ise görünür yapılır, değilse sona eklenir. */
    fun addPreset(layout: List<HomeSection>, id: String): List<HomeSection> {
        if (HomeCatalog.preset(id) == null) return layout
        return if (layout.any { it.id == id }) setVisible(layout, id, true) else layout + HomeSection(id)
    }

    /** Kullanıcının yazdığı kategori sona eklenir; ad/sorgu boşsa ya da sınır dolmuşsa değişmez. */
    fun addCustom(layout: List<HomeSection>, name: String, query: String, type: MediaType?, newId: String): List<HomeSection> {
        val n = name.trim().take(MAX_NAME)
        val q = query.trim().take(MAX_QUERY)
        if (n.isEmpty() || q.isEmpty() || layout.count { it.isCustom } >= MAX_CUSTOM) return layout
        val id = HomeCatalog.CUSTOM_PREFIX + newId
        if (layout.any { it.id == id }) return layout
        return layout + HomeSection(id = id, name = n, query = q, type = type?.tmdbPath)
    }

    /** Özel kategoriyi düzenler (ad, sorgu, tür). */
    fun editCustom(layout: List<HomeSection>, id: String, name: String, query: String, type: MediaType?): List<HomeSection> {
        val n = name.trim().take(MAX_NAME)
        val q = query.trim().take(MAX_QUERY)
        if (n.isEmpty() || q.isEmpty()) return layout
        return layout.map { if (it.id == id && it.isCustom) it.copy(name = n, query = q, type = type?.tmdbPath) else it }
    }

    /**
     * Özel bölümler (kapak, devam et, yeni eklenti rafları) ve eklenti rafları silinmez, yalnızca gizlenir
     * (eklenti rafı silinse eklenti onu yine getirirdi).
     */
    fun remove(layout: List<HomeSection>, id: String): List<HomeSection> {
        val target = layout.firstOrNull { it.id == id } ?: return layout
        return when {
            target.isSpecial -> layout
            target.isShelf -> setVisible(layout, id, false)
            else -> layout.filterNot { it.id == id }
        }
    }

    /**
     * Ana sayfada ilk kez görülen eklenti raflarını düzene ekler: "yeni eklenti rafları" bölümünün hemen önüne,
     * görülme sırasıyla (böylece ilk açılışta eskisiyle aynı yerde durur). Var olan rafın adı değiştiyse güncellenir.
     * Değişiklik yoksa aynı liste döner.
     */
    fun mergeShelves(layout: List<HomeSection>, shelves: List<ShelfInfo>): List<HomeSection> {
        if (shelves.isEmpty()) return layout
        val byId = layout.associateBy { it.id }
        var changed = false
        val updated = layout.map { s ->
            val info = if (s.isShelf) shelves.firstOrNull { it.key == s.id } else null
            if (info != null && (info.title != s.name || info.providerName != s.sub)) {
                changed = true
                s.copy(name = info.title, sub = info.providerName)
            } else {
                s
            }
        }
        val fresh = shelves.filter { it.key !in byId && it.title.isNotBlank() }.distinctBy { it.key }
            .map { HomeSection(id = it.key, name = it.title, sub = it.providerName) }
        if (fresh.isEmpty()) return if (changed) updated else layout
        val anchor = updated.indexOfFirst { it.id == HomeCatalog.PROVIDERS }.takeIf { it >= 0 } ?: updated.size
        return updated.toMutableList().apply { addAll(anchor, fresh) }
    }

    /** Ekleme ekranında gösterilecek hazır kategoriler: listede olmayanlar ya da gizli olanlar. */
    fun addable(layout: List<HomeSection>): List<HomePreset> {
        val visible = layout.filter { it.visible }.mapTo(HashSet()) { it.id }
        return HomeCatalog.PRESETS.filter { it.id !in visible }
    }
}
