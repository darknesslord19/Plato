package com.kaan.play.ui.addons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.kaan.play.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.kaan.play.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.R
import com.kaan.play.data.web.WebSite
import com.kaan.play.data.web.WebSiteException
import com.kaan.play.data.web.WebSiteRepository
import com.kaan.play.data.web.WebSiteUrl
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.dpadLeavesTextField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Web sitesi ekle" penceresinin durumu; pencere kapalıyken ViewModel'de null. */
data class AddWebSiteState(
    val name: String = "",
    val address: String = "",
    val error: WebSiteException.Kind? = null,
)

class WebSitesViewModel(private val repo: WebSiteRepository) : ViewModel() {

    /** null = henüz yüklenmedi. */
    val sites: StateFlow<List<WebSite>?> = repo.sites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _dialog = MutableStateFlow<AddWebSiteState?>(null)
    val dialog: StateFlow<AddWebSiteState?> = _dialog.asStateFlow()

    fun openAdd() { _dialog.value = AddWebSiteState() }

    fun dismissAdd() { _dialog.value = null }

    fun onNameChange(text: String) = _dialog.update { it?.copy(name = text, error = null) }

    fun onAddressChange(text: String) = _dialog.update { it?.copy(address = text, error = null) }

    fun submit() {
        val current = _dialog.value ?: return
        if (current.address.isBlank()) return
        viewModelScope.launch {
            try {
                repo.add(current.name, current.address)
                _dialog.value = null
            } catch (e: WebSiteException) {
                _dialog.update { it?.copy(error = e.kind) }
            }
        }
    }

    fun remove(site: WebSite) {
        viewModelScope.launch { repo.remove(site) }
    }
}

/** Eklenmiş bir site: dokununca açılır; sağdaki düğme siler. */
@Composable
fun WebSiteRow(site: WebSite, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val host = remember(site.url) { WebSiteUrl.displayHost(site.url) }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(site.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                PlayIcons.Trash,
                contentDescription = stringResource(R.string.web_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AddWebSiteDialog(
    state: AddWebSiteState,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.web_add_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(),
                )
                OutlinedTextField(
                    value = state.address,
                    onValueChange = onAddressChange,
                    label = { Text(stringResource(R.string.web_add_address)) },
                    placeholder = { Text(stringResource(R.string.web_add_hint)) },
                    singleLine = true,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(),
                )
                state.error?.let { kind ->
                    Text(
                        text = stringResource(
                            when (kind) {
                                WebSiteException.Kind.InvalidUrl -> R.string.web_error_invalid_url
                                WebSiteException.Kind.AlreadyAdded -> R.string.web_error_already_added
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.address.isNotBlank()) {
                Text(stringResource(R.string.addons_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
