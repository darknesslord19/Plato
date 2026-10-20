package com.kaan.play.data.cloudstream.extractors

import com.lagradost.cloudstream3.extractors.ByseSX

/**
 * SezonlukDizi ve benzeri kaynakların kullandığı Byse CDN alan adı (bysejikuar.com).
 * CloudStream'in yerleşik [ByseSX] çıkarıcısını bu alan adına bağlar.
 */
class ByseJikuar : ByseSX() {
    override val mainUrl: String = "https://bysejikuar.com"
}
