package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.plugin.runtime.PluginSettingField
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.SettingsRow
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberCursorField
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Bir eklentinin küçük ayrıntı sayfası: aç/kapat anahtarı ve eklentinin kendi `onSettings()` düzeninden (header/info/toggle/
 * select/text) çizilen sade form. Değerler anında eklenti başına saklanır ve çalışırken `SCRAPER_SETTINGS` olarak geçer.
 */
@Composable
fun PluginSettingsScreen(pluginId: Long, onBack: () -> Unit) {
    val vm = containerViewModel(key = "plugin-$pluginId") {
        PluginSettingsViewModel(it.plugins, it.pluginRuntime, it.pluginSettings, pluginId)
    }
    val plugin by vm.plugin.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val values by vm.values.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = plugin?.name.orEmpty(), onBack = onBack)
        val current = plugin ?: return@Column

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "enabled") {
                val label = stringResource(R.string.addons_enabled_toggle, current.name)
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.plugin_enabled), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = current.enabled,
                            onCheckedChange = vm::setEnabled,
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.focusRing(CircleShape).semantics { contentDescription = label },
                        )
                    }
                }
            }
            when (val state = layout) {
                PluginLayoutState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
                PluginLayoutState.Failed -> item(key = "failed") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.plugin_settings_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = vm::reload) { Text(stringResource(R.string.common_retry)) }
                    }
                }
                is PluginLayoutState.Ready -> items(state.fields.size, key = { "f-$it" }) { index ->
                    FieldRow(state.fields[index], values, onChange = vm::setValue)
                }
            }
        }
    }
}

@Composable
private fun FieldRow(field: PluginSettingField, values: Map<String, JsonElement>, onChange: (String, JsonElement?) -> Unit) {
    when (field) {
        is PluginSettingField.Header -> Text(
            text = field.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
        )
        is PluginSettingField.Info -> Muted(field.label)
        is PluginSettingField.Toggle -> {
            val checked = (values[field.key] as? JsonPrimitive)?.booleanOrNull ?: field.default
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(field.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = checked,
                            onCheckedChange = { onChange(field.key, JsonPrimitive(it)) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.focusRing(CircleShape).semantics { contentDescription = field.label },
                        )
                    }
                    field.description?.let { Muted(it, horizontal = 0.dp) }
                }
            }
        }
        is PluginSettingField.Select -> {
            var open by remember { mutableStateOf(false) }
            val current = (values[field.key] as? JsonPrimitive)?.contentOrNull ?: field.default
            val selected = field.options.firstOrNull { it.value == current } ?: field.options.first()
            SettingsRow(title = field.label, value = selected.label, subtitle = field.description.orEmpty(), onClick = { open = true })
            if (open) {
                ChoiceDialog(
                    title = field.label,
                    options = field.options,
                    selected = selected,
                    label = { it.label },
                    onSelect = { open = false; onChange(field.key, JsonPrimitive(it.value)) },
                    onDismiss = { open = false },
                )
            }
        }
        is PluginSettingField.Text -> {
            val stored = (values[field.key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val cursor = rememberCursorField(stored) { onChange(field.key, it.takeIf(String::isNotEmpty)?.let(::JsonPrimitive)) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = cursor.value,
                    onValueChange = cursor.onValueChange,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    label = field.label?.let { { Text(it) } },
                    placeholder = { Text(field.placeholder) },
                    singleLine = true,
                    visualTransformation = if (field.isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.isPassword) KeyboardType.Password else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                        showKeyboardOnFocus = false,
                    ),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(cursor),
                )
                field.description?.let { Muted(it, horizontal = 0.dp) }
            }
        }
    }
}

@Composable
private fun Muted(text: String, horizontal: androidx.compose.ui.unit.Dp = 20.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 4.dp),
    )
}
