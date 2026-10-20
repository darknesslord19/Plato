package com.kaan.play.data.announcement

import kotlinx.serialization.Serializable

/**
 * announcements.json dosyasındaki duyurular listesi sarmalayıcısı.
 */
@Serializable
data class AnnouncementsPayload(
    val announcements: List<Announcement> = emptyList(),
)

/**
 * Tek bir uygulama içi duyuru.
 * [id]: Benzersiz duyuru kimliği (örn. "2026-10-21-1").
 * [title]: Duyuru başlığı.
 * [message]: Duyuru metni.
 * [link]: İsteğe bağlı bağlantı (yalnızca https kabul edilir).
 * [minVersion]: İsteğe bağlı en küçük sürüm ("1.1.0").
 * [maxVersion]: İsteğe bağlı en büyük sürüm.
 * [until]: İsteğe bağlı son geçerlilik tarihi (ISO-8601 YYYY-MM-DD).
 */
@Serializable
data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val link: String? = null,
    val minVersion: String? = null,
    val maxVersion: String? = null,
    val until: String? = null,
)
