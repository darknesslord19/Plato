package com.kaan.play.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.home.HomeCatalog
import com.kaan.play.data.home.HomeLayoutRules
import com.kaan.play.data.home.HomePreset
import com.kaan.play.data.home.HomeSection
import com.kaan.play.data.model.MediaType
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.dpadLeavesTextField
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberCursorField
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Ana sayfayı düzenle: bölümlerin sırası (yukarı/aşağı düğmeleri; sürükleme yok, kumandayla da çalışır),
 * göster/gizle anahtarı, hazır kategori ekleme ve kendi kategorini yazma (TMDB araması). Değişiklik anında kaydedilir.
 */
@Composable
fun HomeLayoutScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val store = container.homeLayout
    val scope = rememberCoroutineScope()
    val layout by store.layout.collectAsStateWithLifecycle(initialValue = null)
    var addPreset by remember { mutableStateOf(false) }
    var editCustom by remember { mutableStateOf<HomeSection?>(null) }
    var newCustom by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val focus = rememberFocusMemory(enabled = layout != null)

    fun update(transform: (List<HomeSection>) -> List<HomeSection>) {
        scope.launch { store.update(transform) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.home_edit),
            onBack = onBack,
            action = {
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.home_edit_reset)) }
            },
        )
        val sections = layout
        if (sections != null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { addPreset = true },
                            modifier = Modifier.weight(1f).focusMemory(focus, "add_preset", isDefault = true),
                        ) { Text(stringResource(R.string.home_edit_add_preset), maxLines = 2) }
                        OutlinedButton(
                            onClick = { newCustom = true },
                            modifier = Modifier.weight(1f).focusMemory(focus, "add_custom"),
                        ) { Text(stringResource(R.string.home_edit_add_custom), maxLines = 2) }
                    }
                }
                item(key = "hint") {
                    Text(
                        text = stringResource(R.string.home_edit_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(sections, key = { _, s -> s.id }) { index, section ->
                    SectionRow(
                        section = section,
                        canUp = index > 0,
                        canDown = index < sections.lastIndex,
                        onUp = { update { HomeLayoutRules.move(it, section.id, -1) } },
                        onDown = { update { HomeLayoutRules.move(it, section.id, +1) } },
                        onToggle = { v -> update { HomeLayoutRules.setVisible(it, section.id, v) } },
                        onEdit = if (section.isCustom) ({ editCustom = section }) else null,
                        onRemove = if (!section.isSpecial) ({ update { HomeLayoutRules.remove(it, section.id) } }) else null,
                        modifier = Modifier.focusMemory(focus, "s-${section.id}"),
                    )
                }
            }

            if (addPreset) {
                PresetDialog(
                    addable = HomeLayoutRules.addable(sections),
                    onPick = { id -> update { HomeLayoutRules.addPreset(it, id) } },
                    onDismiss = { addPreset = false },
                )
            }
        }
    }

    if (newCustom) {
        CustomCategoryDialog(
            initial = null,
            onSave = { name, query, type ->
                update { HomeLayoutRules.addCustom(it, name, query, type, UUID.randomUUID().toString().take(8)) }
                newCustom = false
            },
            onDismiss = { newCustom = false },
        )
    }
    editCustom?.let { section ->
        CustomCategoryDialog(
            initial = section,
            onSave = { name, query, type ->
                update { HomeLayoutRules.editCustom(it, section.id, name, query, type) }
                editCustom = null
            },
            onDismiss = { editCustom = null },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.home_edit_reset), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.home_edit_reset_message)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { store.reset() }; confirmReset = false }) {
                    Text(stringResource(R.string.home_edit_reset))
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun SectionRow(
    section: HomeSection,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(40.dp)) {
                    Icon(
                        PlayIcons.ChevronDown,
                        contentDescription = stringResource(R.string.home_edit_move_up),
                        modifier = Modifier.size(20.dp).rotate(180f),
                    )
                }
                IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(40.dp)) {
                    Icon(
                        PlayIcons.ChevronDown,
                        contentDescription = stringResource(R.string.home_edit_move_down),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .then(if (onEdit != null) Modifier.focusRing(RoundedCornerShape(10.dp)).selectable(false, onClick = onEdit) else Modifier),
            ) {
                Text(
                    text = section.title(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (section.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val kind = when {
                    section.isSpecial -> stringResource(R.string.home_edit_kind_special)
                    section.isCustom -> stringResource(R.string.home_edit_kind_custom, section.query.orEmpty())
                    else -> HomeCatalog.preset(section.id)?.group?.let { stringResource(it.titleRes()) }.orEmpty()
                }
                Text(
                    text = kind,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(PlayIcons.Trash, contentDescription = stringResource(R.string.home_edit_remove), modifier = Modifier.size(20.dp))
                }
            }
            // Satırın sağı: göster/gizle (anahtarın kendisi yalnızca gösterir, dokunulan/odaklanan alan sarmalayıcıdır).
            Row(
                modifier = modifier
                    .focusRing(RoundedCornerShape(20.dp))
                    .toggleable(value = section.visible, role = Role.Switch, onValueChange = onToggle)
                    .padding(4.dp),
            ) {
                Switch(
                    checked = section.visible,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

/** Hazır kategoriler gruplu; dokununca eklenir (pencere açık kalır, birden çok eklenebilir). */
@Composable
private fun PresetDialog(addable: List<HomePreset>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.home_edit_add_preset), style = MaterialTheme.typography.titleLarge) },
        text = {
            if (addable.isEmpty()) {
                Text(stringResource(R.string.home_edit_all_added), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    addable.groupBy { it.group }.forEach { (group, presets) ->
                        item(key = "g-$group") {
                            Text(
                                text = stringResource(group.titleRes()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(presets, key = { it.id }) { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRing(shape)
                                    .selectable(selected = false, onClick = { onPick(preset.id) })
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    PlayIcons.Plus,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(homeSectionTitleRes(preset.id)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_edit_done)) } },
    )
}

/** Kendi kategorin: ad + TMDB'de aranacak kelime + tür (hepsi / film / dizi). */
@Composable
private fun CustomCategoryDialog(
    initial: HomeSection?,
    onSave: (name: String, query: String, type: MediaType?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var query by remember { mutableStateOf(initial?.query.orEmpty()) }
    var type by remember { mutableStateOf(MediaType.entries.firstOrNull { it.tmdbPath == initial?.type }) }
    val nameField = rememberCursorField(name) { name = it.take(HomeLayoutRules.MAX_NAME) }
    val queryField = rememberCursorField(query) { query = it.take(HomeLayoutRules.MAX_QUERY) }
    val chipShape = RoundedCornerShape(20.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringResource(if (initial == null) R.string.home_edit_add_custom else R.string.home_edit_edit_custom),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameField.value,
                    onValueChange = nameField.onValueChange,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.home_custom_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(nameField),
                )
                OutlinedTextField(
                    value = queryField.value,
                    onValueChange = queryField.onValueChange,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.home_custom_query)) },
                    supportingText = { Text(stringResource(R.string.home_custom_query_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().dpadLeavesTextField(queryField),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, MediaType.Movie, MediaType.Tv).forEach { option ->
                        val selected = option == type
                        Text(
                            text = stringResource(
                                when (option) {
                                    null -> R.string.home_custom_type_all
                                    MediaType.Movie -> R.string.home_custom_type_movie
                                    MediaType.Tv -> R.string.home_custom_type_series
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .focusRing(chipShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    chipShape,
                                )
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { type = option })
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, query, type) },
                enabled = name.isNotBlank() && query.isNotBlank(),
            ) { Text(stringResource(R.string.cs_domain_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
