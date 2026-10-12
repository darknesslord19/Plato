package com.kaan.play.data.stream

/** Listede önce yüksek çözünürlük, sonra büyük dosya; kalitesi/boyutu bilinmeyenler sonda. */
fun List<PlayableStream>.ranked(): List<PlayableStream> =
    sortedWith(
        compareByDescending<PlayableStream> { it.resolution ?: 0 }
            .thenByDescending { it.sizeBytes ?: 0L },
    )

/**
 * En uygun kaynak: 1080p'ye kadar olanların en iyisi (4K çoğu cihazda takılır ve çok yer tutar);
 * hiçbiri yoksa listenin başı. [ranked] sıralı liste bekler.
 */
fun List<PlayableStream>.recommended(): PlayableStream? =
    firstOrNull { (it.resolution ?: 0) in 1..1080 } ?: firstOrNull()
