package com.kaan.play.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Play'in kendi güncelleme bildirimi (Firebase Realtime Database gibi düz bir JSON adresinden okunur).
 *
 * ```json
 * {
 *   "version": "1.1.5",
 *   "apkUrl": "https://github.com/<sahip>/<depo>/releases/download/v1.1.5/Play-1.1.5.apk",
 *   "sha256": "<64 haneli onaltılık SHA-256>",
 *   "size": 47518540,
 *   "pageUrl": "https://github.com/<sahip>/<depo>/releases/tag/v1.1.5",
 *   "notes": "Bu sürümde neler var (isteğe bağlı, kısa)"
 * }
 * ```
 * `version`, `apkUrl` ve `sha256` zorunludur; `size`, `pageUrl`, `notes` isteğe bağlıdır.
 */
@Serializable
data class UpdateManifestJson(
    val version: String? = null,
    val apkUrl: String? = null,
    val sha256: String? = null,
    val size: Long = 0,
    val pageUrl: String? = null,
    val notes: String? = null,
)

/** Bildirimin denetimi (Android'den bağımsız, birim testli). */
object UpdateManifestRules {

    sealed interface Result {
        /** Bildirim geçerli: [info] yüklüden yeni sürüm ya da (yeni değilse) null. */
        data class Valid(val info: UpdateInfo?) : Result
        /** Bildirim okunamadı/kurallara uymuyor: GitHub'a geri düşülür. */
        data class Invalid(val reason: String) : Result
    }

    private const val MAX_NOTES = 600

    /**
     * [repo] ("sahip/depo"): APK yalnızca bu deponun GitHub release'lerinden indirilebilir. Bildirim adresine yazabilen
     * biri başka bir dosyayı göstermeye çalışsa bile kabul edilmez; ayrıca Android farklı imzalı bir APK'yı mevcut
     * uygulamanın üzerine kurmaz.
     */
    fun evaluate(text: String, installedVersion: String, repo: String, json: Json): Result {
        val obj = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return Result.Invalid("not an object")
        val m = try {
            json.decodeFromJsonElement(UpdateManifestJson.serializer(), obj)
        } catch (_: Exception) {
            return Result.Invalid("bad fields")
        }
        val version = m.version?.trim()?.removePrefix("v")?.removePrefix("V")?.takeIf { it.isNotEmpty() && it.length <= 24 }
            ?: return Result.Invalid("version")
        if (!UpdateRules.isNewer("v$version", "0")) return Result.Invalid("version format")
        val apkUrl = m.apkUrl?.trim().orEmpty()
        if (!isAllowedApkUrl(apkUrl, repo)) return Result.Invalid("apkUrl")
        val name = apkUrl.toHttpUrlOrNull()?.pathSegments?.lastOrNull().orEmpty()
        if (!UpdateApkRules.isPlausibleApkName(name)) return Result.Invalid("apk name")
        val sha = m.sha256?.trim()?.lowercase()?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) }
            ?: return Result.Invalid("sha256")
        if (m.size < 0 || m.size > UpdateApkRules.MAX_APK_BYTES) return Result.Invalid("size")
        val page = m.pageUrl?.trim()?.takeIf { UpdateRules.isSafePage(it) }
            ?: "https://github.com/$repo/releases/tag/v$version"

        if (!UpdateRules.isNewer(version, installedVersion)) return Result.Valid(null)
        val notes = m.notes?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTES)
        return Result.Valid(
            UpdateInfo(
                tag = "v$version",
                pageUrl = page,
                apk = UpdateApk(name = name, url = apkUrl, size = m.size, sha256Url = "", sha256 = sha),
                notes = notes,
            ),
        )
    }

    /** https://github.com/<repo>/releases/download/... (büyük/küçük harf duyarsız sahip/depo). */
    fun isAllowedApkUrl(url: String, repo: String): Boolean {
        val http = url.toHttpUrlOrNull() ?: return false
        if (!http.isHttps || http.host != "github.com") return false
        val seg = http.pathSegments
        val (owner, name) = repo.split('/').takeIf { it.size == 2 } ?: return false
        return seg.size >= 5 &&
            seg[0].equals(owner, ignoreCase = true) &&
            seg[1].equals(name, ignoreCase = true) &&
            seg[2] == "releases" && seg[3] == "download"
    }
}
