package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.plugin.AddResult
import com.kaan.play.data.plugin.DeepLinkEvent
import com.kaan.play.data.plugin.DeepLinkHandler
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.flow.Flow

/**
 * `play://add` bağlantısı: önce onay penceresi (adres görünür, "Ekle" / "Vazgeç"), sonra sonucu
 * (eklendi / hata / geçersiz) alttan kısa bir Snackbar ile gösterir.
 */
@Composable
fun DeepLinkHost(handler: DeepLinkHandler) {
    val pending by handler.pending.collectAsStateWithLifecycle()
    pending?.let { url ->
        val focus = rememberInitialFocus()
        AlertDialog(
            onDismissRequest = handler::dismiss,
            title = { Text(stringResource(R.string.deeplink_confirm_title)) },
            text = { Text(stringResource(R.string.deeplink_confirm_message, url), maxLines = 8, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = handler::confirm, modifier = Modifier.focusRequester(focus)) {
                    Text(stringResource(R.string.deeplink_confirm_add))
                }
            },
            dismissButton = { TextButton(onClick = handler::dismiss) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    DeepLinkSnackbar(handler.events)
}

@Composable
private fun DeepLinkSnackbar(events: Flow<DeepLinkEvent>) {
    val host = remember { SnackbarHostState() }
    var current by remember { mutableStateOf<DeepLinkEvent?>(null) }
    LaunchedEffect(events) { events.collect { current = it } }

    // Metin kompozisyonda çözülür (hata iletileri @Composable); sonra Snackbar gösterilir.
    val text = when (val e = current) {
        null -> null
        is DeepLinkEvent.Added -> when (e.result) {
            is AddResult.Addon -> stringResource(R.string.deeplink_plugin_added)
            is AddResult.PluginRepo, is AddResult.CsRepo -> stringResource(R.string.deeplink_repo_added)
        }
        is DeepLinkEvent.Failed -> e.error.userMessage()
        DeepLinkEvent.Invalid -> stringResource(R.string.deeplink_invalid)
    }
    LaunchedEffect(current, text) {
        if (text != null) {
            host.showSnackbar(text)
            current = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = host,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
        )
    }
}
