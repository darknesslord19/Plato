package com.kaan.play.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.tmdb.TmdbKeyRules
import com.kaan.play.data.tmdb.TmdbKeyStore
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.rememberCursorField
import kotlinx.coroutines.launch

/** Ayarlar > Eklentiler satırının alt yazısı: anahtarın nereden geldiği (anahtarın kendisi gösterilmez). */
@Composable
fun tmdbKeySummary(): String {
    val store = rememberContainer().tmdbKeys
    val user by store.userKey.collectAsState()
    return when (store.source(user)) {
        TmdbKeyStore.Source.User -> stringResource(R.string.tmdb_key_source_user, TmdbKeyRules.mask(user!!))
        TmdbKeyStore.Source.BuiltIn -> stringResource(R.string.tmdb_key_source_builtin)
        TmdbKeyStore.Source.None -> stringResource(R.string.tmdb_key_source_none)
    }
}

/**
 * TMDB API anahtarı girme penceresi. Kaydedilen anahtar derlemedeki anahtarın yerine geçer ve hemen kullanılır
 * (ana sayfa, arama, detay ve Nuvio eklentilerinin TMDB istekleri). [onSaved] başarıyla kaydedince çağrılır.
 */
@Composable
fun TmdbKeyDialog(onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    val store = rememberContainer().tmdbKeys
    val user by store.userKey.collectAsState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val field = rememberCursorField(text) { text = it; invalid = false }
    val save = {
        scope.launch {
            if (store.set(text)) {
                onSaved()
                onDismiss()
            } else {
                invalid = true
            }
        }
        Unit
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.tmdb_key_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.tmdb_key_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tmdbKeySummary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.tmdb_key_label)) },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(field),
                )
                if (invalid) {
                    Text(
                        text = stringResource(R.string.tmdb_key_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (user != null) {
                    TextButton(onClick = {
                        scope.launch {
                            store.set(null)
                            onSaved()
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.tmdb_key_remove)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.cs_domain_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
