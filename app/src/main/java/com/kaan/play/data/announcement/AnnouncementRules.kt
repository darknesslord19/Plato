package com.kaan.play.data.announcement

import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.update.UpdateRules
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android'den bağımsız duyuru gösterim kuralları (birim testli).
 */
object AnnouncementRules {

    /** ISO-8601 YYYY-MM-DD biçiminde bugünün tarihi. */
    fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Ham JSON metnini duyuru listesine ayrıştırır. Bozuk JSON veya ayrıştırma hatasında null döner.
     */
    fun parse(rawJson: String, json: Json = PlayJson): List<Announcement>? = try {
        json.decodeFromString<AnnouncementsPayload>(rawJson).announcements
    } catch (_: Exception) {
        null
    }

    /**
     * Bağlantının güvenli olup olmadığını (yalnızca https) denetler.
     */
    fun isSafeLink(url: String): Boolean {
        val http = url.toHttpUrlOrNull() ?: return false
        return http.isHttps
    }

    /**
     * [until] tarihi geçmişse true döner. until belirtilmemişse süresi geçmemiş sayılır (false).
     * Tarih biçimi "YYYY-MM-DD" olmalıdır.
     */
    fun isExpired(until: String?, today: String = todayString()): Boolean {
        if (until.isNullOrBlank()) return false
        return until < today
    }

    /**
     * Tek bir duyurunun gösterilmeye uygun olup olmadığını doğrular:
     * - id, title ve message boş olamaz.
     * - Daha önce görülmemiş olmalıdır (id !in seenIds).
     * - Süresi geçmemiş olmalıdır (!isExpired).
     * - minVersion ve maxVersion sınırlarına uymalıdır (UpdateRules.isNewer ile).
     * - link varsa güvenli https olmalıdır.
     */
    fun isEligible(
        announcement: Announcement,
        installedVersion: String,
        seenIds: Set<String>,
        today: String = todayString(),
    ): Boolean {
        if (announcement.id.isBlank()) return false
        if (announcement.title.isBlank()) return false
        if (announcement.message.isBlank()) return false
        if (announcement.id in seenIds) return false
        if (isExpired(announcement.until, today)) return false
        if (announcement.minVersion != null && UpdateRules.isNewer(announcement.minVersion, installedVersion)) return false
        if (announcement.maxVersion != null && UpdateRules.isNewer(installedVersion, announcement.maxVersion)) return false
        if (announcement.link != null && !isSafeLink(announcement.link)) return false
        return true
    }

    /**
     * Duyuru listesi içinden süresi geçmemiş, sürüm aralığına uyan, daha önce görülmemiş
     * ve linki güvenli olan ilk duyuruyu seçer. Uygun duyuru yoksa null döner.
     */
    fun pick(
        announcements: List<Announcement>,
        installedVersion: String,
        seenIds: Set<String>,
        today: String = todayString(),
    ): Announcement? {
        return announcements.firstOrNull { isEligible(it, installedVersion, seenIds, today) }
    }
}
