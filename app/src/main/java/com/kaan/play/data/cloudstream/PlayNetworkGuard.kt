package com.kaan.play.data.cloudstream

/**
 * CS plugin'lerine karşı basit ağ güvenliği: yerel/özel IP'ler reddedilir.
 * Faz 2 NetworkPolicy ile aynı prensipler uygulanır,
 * DEX yüklü kod ve ağ katmanı bu nesneyi denetler.
 */
object PlayNetworkGuard {
    private val PRIVATE_RANGES = listOf(
        "10.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
        "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.",
        "172.31.", "192.168.", "127.", "169.254.", "100.64.", "::1", "fc", "fd",
    )

    fun checkUrl(url: String) {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw SecurityException("CS plugin: only http(s) allowed: $url")
        }
        val host = try { java.net.URL(url).host } catch (_: Exception) { return }
        if (isPrivateHost(host)) throw SecurityException("CS plugin: private address blocked: $host")
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost") return true
        PRIVATE_RANGES.forEach { prefix -> if (host.startsWith(prefix)) return true }
        return false
    }
}
