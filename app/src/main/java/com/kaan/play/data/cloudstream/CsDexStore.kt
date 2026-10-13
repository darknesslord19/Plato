package com.kaan.play.data.cloudstream

import android.os.Build
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * CloudStream `.cs3` dosyalarının disk yönetimi.
 * Her eklenti için iki dosya saklanır:
 *   - `{id}.dex`: DEX bytecode (PathClassLoader ile yüklenir)
 *   - `{id}.manifest`: ZIP içindeki manifest.json içeriği (eklentinin kendi beyanı)
 *
 * Dizin: `filesDir/cloudstream/`
 */
class CsDexStore(private val dir: File) {

    init { dir.mkdirs() }

    fun dexFile(id: Long): File = File(dir, "$id.dex")
    fun manifestFile(id: Long): File = File(dir, "$id.manifest")

    fun exists(id: Long): Boolean = dexFile(id).exists()

    fun delete(id: Long) {
        dexFile(id).delete()
        manifestFile(id).delete()
    }

    fun readManifest(id: Long): String? = manifestFile(id).takeIf { it.exists() }?.readText()

    /**
     * DEX dosyasını okunur-salt moda alır (Android 14+ zorunluluğu).
     * Hata olursa sessizce geçer — eski Android'lerde `setReadOnly()` yeterlidir.
     */
    fun setReadOnly(id: Long) {
        val f = dexFile(id)
        if (!f.exists()) return
        try { f.setReadOnly() } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14 (API 34): DEX'in read-only olması için OS-düzeyinde izin gerekliyse
            // PathClassLoader zaten hata verir; bu çağrı yeterlidir.
            try { f.setReadOnly() } catch (_: Exception) {}
        }
    }
}

// ---------------------------------------------------------------------------
// .cs3 indirme + açma
// ---------------------------------------------------------------------------

const val CS3_MAX_BYTES = 30 * 1024 * 1024L // 30 MB

/**
 * `.cs3` dosyasını indirir, SHA-256 özetini doğrular (boşsa atlanır), DEX ve manifest'i çıkarır.
 * @throws AddonException
 */
suspend fun downloadCs3(
    url: String,
    expectedHash: String,
    client: OkHttpClient,
    store: CsDexStore,
    pluginId: Long,
): Cs3Contents = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).build()
    val bytes: ByteArray = try {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw AddonException(Kind.Http, "${resp.code}")
            val body = resp.body ?: throw AddonException(Kind.InvalidResponse)
            val len = body.contentLength()
            if (len > CS3_MAX_BYTES) throw AddonException(Kind.TooLarge)
            body.bytes().also { if (it.size > CS3_MAX_BYTES) throw AddonException(Kind.TooLarge) }
        }
    } catch (e: AddonException) { throw e }
    catch (e: Exception) { throw AddonException(Kind.Network, cause = e) }

    // SHA-256 doğrulama
    if (expectedHash.isNotEmpty()) {
        val actual = sha256Hex(bytes)
        if (!actual.equals(expectedHash, ignoreCase = true)) {
            throw AddonException(Kind.InvalidResponse, "hash mismatch: expected=$expectedHash actual=$actual")
        }
    }

    // ZIP'ten DEX ve manifest çıkar
    extractCs3(bytes, store, pluginId)
}

data class Cs3Contents(val sha256: String, val sizeBytes: Long, val pluginManifest: String)

private fun extractCs3(bytes: ByteArray, store: CsDexStore, id: Long): Cs3Contents {
    var dexBytes: ByteArray? = null
    var manifestText: String? = null

    ZipInputStream(bytes.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            when {
                name == "classes.dex" || name.endsWith("/classes.dex") -> {
                    dexBytes = zip.readBytes().also { if (it.size > CS3_MAX_BYTES) throw AddonException(Kind.TooLarge) }
                }
                name == "manifest.json" || name.endsWith("/manifest.json") -> {
                    manifestText = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    val dex = dexBytes ?: throw AddonException(Kind.InvalidResponse, "classes.dex not found in .cs3")
    val manifest = manifestText ?: "{}"

    // DEX dosyasına yaz
    val dexFile = store.dexFile(id)
    dexFile.parentFile?.mkdirs()
    FileOutputStream(dexFile).use { it.write(dex) }
    store.setReadOnly(id)
    store.manifestFile(id).writeText(manifest)

    return Cs3Contents(
        sha256 = sha256Hex(bytes),
        sizeBytes = bytes.size.toLong(),
        pluginManifest = manifest,
    )
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
