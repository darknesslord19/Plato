package com.kaan.play.data.library

/** İzleme geçmişi kuralları (tek yerde; oynatıcı, kütüphane ve ana sayfa buna bakar). */
object WatchRules {

    /** Bundan az izlenen içerik geçmişe girmez. */
    const val MIN_RECORD_MS = 30_000L

    /** Bunun üstü "izlendi" sayılır (yüzde). */
    const val WATCHED_PERCENT = 90

    /** Konum bu aralıkla kaydedilir (oynatma sürerken). */
    const val SAVE_INTERVAL_MS = 10_000L

    /** Süre bilinmiyorsa ([durationMs] <= 0) hiçbir şey kaydedilmez. */
    fun shouldRecord(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= MIN_RECORD_MS

    /** %90'ın üstü; tam %90 henüz izlendi sayılmaz. */
    fun isWatched(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs * 100 > durationMs * WATCHED_PERCENT

    /** "Kaldığın yerden devam" teklif edilebilir: yeterince izlenmiş ama bitmemiş. */
    fun isResumable(positionMs: Long, durationMs: Long): Boolean =
        shouldRecord(positionMs, durationMs) && !isWatched(positionMs, durationMs)
}
