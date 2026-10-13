package com.kaan.play.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Güncelleme penceresinin durumu. */
sealed interface UpdateState {
    /** Gösterilecek bir şey yok. */
    data object Idle : UpdateState

    /** Yeni sürüm var; kullanıcı karar verecek. */
    data class Offer(val info: UpdateInfo) : UpdateState

    /** APK iniyor; [total] bilinmiyorsa 0. */
    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long) : UpdateState

    /** İndirildi ve özeti doğrulandı; kurulum ekranı açılacak ([UpdateFlow.installIfPossible]). */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState

    /** "Bilinmeyen kaynaklardan yükleme" izni yok; kullanıcı ayara gidip dönünce kurulum kaldığı yerden sürer. */
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateState

    /** Bir adım başarısız oldu; yedek: release sayfasını tarayıcıda aç. */
    data class Failed(val info: UpdateInfo, val reason: Reason) : UpdateState

    enum class Reason { DOWNLOAD, CHECKSUM, INSTALL }
}

/** Android'e bağlı parçalar (kurulum izni, kurulum ekranı, ayar sayfası); birim testte sahtesi kullanılır. */
interface InstallPlatform {
    /** Bu uygulama APK kurma isteğinde bulunabilir mi (API 26+ için "bilinmeyen kaynaklar" izni). */
    fun canInstall(): Boolean

    /** Sistem kurulum ekranını açar; açılamadıysa false. */
    fun openInstaller(file: File): Boolean

    /** Bu uygulamanın "bilinmeyen kaynaklardan yükleme" ayarını açar; açılamadıysa false. */
    fun openInstallPermissionSettings(): Boolean

    /** İndirilen dosya gerçekten bu uygulamanın paketi mi. */
    fun isOurPackage(file: File): Boolean
}

/**
 * Uygulama içi güncelleme akışı: [UpdateChecker]'ın haberi → indir (ilerleme) → SHA-256 doğrula → kurulum izni
 * → sistem kurulum ekranı. Her hata, tarayıcıda release sayfasını açan yedeğe düşer (pencere [UpdateState.Failed]).
 *
 * İndirme [scope] içinde sürer (ekran döndürme/yeniden oluşturma onu kesmez). Kurulum ekranı yalnızca arayüz
 * ön plandayken [installIfPossible] ile açılır; arka plandan etkinlik başlatmak Android'de engellenir.
 */
class UpdateFlow(
    private val checker: UpdateChecker,
    private val downloader: UpdateDownloader,
    private val platform: InstallPlatform,
    private val dir: File,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch {
            checker.available.collect { info ->
                if (info != null && _state.value is UpdateState.Idle) _state.value = UpdateState.Offer(info)
            }
        }
    }

    /** "Güncelle": APK varsa indirmeyi başlatır. APK yoksa (eşleşme yok) arayüz tarayıcıya yönlendirir, buraya gelmez. */
    fun start() {
        val info = when (val s = _state.value) {
            is UpdateState.Offer -> s.info
            is UpdateState.Failed -> s.info
            else -> return
        }
        val apk = info.apk ?: return
        _state.value = UpdateState.Downloading(info, 0, apk.size)
        job = scope.launch {
            try {
                val file = downloader.download(apk, dir) { done, total ->
                    _state.update { if (it is UpdateState.Downloading) UpdateState.Downloading(info, done, total) else it }
                }
                _state.value = if (platform.isOurPackage(file)) {
                    UpdateState.Ready(info, file)
                } else {
                    file.delete()
                    UpdateState.Failed(info, UpdateState.Reason.CHECKSUM)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(
                    info,
                    if (e.reason == UpdateException.Reason.CHECKSUM) UpdateState.Reason.CHECKSUM else UpdateState.Reason.DOWNLOAD,
                )
            } catch (_: Exception) {
                _state.value = UpdateState.Failed(info, UpdateState.Reason.DOWNLOAD)
            }
        }
    }

    /**
     * Ön plandayken çağrılır (indirme bitince ve ayar sayfasından dönünce). Dosya hazırsa ve izin varsa kurulum
     * ekranını açar; izin yoksa [UpdateState.NeedsPermission]'a geçer.
     */
    fun installIfPossible() {
        val (info, file) = when (val s = _state.value) {
            is UpdateState.Ready -> s.info to s.file
            is UpdateState.NeedsPermission -> s.info to s.file
            else -> return
        }
        when {
            !platform.canInstall() -> _state.value = UpdateState.NeedsPermission(info, file)
            platform.openInstaller(file) -> dismiss() // sistem ekranı devraldı; akış bitti
            else -> _state.value = UpdateState.Failed(info, UpdateState.Reason.INSTALL)
        }
    }

    /** İzin penceresindeki "Ayarları aç". Dönüşte [installIfPossible] kaldığı yerden sürdürür. */
    fun openPermissionSettings() {
        if (_state.value !is UpdateState.NeedsPermission) return
        if (!platform.openInstallPermissionSettings()) {
            val s = _state.value as UpdateState.NeedsPermission
            _state.value = UpdateState.Failed(s.info, UpdateState.Reason.INSTALL)
        }
    }

    /** Pencereyi kapatır; iniyorsa indirmeyi iptal eder. Bu oturumda aynı sürüm bir daha sorulmaz. */
    fun dismiss() {
        job?.cancel()
        job = null
        _state.value = UpdateState.Idle
        checker.dismiss()
    }
}
