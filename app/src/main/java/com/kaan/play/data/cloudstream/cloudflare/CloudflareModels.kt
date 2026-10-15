package com.kaan.play.data.cloudstream.cloudflare

/**
 * Cloudflare bot koruması aşma sonucu elde edilen çerezler ve User-Agent.
 */
data class CloudflareCredentials(
    val cookies: Map<String, String>,
    val userAgent: String,
    val expiresAt: Long = System.currentTimeMillis() + DEFAULT_TTL_MS,
) {
    val cookieHeader: String
        get() = cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" }

    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt

    companion object {
        const val DEFAULT_TTL_MS = 2 * 60 * 60 * 1000L // 2 saat
    }
}

sealed interface CloudflareResult {
    data class Success(val credentials: CloudflareCredentials) : CloudflareResult
    data class Failure(
        val reason: String,
        val isInteractive: Boolean = false,
    ) : CloudflareResult
}
