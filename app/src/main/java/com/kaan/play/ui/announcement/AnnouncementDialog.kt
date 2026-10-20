package com.kaan.play.ui.announcement

import android.content.Intent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.announcement.AnnouncementChecker
import com.kaan.play.data.announcement.AnnouncementRules
import com.kaan.play.data.update.UpdateFlow
import com.kaan.play.data.update.UpdateState
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.launch

/**
 * Uygulama içi duyuru penceresi.
 * - Play temasıyla ve PlayButtons düğmeleriyle çizilir.
 * - D-pad ile odaklanabilir (ilk odak "Tamam" düğmesindedir).
 * - Geri tuşu = Tamam (pencereyi kapatır ve duyuruyu görüldü olarak saklar).
 * - Güncelleme penceresi açıksa duyuru gösterilmez.
 */
@Composable
fun AnnouncementDialog(
    checker: AnnouncementChecker,
    updateFlow: UpdateFlow? = null,
) {
    val announcement by checker.available.collectAsStateWithLifecycle()
    val updateState = updateFlow?.state?.collectAsStateWithLifecycle()?.value

    // Güncelleme penceresi aktifse duyuru gösterilmez
    if (updateState != null && updateState !is UpdateState.Idle) return

    val item = announcement ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = rememberInitialFocus()

    AlertDialog(
        onDismissRequest = {
            scope.launch { checker.dismiss(item) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch { checker.dismiss(item) }
                },
                modifier = Modifier.focusRequester(focus),
            ) {
                Text(stringResource(R.string.announcement_ok))
            }
        },
        dismissButton = if (!item.link.isNullOrBlank() && AnnouncementRules.isSafeLink(item.link)) {
            {
                OutlinedButton(
                    onClick = {
                        val link = item.link
                        scope.launch { checker.dismiss(item) }
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, link.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text(stringResource(R.string.announcement_open))
                }
            }
        } else null,
    )
}
