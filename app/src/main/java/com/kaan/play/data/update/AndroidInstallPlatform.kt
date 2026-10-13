package com.kaan.play.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/** [InstallPlatform]'un Android gerçeklemesi: FileProvider ile sistem kurulum ekranı, "bilinmeyen kaynaklar" ayarı. */
class AndroidInstallPlatform(private val context: Context) : InstallPlatform {

    // API 26 öncesinde uygulama başına izin yoktur (genel ayar kurulum ekranında sorulur).
    override fun canInstall(): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    } catch (_: SecurityException) {
        false // izin bildirimi eksikse çökme yerine ayar/tarayıcı yedeğine düş
    }

    override fun openInstaller(file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: SecurityException) {
        false
    }

    override fun openInstallPermissionSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun isOurPackage(file: File): Boolean = try {
        context.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName == context.packageName
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
