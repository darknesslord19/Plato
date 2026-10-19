package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** `play://add?repo=<adres>` ya da `play://add?nuvio=<adres>` bağlantısının sonucu (tek seferlik bildirim). */
sealed interface DeepLinkEvent {
    data class Added(val result: AddResult) : DeepLinkEvent
    data class Failed(val error: AddonException) : DeepLinkEvent
    data object Invalid : DeepLinkEvent
}

data class DeepLinkPayload(
    val url: String,
    val expectedType: SourceType?,
)

/** Android'den bağımsız bağlantı kuralları (birim testli). */
object DeepLink {
    const val SCHEME = "play"
    const val HOST = "add"

    /** `nuvio` ile `nuvio_plugin` eşanlamlıdır; boş/boşluk değer ötekine düşer (getQueryParameter boşta "" döndürür). */
    fun nuvioParam(nuvio: String?, nuvioPlugin: String?): String? =
        listOf(nuvio, nuvioPlugin).firstOrNull { !it.isNullOrBlank() }

    fun parse(
        scheme: String?,
        host: String?,
        repo: String?,
        nuvio: String?,
        nuvioPlugin: String? = null,
    ): DeepLinkPayload? {
        if (scheme != SCHEME || host != HOST) return null
        val (raw, type) = when {
            !repo.isNullOrBlank() -> repo to SourceType.CLOUDSTREAM
            !nuvioPlugin.isNullOrBlank() -> nuvioPlugin to SourceType.NUVIO_PLUGIN
            !nuvio.isNullOrBlank() -> nuvio to SourceType.NUVIO_REPO
            else -> return null
        }
        val trimmed = raw.trim()
        val url = trimmed.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" && url.scheme != "http") return null
        return DeepLinkPayload(url.toString(), type)
    }

    /**
     * Eklenecek adres; bağlantı `play://add` değilse, `repo`/`nuvio` yoksa ya da değer http(s) adresi değilse null.
     */
    fun target(scheme: String?, host: String?, repo: String?, nuvio: String?): String? =
        parse(scheme, host, repo, nuvio)?.url
}

/**
 * Gelen `play://add` bağlantısı: geçerliyse önce kullanıcıdan onay ister ([pending]); "Ekle" denince mevcut
 * [SourceInstaller] ile ekler ve sonucu [events] ile bildirir. Onay olmadan hiçbir şey eklenmez.
 */
class DeepLinkHandler(
    private val installer: SourceInstaller,
    private val scope: CoroutineScope,
) {
    // Kanal: bildirim, arayüz dinlemeye başlamadan önce üretilse de (soğuk açılış) bir kez ve kaybolmadan ulaşır.
    private val channel = Channel<DeepLinkEvent>(Channel.BUFFERED)
    val events: Flow<DeepLinkEvent> = channel.receiveAsFlow()

    /** Onay bekleyen yük; null = onay penceresi kapalı. Etkinlik yeniden oluşsa da (döndürme) pencere kalır. */
    private val _pending = MutableStateFlow<DeepLinkPayload?>(null)
    val pending: StateFlow<DeepLinkPayload?> = _pending.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun handle(scheme: String?, host: String?, repo: String?, nuvio: String?, nuvioPlugin: String? = null) {
        if (scheme != DeepLink.SCHEME) return
        val payload = DeepLink.parse(scheme, host, repo, nuvio, nuvioPlugin)
        if (payload == null) channel.trySend(DeepLinkEvent.Invalid) else _pending.value = payload
    }

    fun dismiss() {
        if (_busy.value) return
        _pending.value = null
    }

    fun confirm() {
        val payload = _pending.value ?: return
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            val event = try {
                val result = installer.add(payload.url, payload.expectedType)
                _pending.value = null
                DeepLinkEvent.Added(result)
            } catch (e: AddonException) {
                _pending.value = null
                DeepLinkEvent.Failed(e)
            } finally {
                _busy.value = false
            }
            channel.send(event)
        }
    }
}
