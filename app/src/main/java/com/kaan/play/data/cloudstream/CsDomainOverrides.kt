package com.kaan.play.data.cloudstream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Site adresi geçersiz kılma kuralları (Android'den bağımsız, birim testli).
 *
 * Kullanıcı bir CloudStream eklentisinin sitesi alan adı değiştirdiğinde yeni adresi kendisi girer; eklenti
 * yüklenirken `MainAPI.mainUrl` bu adresle değiştirilir. Yalnızca `şema://sunucu[:port]` saklanır (yol, sorgu,
 * parça atılır), çünkü eklentiler yolları `mainUrl`'nin arkasına kendileri ekler.
 */
object CsDomainRules {

    /**
     * Kullanıcının yazdığı metni kanonik adrese çevirir; geçersizse null.
     * Şema yoksa `https://` eklenir; yalnızca http/https; kullanıcı:parola, yerel/özel ağ adresleri ve
     * `localhost` reddedilir ([PlayNetworkGuard] ile aynı kural).
     */
    fun normalize(input: String): String? {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(text)
        val withScheme = if (hasScheme) text else "https://$text"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host
        if (host.isEmpty() || (!host.contains('.') && !host.contains(':'))) return null
        val lowerHost = host.lowercase()
        // Yerel makine her zaman reddedilir (koruma, oturumda açılan yerel portlara izin verdiği için ayrıca bakılır).
        if (lowerHost == "localhost" || lowerHost.endsWith(".localhost") || lowerHost.startsWith("127.")) return null
        if (PlayNetworkGuard.isBlockedHost(host, url.port)) return null
        val defaultPort = if (url.scheme == "https") 443 else 80
        val hostPart = if (host.contains(':')) "[$host]" else host
        val portPart = if (url.port != defaultPort) ":${url.port}" else ""
        return "${url.scheme}://$hostPart$portPart"
    }

    /** Ekranda kısa gösterim: şema olmadan sunucu adı (ör. "yeni.site"). */
    fun displayHost(url: String): String =
        url.toHttpUrlOrNull()?.host ?: url.substringAfter("://").substringBefore('/')
}

/**
 * Eklenti başına site adresi geçersiz kılmaları (DataStore `cs_domains`). Anahtar eklentinin `internalName`'idir,
 * böylece eklenti kaldırılıp yeniden kurulsa da girilen adres korunur.
 */
class CsDomainOverrides(private val store: DataStore<Preferences>) {

    /** internalName → adres. */
    val all: Flow<Map<String, String>> = store.data
        .map { prefs ->
            prefs.asMap().mapNotNull { (k, v) ->
                val name = k.name.removePrefix(PREFIX).takeIf { k.name.startsWith(PREFIX) } ?: return@mapNotNull null
                (v as? String)?.let { name to it }
            }.toMap()
        }
        .distinctUntilChanged()

    suspend fun get(internalName: String): String? = store.data.first()[key(internalName)]

    /**
     * Adresi kaydeder. [url] null ya da boşsa geçersiz kılma silinir (eklentinin kendi adresine dönülür).
     * Geçersiz adres için false döner ve hiçbir şey yazılmaz.
     */
    suspend fun set(internalName: String, url: String?): Boolean {
        if (url.isNullOrBlank()) {
            store.edit { it.remove(key(internalName)) }
            return true
        }
        val normalized = CsDomainRules.normalize(url) ?: return false
        store.edit { it[key(internalName)] = normalized }
        return true
    }

    private fun key(internalName: String) = stringPreferencesKey(PREFIX + internalName)

    private companion object {
        const val PREFIX = "cs_domain_"
    }
}
