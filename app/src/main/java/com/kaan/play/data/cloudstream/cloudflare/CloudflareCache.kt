package com.kaan.play.data.cloudstream.cloudflare

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Alan adı başına Cloudflare çerez ve User-Agent önbelleği.
 * Çerezler alan adına göre kesin olarak izole edilir; başka sitelere sızmaz.
 */
object CloudflareCache {
    private val cache = ConcurrentHashMap<String, CloudflareCredentials>()

    fun normalizeHost(hostOrUrl: String): String {
        val host = try {
            if (hostOrUrl.startsWith("http://") || hostOrUrl.startsWith("https://")) {
                URI(hostOrUrl).host ?: hostOrUrl
            } else {
                hostOrUrl
            }
        } catch (_: Exception) {
            hostOrUrl
        }
        return host.lowercase().trim().removePrefix("www.")
    }

    fun get(hostOrUrl: String): CloudflareCredentials? {
        val host = normalizeHost(hostOrUrl)
        val creds = cache[host] ?: return null
        if (creds.isExpired) {
            cache.remove(host)
            return null
        }
        return creds
    }

    fun put(hostOrUrl: String, credentials: CloudflareCredentials) {
        if (!credentials.cookies.containsKey("cf_clearance")) return
        val host = normalizeHost(hostOrUrl)
        cache[host] = credentials
    }

    fun clear(hostOrUrl: String? = null) {
        if (hostOrUrl == null) {
            cache.clear()
        } else {
            cache.remove(normalizeHost(hostOrUrl))
        }
    }

    fun count(): Int = cache.size
}
