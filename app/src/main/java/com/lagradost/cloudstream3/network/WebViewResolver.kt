package com.lagradost.cloudstream3.network

/**
 * Gerçek CloudStream'de Cloudflare/JS-korumalı sayfaları bir Android `WebView` içinde çalıştırıp
 * engellenen isteği yakalayan yardımcıdır. Play'in akış toplama çalışma zamanı arka plan havuzunda
 * çalışır (Activity/WebView yok), bu yüzden burada **gerçek bir WebView çalıştırılmaz**; yalnızca
 * arayüz uygulanır ki eklenti bu sınıfı oluşturduğunda çökmesin. Çözümleme her zaman boş döner —
 * eklenti bu durumda başka bir çıkarıcıya düşer (bilinen sınır).
 */
class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = null,
    val useOkhttp: Boolean = false,
    val script: String? = null,
    val scriptCallback: (String) -> Unit = {},
    val timeout: Long = 15_000L,
) {
    companion object {
        fun getWebViewUserAgent(): String? = null
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        userAgent: String? = null,
        callback: (String) -> Unit = {},
    ): String? = null

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        additionalData: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        callback: (String) -> Unit = {},
    ): String? = null
}
