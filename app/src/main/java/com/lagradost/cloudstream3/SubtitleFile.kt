package com.lagradost.cloudstream3

/**
 * Oynatıcıya iletilecek altyazı dosyası. Eklentiler bu sınıfı `com.lagradost.cloudstream3`
 * paketinde arar (`utils` değil); paket yanlışsa yükleme `NoClassDefFoundError` verir.
 */
class SubtitleFile(var lang: String, var url: String) {
    var headers: Map<String, String>? = null
}
