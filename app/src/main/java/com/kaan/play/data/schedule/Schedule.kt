package com.kaan.play.data.schedule

/**
 * Takvim günleri `yyyy-MM-dd` → 1970-01-01'den beri gün sayısı. java.time Android 8 altında (minSdk 24, TV
 * kutuları) yok; bu yüzden küçük, saf bir hesap kullanılır (Howard Hinnant'ın sivil takvim algoritması).
 */
object Days {
    fun parse(text: String): Long? {
        val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(text) ?: return null
        val (y, mo, d) = m.destructured
        val month = mo.toInt()
        val day = d.toInt()
        if (month !in 1..12 || day !in 1..31) return null
        return fromCivil(y.toLong(), month, day)
    }

    fun fromCivil(year: Long, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** Cihazın yerel saat dilimine göre bugün. */
    fun today(): Long {
        val c = java.util.Calendar.getInstance()
        return fromCivil(c.get(java.util.Calendar.YEAR).toLong(), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
}

/** Yayınlanan ya da yayınlanacak tek bölüm; [airDate] `yyyy-MM-dd`. */
data class AirEpisode(val season: Int, val episode: Int, val name: String, val airDate: String) {
    val day: Long? get() = Days.parse(airDate)
    /** Bildirim kimliği için: dizi + sezon + bölüm. */
    fun keyFor(tvId: Int) = "$tvId:$season:$episode"
}

/** Bir dizinin son yayınlanan ve sıradaki bölümü. */
data class ShowAirInfo(
    val tvId: Int,
    val title: String,
    val posterPath: String?,
    val last: AirEpisode?,
    val next: AirEpisode?,
)

/** Takvim satırı: hangi dizinin hangi bölümü, hangi gün; [aired] geçmişte mi. */
data class CalendarEntry(val show: ShowAirInfo, val episode: AirEpisode, val day: Long, val aired: Boolean)

/** Takvim ve bildirim kuralları (Android'den bağımsız, birim testli). */
object ScheduleRules {
    /** Takvimde geriye dönük gösterilen gün sayısı ("son yayınlananlar"). */
    const val RECENT_DAYS = 7L
    /** İleriye dönük gösterilen gün sayısı. */
    const val UPCOMING_DAYS = 60L
    /** Bildirim yalnızca bu kadar gün içinde yayınlanan bölüm için gider (eski bölümler bildirilmez). */
    const val ALERT_WINDOW_DAYS = 2L

    /**
     * Takvim: son [RECENT_DAYS] günde yayınlananlar (en yeni üstte) ve önümüzdeki [UPCOMING_DAYS] gündekiler
     * (en yakın üstte). Aynı dizinin aynı bölümü bir kez.
     */
    fun calendar(shows: List<ShowAirInfo>, today: Long): Pair<List<CalendarEntry>, List<CalendarEntry>> {
        val recent = ArrayList<CalendarEntry>()
        val upcoming = ArrayList<CalendarEntry>()
        val seen = HashSet<String>()
        shows.forEach { show ->
            listOfNotNull(show.last, show.next).forEach { ep ->
                val d = ep.day ?: return@forEach
                if (!seen.add(ep.keyFor(show.tvId))) return@forEach
                when {
                    d <= today && d >= today - RECENT_DAYS -> recent += CalendarEntry(show, ep, d, true)
                    d > today && d <= today + UPCOMING_DAYS -> upcoming += CalendarEntry(show, ep, d, false)
                }
            }
        }
        return recent.sortedByDescending { it.day } to upcoming.sortedBy { it.day }
    }

    /**
     * Bildirilecek yeni bölümler: son bölüm bugün ya da son [ALERT_WINDOW_DAYS] gün içinde yayınlandıysa ve daha
     * önce bildirilmediyse. Gelecek tarihli bölüm bildirilmez.
     */
    fun newAlerts(shows: List<ShowAirInfo>, notified: Set<String>, today: Long): List<Pair<ShowAirInfo, AirEpisode>> =
        shows.mapNotNull { show ->
            val ep = show.last ?: return@mapNotNull null
            val d = ep.day ?: return@mapNotNull null
            val recent = d <= today && d >= today - ALERT_WINDOW_DAYS
            if (recent && ep.keyFor(show.tvId) !in notified) show to ep else null
        }

    /** Bildirilen anahtarlar sınırsız büyümesin: en yeni [max] tanesi tutulur. */
    fun trimNotified(keys: List<String>, max: Int = 400): List<String> = keys.takeLast(max)
}
