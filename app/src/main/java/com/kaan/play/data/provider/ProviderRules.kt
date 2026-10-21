package com.kaan.play.data.provider

import java.util.Locale

/**
 * Sağlayıcı seçici ve sıralama kuralları (Android'den bağımsız saf fonksiyonlar).
 */
object ProviderRules {

    /**
     * Sekmeye göre süzme:
     * - [ProviderTab.All]: hepsi
     * - [ProviderTab.CloudStream]: yalnızca CS eklentileri
     * - [ProviderTab.Nuvio]: Nuvio addon'ları ve Nuvio JS plugin'leri
     */
    fun filterByTab(items: List<ProviderItem>, tab: ProviderTab): List<ProviderItem> = when (tab) {
        ProviderTab.All -> items
        ProviderTab.CloudStream -> items.filter { it.kind == ProviderKind.CloudStream }
        ProviderTab.Nuvio -> items.filter { it.kind == ProviderKind.NuvioAddon || it.kind == ProviderKind.NuvioPlugin }
    }

    /**
     * Arama sorgusuna göre süzme (büyük/küçük harf duyarsız, Türkçe yerel ayar destekli).
     */
    fun filterByQuery(items: List<ProviderItem>, query: String): List<ProviderItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items
        val lowerQuery = trimmed.lowercase(Locale.getDefault())
        return items.filter { it.name.lowercase(Locale.getDefault()).contains(lowerQuery) }
    }

    /**
     * Sabitlenenleri en üste alır, kendi aralarında ve sabitlenmeyenler kendi aralarında alfabetik sıralanır.
     */
    fun applyPinningAndSort(items: List<ProviderItem>, pinnedIds: Set<String>): List<ProviderItem> {
        return items
            .map { item -> item.copy(isPinned = item.id in pinnedIds) }
            .sortedWith(
                compareByDescending<ProviderItem> { it.isPinned }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.id },
            )
    }

    /**
     * Çoklu seçim modunda bir öğeye dokunulduğunda yeni seçim kümesini hesaplar.
     * Boş kalırsa veya tümü temizlenirse boş döner (boş küme = Tüm sağlayıcılar).
     */
    fun toggleMultiSelect(currentSelected: Set<String>, id: String): Set<String> {
        if (id == ProviderConstants.ALL_PROVIDERS_ID) {
            return emptySet()
        }
        val cleanCurrent = currentSelected - ProviderConstants.ALL_PROVIDERS_ID
        val next = if (id in cleanCurrent) cleanCurrent - id else cleanCurrent + id
        return next
    }

    /**
     * Tekli seçim: dokunulan öğe kimliğini içeren tek elemanlı küme veya "tüm sağlayıcılar" için boş küme.
     */
    fun singleSelect(id: String): Set<String> {
        return if (id == ProviderConstants.ALL_PROVIDERS_ID) emptySet() else setOf(id)
    }

    /**
     * Seçim durumunun "Tüm sağlayıcılar" olup olmadığını denetler.
     */
    fun isAllProviders(selectedIds: Set<String>): Boolean {
        return selectedIds.isEmpty() || selectedIds.contains(ProviderConstants.ALL_PROVIDERS_ID)
    }

    /**
     * Varsayılan ana sayfa ("Tüm sağlayıcılar") için sağlayıcı sırası:
     * 1. Kurulu ve açık Nuvio katalog eklentileri (eklenme sırasıyla)
     * 2. Sabitlenmiş (iğnelenmiş) CloudStream eklentileri (kendi aralarında alfabetik)
     */
    fun buildHomeProviders(
        nuvioProviders: List<ProviderItem>,
        csProviders: List<ProviderItem>,
        pinnedIds: Set<String>,
    ): List<ProviderItem> {
        val pinnedCs = csProviders
            .filter { it.id in pinnedIds }
            .sortedWith(
                compareBy<ProviderItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.id },
            )
        return nuvioProviders + pinnedCs
    }

    /**
     * Ana sayfadaki bir sağlayıcı bölümünde gösterilecek raflar. Hata, boş sonuç ve öğesiz raflar gösterilmez:
     * sunucu kapalıysa ya da yanıt vermezse bölüm sessizce gizlenir (boş liste).
     */
    fun homeSectionRows(result: CatalogResult): List<CatalogRow> = when (result) {
        is CatalogResult.Success -> result.rows.filter { it.items.isNotEmpty() }
        else -> emptyList()
    }

    /**
     * Ana sayfada sağlayıcı raflarıyla aynı adı taşıyan TMDB raflarının anahtarları (ikinci kopya gizlenir).
     * Yalnızca gerçekten görünen (yüklenmiş, öğeli) sağlayıcı rafları sayılır; addon rafı yüklenemezse TMDB rafı
     * görünür kalır. Karşılaştırma büyük/küçük harf ve fazla boşluk duyarsız, Türkçe harf kurallarıyla
     * ("Popüler filmler" = "Popüler Filmler").
     */
    fun <K> duplicateTmdbRows(tmdbTitles: Map<K, String>, shownProviderTitles: Collection<String>): Set<K> {
        val shown = shownProviderTitles.mapTo(HashSet()) { normalizeRowTitle(it) }
        if (shown.isEmpty()) return emptySet()
        return tmdbTitles.filterValues { normalizeRowTitle(it) in shown }.keys
    }

    private val TURKISH = Locale.forLanguageTag("tr")
    private val SPACES = Regex("\\s+")

    internal fun normalizeRowTitle(title: String): String =
        title.trim().replace(SPACES, " ").lowercase(TURKISH)
}
