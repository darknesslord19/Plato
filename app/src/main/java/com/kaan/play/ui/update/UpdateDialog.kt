package com.kaan.play.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.update.UpdateFlow
import com.kaan.play.data.update.UpdateInfo
import com.kaan.play.data.update.UpdateState
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.tv.rememberInitialFocus

/**
 * Güncelleme penceresi ([UpdateFlow] durumuna göre): öneri → indirme (ilerleme çubuğu) → [gerekirse izin] → sistem
 * kurulum ekranı. Cihaza uyan APK yoksa ya da herhangi bir adım başarısız olursa "Tarayıcıda aç" yedeği çıkar.
 * Kumandada odak her pencerede ana düğmede başlar.
 */
@Composable
fun UpdateDialog(flow: UpdateFlow) {
    val state by flow.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Kurulum ekranı yalnızca ön plandayken açılır (arka plandan etkinlik başlatmak engellenir): indirme bitince ve
    // ayar sayfasından dönünce (ON_RESUME) kaldığı yerden sürer.
    LaunchedEffect(state) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) flow.installIfPossible()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { flow.installIfPossible() }

    when (val s = state) {
        UpdateState.Idle -> Unit
        is UpdateState.Offer -> OfferDialog(s.info, flow, context)
        is UpdateState.Downloading -> DownloadingDialog(s, flow)
        is UpdateState.Ready -> Unit // kurulum ekranı açılıyor
        is UpdateState.NeedsPermission -> PermissionDialog(flow)
        is UpdateState.Failed -> FailedDialog(s, flow, context)
    }
}

@Composable
private fun OfferDialog(info: UpdateInfo, flow: UpdateFlow, context: Context) {
    val focus = rememberInitialFocus()
    val inApp = info.apk != null
    AlertDialog(
        onDismissRequest = flow::dismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(if (inApp) R.string.update_message else R.string.update_message_web, info.tag)) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (inApp) {
                        flow.start()
                    } else {
                        openReleasePage(context, info)
                        flow.dismiss()
                    }
                },
                modifier = Modifier.focusRequester(focus),
            ) { Text(stringResource(if (inApp) R.string.update_install else R.string.update_download)) }
        },
        dismissButton = { TextButton(onClick = flow::dismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

@Composable
private fun DownloadingDialog(state: UpdateState.Downloading, flow: UpdateFlow) {
    val focus = rememberInitialFocus()
    val fraction = if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else null
    AlertDialog(
        onDismissRequest = flow::dismiss,
        // Yanlışlıkla dışarı dokunmak indirmeyi iptal etmesin; Geri tuşu ve "İptal" iptal eder.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.update_downloading_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.update_progress, (fraction * 100).toInt()),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = flow::dismiss, modifier = Modifier.focusRequester(focus)) {
                Text(stringResource(R.string.update_cancel))
            }
        },
    )
}

@Composable
private fun PermissionDialog(flow: UpdateFlow) {
    val focus = rememberInitialFocus()
    AlertDialog(
        onDismissRequest = flow::dismiss,
        title = { Text(stringResource(R.string.update_permission_title)) },
        text = { Text(stringResource(R.string.update_permission_message)) },
        confirmButton = {
            TextButton(onClick = flow::openPermissionSettings, modifier = Modifier.focusRequester(focus)) {
                Text(stringResource(R.string.update_open_settings))
            }
        },
        dismissButton = { TextButton(onClick = flow::dismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

@Composable
private fun FailedDialog(state: UpdateState.Failed, flow: UpdateFlow, context: Context) {
    val focus = rememberInitialFocus()
    val message = when (state.reason) {
        UpdateState.Reason.DOWNLOAD -> R.string.update_failed_download
        UpdateState.Reason.CHECKSUM -> R.string.update_failed_checksum
        UpdateState.Reason.INSTALL -> R.string.update_failed_install
    }
    AlertDialog(
        onDismissRequest = flow::dismiss,
        title = { Text(stringResource(R.string.update_failed_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    openReleasePage(context, state.info)
                    flow.dismiss()
                },
                modifier = Modifier.focusRequester(focus),
            ) { Text(stringResource(R.string.update_open_browser)) }
        },
        dismissButton = { TextButton(onClick = flow::dismiss) { Text(stringResource(R.string.update_close)) } },
    )
}

/** Tarayıcısı olmayan cihazda (bazı TV'ler) sessizce geçer. */
private fun openReleasePage(context: Context, info: UpdateInfo) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, info.pageUrl.toUri()))
    } catch (_: ActivityNotFoundException) {
    }
}
