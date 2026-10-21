package com.kaan.play.data.addon

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.first

/**
 * İlk açılışta eklenen varsayılan addon'lar için ortak tohumlama düzeni.
 *
 * Kurallar (her grup için ayrı DataStore bayrağı):
 * 1. Tohumlama grup başına yalnızca bir kez yapılır; bayrak açıksa hiçbir şey eklenmez.
 * 2. Kullanıcı silerse bayrak açık kaldığı için bir daha geri gelmez.
 * 3. Adres zaten kayıtlıysa yeniden eklenmez (çiftlenmez).
 * 4. Açılışı geciktirmemek için ağa çıkılmaz; manifest alanları sabit yazılır.
 */
object DefaultAddons {

    data class DefaultAddon(
        val url: String,
        val manifest: AddonManifest,
        val version: String?,
        val description: String?,
        val logoUrl: String?,
    )

    /**
     * [flag] kapalıysa [defaults] içindeki (kayıtlı olmayan) addon'ları AÇIK olarak ekler ve bayrağı açar.
     * @return Tohumlama yapıldıysa true, zaten yapılmışsa false.
     */
    suspend fun seedOnce(
        dao: AddonDao,
        store: DataStore<Preferences>,
        flag: Preferences.Key<Boolean>,
        defaults: List<DefaultAddon>,
        clock: () -> Long = System::currentTimeMillis,
    ): Boolean {
        if (store.data.first()[flag] == true) return false

        for (item in defaults) {
            if (dao.countBySourceUrl(item.url) == 0) {
                dao.insert(
                    AddonEntity(
                        type = AddonType.NUVIO_ADDON,
                        sourceUrl = item.url,
                        addonId = item.manifest.id,
                        name = item.manifest.name,
                        version = item.version,
                        description = item.description,
                        logoUrl = item.logoUrl,
                        enabled = true,
                        manifestJson = PlayJson.encodeToString(AddonManifest.serializer(), item.manifest),
                        addedAt = clock(),
                    ),
                )
            }
        }

        store.edit { it[flag] = true }
        return true
    }
}
