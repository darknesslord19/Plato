package com.kaan.play.data.stream

/**
 * Eklenti ve sağlayıcı başlıklarını temizleme yardımcısı.
 * Site eklerini ("izle", "Türkçe Dublaj izle", "Full HD izle", vb.) ve parantez içindeki yıl/kalite bilgilerini temizler.
 */
object TitleCleaner {

    private val YEAR_PAREN_REGEX = Regex("""[\(\[](19\d\d|20\d\d)[\)\]]""")
    private val YEAR_WORD_REGEX = Regex("""\b(19\d\d|20\d\d)\b""")

    // Başlangıçtaki bölüm/sezon kalıpları: "S4B11 - ", "S01E05 : ", "1. Sezon 2. Bölüm - ", "12. Bölüm - "
    private val PREFIX_EPISODE_REGEX = Regex(
        """^(?:S\d+\s*[BEbe]\d+|S\d+|(?:(?:\d+\s*\.)?\s*[Ss]ezon\s*)?(?:\d+\s*\.)?\s*[Bb]ölüm|(?:Episode|Ep\.?)\s*\d+)\s*[-:]\s*""",
        RegexOption.IGNORE_CASE
    )

    // Sondaki bölüm/sezon kalıpları: " - S4B11", " - 12. Bölüm", " - 1. Sezon 2. Bölüm", " 12. Bölüm"
    private val SUFFIX_EPISODE_REGEX = Regex(
        """\s*[-:]\s*(?:S\d+\s*[BEbe]\d+|S\d+|(?:(?:\d+\s*\.)?\s*[Ss]ezon\s*)?(?:\d+\s*\.)?\s*[Bb]ölüm|(?:Episode|Ep\.?)\s*\d+).*$""",
        RegexOption.IGNORE_CASE
    )
    private val SUFFIX_EPISODE_TRAILING = Regex(
        """\s+(?:(?:\d+\s*\.)?\s*[Ss]ezon\s*)?\d+\.\s*[Bb]ölüm\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val SUFFIX_FINALE = Regex(
        """\s*[-:]\s*(?:(?:\d+\s*\.)?\s*[Ss]ezon|(?:Sezon\s+)?Finali?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Site ekleri: "Türkçe Dublaj izle", "Türkçe Altyazılı izle", "Full HD izle", "izle", vb.
    private val SITE_SUFFIX_REGEX = Regex(
        """\s*\b(?:Türkçe\s+Dublaj\s+izle|Türkçe\s+Altyazı(?:lı)?\s+izle|Full\s+HD\s+izle|HD\s+izle|1080p\s+izle|720p\s+izle|Online\s+izle|Tek\s+Parça\s+izle|izle)\b\s*""",
        RegexOption.IGNORE_CASE
    )

    // Parantez/köşeli parantez içindeki etiketler: (Türkçe Dublaj), [Altyazılı], (1080p), [Dual], (2026) vb.
    private val BRACKET_TAGS_REGEX = Regex(
        """\s*[\(\[](?:(?:Türkçe\s+)?(?:Dublaj|Altyazı(?:lı)?|Dual|TR|ENG|1080p|720p|4K|HD)[^\)\]]*|(?:19|20)\d\d)[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    fun clean(raw: String): String {
        return cleanTitleAndYear(raw).first
    }

    fun cleanTitleAndYear(raw: String): Pair<String, Int?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "" to null

        // 1. Önce yılı tespit et
        var year: Int? = YEAR_PAREN_REGEX.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
        if (year == null) {
            val words = trimmed.split(" ")
            for (w in words.reversed()) {
                val candidate = YEAR_WORD_REGEX.matchEntire(w)?.groupValues?.get(1)?.toIntOrNull()
                if (candidate != null && candidate in 1900..2099) {
                    year = candidate
                    break
                }
            }
        }

        var cleaned = trimmed

        // 2. Dıştaki site eklerini temizle ("izle", "Full HD izle", vb.)
        cleaned = cleaned.replace(SITE_SUFFIX_REGEX, " ").trim()

        // 3. Parantez içi etiketleri temizle (dublaj, altyazı, kalite, yıl)
        cleaned = cleaned.replace(BRACKET_TAGS_REGEX, " ").trim()

        // 4. Bölüm/sezon kalıplarını temizle
        cleaned = cleaned.replace(PREFIX_EPISODE_REGEX, "")
        cleaned = cleaned.replace(SUFFIX_EPISODE_REGEX, "")
        cleaned = cleaned.replace(SUFFIX_EPISODE_TRAILING, "")
        cleaned = cleaned.replace(SUFFIX_FINALE, "")

        // 5. Eğer tespit edilen yıl kelime olarak hala sonda veya ortadaysa temizle
        if (year != null) {
            cleaned = cleaned.replace(Regex("""\b$year\b"""), " ")
        }

        // 6. Kalan dangling tire, iki nokta, boşlukları temizle
        cleaned = cleaned.replace(Regex("""\s*[-:]\s*$"""), "")
        cleaned = cleaned.replace(Regex("""^\s*[-:]\s*"""), "")
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

        val finalTitle = if (cleaned.isNotBlank()) cleaned else trimmed
        return finalTitle to year
    }
}
