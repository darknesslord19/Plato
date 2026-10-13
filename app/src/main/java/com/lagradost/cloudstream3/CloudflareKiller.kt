package com.lagradost.cloudstream3

import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests

/**
 * CloudflareKiller stub'ı. CS eklentileri Cloudflare korumalı sitelere erişmek için bu sınıfı kullanabilir.
 * Play bu stub'ı no-op olarak sağlar; Cloudflare bypass gerçek uygulanmaz (WebView oturumu yok).
 */
class CloudflareKiller {
    /** Gerçek CF bypass yerine doğrudan `app` üzerinden istek dener. */
    suspend fun bypassCloudflare(url: String): NiceResponse = app.get(url)

    suspend fun getUserAgent(): String = Requests.DEFAULT_UA

    companion object {
        @JvmStatic
        val DEFAULT_HEADERS: Map<String, String> = mapOf("User-Agent" to Requests.DEFAULT_UA)
    }
}
