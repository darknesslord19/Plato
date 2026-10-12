package com.kaan.play.data.web

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Web görünümünün Android'den bağımsız kuralları (birim testli): hangi alan adı engellenir, hangi
 * adrese gidilebilir, bir bağlantı aynı siteye mi gidiyor. Android tarafı yalnızca bunları uygular.
 */
class WebPolicy(blockedDomains: Collection<String> = AdDomains.all) {

    private val blocked: Set<String> = blockedDomains.mapTo(HashSet()) { it.trim().lowercase(Locale.ROOT).trimEnd('.') }

    /** [host], listedeki bir alan adının kendisi ya da alt alan adıysa true. `notdoubleclick.net` eşleşmez. */
    fun isBlockedHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = host.lowercase(Locale.ROOT).trimEnd('.')
        while (true) {
            if (h in blocked) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** Tam adres için: çözümlenemeyen adres engellenmez (karar çağırana ait). */
    fun isBlockedUrl(url: String?): Boolean = isBlockedHost(url?.toHttpUrlOrNull()?.host)

    /**
     * Sayfa içi gezinme izni: yalnızca http/https ve engelli alan adı olmayan adresler.
     * `intent://`, `market://`, `tel:`, `javascript:`, `data:` ve harici uygulama bağlantıları reddedilir.
     */
    fun allowsNavigation(url: String?): Boolean {
        val parsed = url?.toHttpUrlOrNull() ?: return false // OkHttp yalnızca http/https çözümler
        return !isBlockedHost(parsed.host)
    }

    companion object {
        /**
         * İki adres aynı siteye mi ait: sunucu adları aynı ya da biri ötekinin alt alan adı
         * (`www.a.com` ~ `a.com`). Tam bir "kayıtlı alan adı" listesi tutmayız; en sade kural bu.
         */
        fun sameSite(a: String?, b: String?): Boolean {
            val ha = a?.toHttpUrlOrNull()?.host ?: return false
            val hb = b?.toHttpUrlOrNull()?.host ?: return false
            return ha == hb || ha.endsWith(".$hb") || hb.endsWith(".$ha")
        }
    }
}
