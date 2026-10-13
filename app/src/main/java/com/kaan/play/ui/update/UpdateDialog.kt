package com.kaan.play.ui.update

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.update.UpdateChecker
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.tv.rememberInitialFocus

/**
 * Yeni sürüm varsa tek seferlik küçük pencere: "İndir" release sayfasını tarayıcıda açar, "Sonra" kapatır.
 * İkisi de aynı oturumda bir daha göstermez ([UpdateChecker.dismiss]). Kumandada odak "İndir"de başlar.
 */
@Composable
fun UpdateDialog(checker: UpdateChecker) {
    val info by checker.available.collectAsStateWithLifecycle()
    val update = info ?: return
    val context = LocalContext.current
    val focus = rememberInitialFocus()

    AlertDialog(
        onDismissRequest = checker::dismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_message, update.tag)) },
        confirmButton = {
            TextButton(
                onClick = {
                    // Tarayıcısı olmayan cihazda (bazı TV'ler) sessizce geç; pencere yine kapanır.
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, update.pageUrl.toUri()))
                    } catch (_: ActivityNotFoundException) {
                    }
                    checker.dismiss()
                },
                modifier = Modifier.focusRequester(focus),
            ) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = { TextButton(onClick = checker::dismiss) { Text(stringResource(R.string.update_later)) } },
    )
}
