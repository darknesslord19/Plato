package com.kaan.play.data.web

import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WebSite(val id: Long, val name: String, val url: String, val addedAt: Long)

/** Bir siteyi kaldırırken web görünümünün çerez/depolama/önbellek verisini siler (Android tarafı uygular). */
fun interface WebDataCleaner {
    /** [anySiteLeft] false ise (son site kaldırıldı) uygulamanın tüm web verisi silinir. */
    suspend fun clear(url: String, anySiteLeft: Boolean)
}

/**
 * Kullanıcının eklediği web siteleri. `addons` tablosunda [AddonType.WEB_SITE] satırı olarak durur
 * (ad = ad, sourceUrl = adres; manifest alanları kullanılmaz), bu yüzden şema/göç değişmez.
 */
class WebSiteRepository(
    private val dao: AddonDao,
    private val cleaner: WebDataCleaner,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val sites: Flow<List<WebSite>> = dao.observeOfType(AddonType.WEB_SITE).map { rows -> rows.map { it.toSite() } }

    /**
     * [input] normalleştirilir ([WebSiteUrl]); [name] boşsa sunucu adı kullanılır.
     * @throws WebSiteException
     */
    suspend fun add(name: String, input: String): WebSite {
        val url = WebSiteUrl.normalize(input)
        if (dao.countBySourceUrl(url) > 0) throw WebSiteException(WebSiteException.Kind.AlreadyAdded)

        val host = WebSiteUrl.displayHost(url)
        val entity = AddonEntity(
            type = AddonType.WEB_SITE,
            sourceUrl = url,
            addonId = host,
            name = name.trim().take(MAX_NAME).ifEmpty { host },
            version = null,
            description = null,
            logoUrl = null,
            enabled = true,
            manifestJson = "{}",
            addedAt = clock(),
        )
        val rowId = dao.insert(entity)
        if (rowId == -1L) throw WebSiteException(WebSiteException.Kind.AlreadyAdded)
        return entity.copy(id = rowId).toSite()
    }

    /** Siteyi siler ve web verisini temizler. */
    suspend fun remove(site: WebSite) {
        dao.delete(site.id)
        cleaner.clear(site.url, anySiteLeft = dao.countOfType(AddonType.WEB_SITE) > 0)
    }

    private fun AddonEntity.toSite() = WebSite(id = id, name = name, url = sourceUrl, addedAt = addedAt)

    companion object {
        const val MAX_NAME = 60
    }
}
