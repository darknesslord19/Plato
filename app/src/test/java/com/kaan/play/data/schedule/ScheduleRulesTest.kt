package com.kaan.play.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRulesTest {

    private val today = Days.parse("2026-10-29")!!

    private fun show(id: Int, last: String?, next: String?) = ShowAirInfo(
        tvId = id,
        title = "Dizi $id",
        posterPath = null,
        last = last?.let { AirEpisode(1, 5, "", it) },
        next = next?.let { AirEpisode(1, 6, "", it) },
    )

    @Test
    fun daysMatchesKnownDates() {
        assertEquals(0L, Days.parse("1970-01-01"))
        assertEquals(20_390L, Days.parse("2025-10-29"))
        assertEquals(Days.parse("2026-03-01")!! - 1, Days.parse("2026-02-28"))
        assertEquals(Days.parse("2024-03-01")!! - 2, Days.parse("2024-02-28")) // artık yıl
        assertNull(Days.parse("bozuk"))
        assertNull(Days.parse("2026-13-01"))
    }

    @Test
    fun calendarSplitsRecentAndUpcoming() {
        val (recent, upcoming) = ScheduleRules.calendar(
            listOf(
                show(1, "2026-10-28", "2026-11-04"),
                show(2, "2026-10-01", "2026-10-30"),
                show(3, "2026-10-29", null),
                show(4, null, "2027-06-01"), // 60 günden uzak
            ),
            today,
        )
        assertEquals(listOf(3, 1), recent.map { it.show.tvId })
        assertEquals(listOf(2, 1), upcoming.map { it.show.tvId })
        assertTrue(recent.all { it.aired })
    }

    @Test
    fun alertsOnlyForFreshUnnotifiedEpisodes() {
        val shows = listOf(
            show(1, "2026-10-29", null), // bugün → bildirilir
            show(2, "2026-10-27", null), // 2 gün önce → bildirilir
            show(3, "2026-10-20", null), // eski → bildirilmez
            show(4, "2026-10-28", null), // daha önce bildirildi
            show(5, "2026-11-02", null), // gelecek tarih (hatalı veri) → bildirilmez
        )
        val alerts = ScheduleRules.newAlerts(shows, setOf("4:1:5"), today)
        assertEquals(listOf(1, 2), alerts.map { it.first.tvId })
    }

    @Test
    fun notifiedListIsTrimmed() {
        val keys = (1..500).map { "k$it" }
        val trimmed = ScheduleRules.trimNotified(keys)
        assertEquals(400, trimmed.size)
        assertEquals("k500", trimmed.last())
    }
}
