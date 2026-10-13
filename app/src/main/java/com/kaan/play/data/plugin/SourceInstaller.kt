package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.AddonUrl
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.cloudstream.CsRepository

/** "Eklenti ekle" sonucu: ne eklendi. Arayüz buna göre sade bir bildirim gösterir. */
sealed interface AddResult {
    data class Addon(val name: String) : AddResult
    data class PluginRepo(val name: String, val installed: Int, val skipped: Int) : AddResult
    data class CsRepo(val name: String, val installed: Int, val skipped: Int) : AddResult
}

/**
 * "Eklenti ekle" penceresinin tek kapısı: adresi bir kez indirir ve **içeriğine** bakarak türünü anlar (etikete ya da
 * adın şekline değil): `resources` varsa addon, `scrapers` dizisi varsa eklenti deposu,
 * `pluginLists` varsa CloudStream deposu. Kullanıcı hangisi olduğunu söylemek zorunda değildir.
 */
class SourceInstaller(
    private val addons: AddonRepository,
    private val plugins: PluginRepository,
    private val csRepos: CsRepository,
    private val fetcher: ManifestFetcher,
) {

    /** @throws AddonException */
    suspend fun add(input: String): AddResult {
        val url = AddonUrl.normalize(input)
        if (addons.isKnown(url)) throw AddonException(Kind.AlreadyAdded)
        val raw = fetcher.fetch(url)
        return when (ManifestKind.classify(raw)) {
            ManifestKind.PluginRepo -> plugins.addFetched(url, raw).let { AddResult.PluginRepo(it.repo.name, it.installed, it.skipped) }
            ManifestKind.External -> csRepos.addFetched(url, raw).let { AddResult.CsRepo(it.repo.name, it.installed, it.skipped) }
            // Addon ve tanınmayan: addon ayrıştırıcısı kesin hatayı (eksik alan, `stream` yok...) verir.
            ManifestKind.Addon, ManifestKind.Unknown -> AddResult.Addon(addons.addFetched(url, raw).manifest.name)
        }
    }
}
