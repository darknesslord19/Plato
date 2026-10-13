package com.kaan.play.data.stream

/** Listede önce yüksek çözünürlük, sonra büyük dosya; kalitesi/boyutu bilinmeyenler sonda. */
fun List<PlayableStream>.ranked(): List<PlayableStream> =
    sortedWith(
        compareByDescending<PlayableStream> { it.resolution ?: 0 }
            .thenByDescending { it.sizeBytes ?: 0L },
    )

/**
 * En uygun kaynak: 1080p'ye kadar olanların en iyisi (4K çoğu cihazda takılır ve çok yer tutar); doğrudan bağlantı torrentten
 * önce gelir (torrent açılışı eş bulmayı bekler). Hiçbiri yoksa listenin başı. [ranked] sıralı liste bekler.
 */
fun List<PlayableStream>.recommended(): PlayableStream? {
    fun fits(s: PlayableStream) = (s.resolution ?: 0) in 1..1080
    return firstOrNull { fits(it) && !TorrentLinks.isMagnet(it.url) } ?: firstOrNull(::fits) ?: firstOrNull()
}
