package com.kaan.play.data.cloudstream.cloudflare

import okhttp3.Headers
import okhttp3.Response

/**
 * Yanıtın Cloudflare koruma/challenge sayfası olup olmadığını tespit eder.
 */
object CloudflareDetector {

    fun isChallenge(response: Response, peekBody: String? = null): Boolean {
        return isChallenge(response.code, response.headers, peekBody)
    }

    fun isChallenge(code: Int, headers: Headers, peekBody: String?): Boolean {
        if (code in 200..299) return false

        val cfMitigated = headers["cf-mitigated"]
        if (cfMitigated.equals("challenge", ignoreCase = true)) return true

        val server = headers["server"].orEmpty().lowercase()
        val cfRay = headers["cf-ray"]
        val isCloudflare = server.contains("cloudflare") || !cfRay.isNullOrBlank()

        if (peekBody != null) {
            val lower = peekBody.lowercase()
            if (lower.contains("_cf_chl_opt") ||
                lower.contains("challenge-error-text") ||
                lower.contains("orchestrate/chl_page") ||
                lower.contains("challenges.cloudflare.com") ||
                lower.contains("cf-challenge-running") ||
                lower.contains("#challenge-form") ||
                lower.contains("#cf-challenge-running") ||
                lower.contains("cf-browser-verification") ||
                lower.contains("enable javascript and cookies to continue")
            ) return true
            if (lower.contains("<title>just a moment...</title>") || lower.contains("just a moment...")) return true
            if (isCloudflare && (lower.contains("turnstile") || lower.contains("ray id:"))) return true
        }

        if (code in setOf(403, 503, 429) && isCloudflare) {
            return true
        }

        return false
    }

    fun isInteractiveChallenge(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("cf-turnstile") ||
                lower.contains("turnstile.cloudflare.com") ||
                lower.contains("challenge-stage") ||
                lower.contains("data-translate=\"why_captcha_headline\"")
    }
}
