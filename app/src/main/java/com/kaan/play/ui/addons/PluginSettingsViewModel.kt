package com.kaan.play.ui.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.plugin.InstalledPlugin
import com.kaan.play.data.plugin.PluginRepository
import com.kaan.play.data.plugin.PluginSettingsRepository
import com.kaan.play.data.plugin.runtime.PluginRuntime
import com.kaan.play.data.plugin.runtime.PluginSettingField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Eklentinin `onSettings()` düzeninin yüklenme durumu. */
sealed interface PluginLayoutState {
    data object Loading : PluginLayoutState
    data object Failed : PluginLayoutState
    data class Ready(val fields: List<PluginSettingField>) : PluginLayoutState
}

/** Eklenti ayrıntı sayfası: aç/kapat, ayar düzenini (eklentinin kendi kodundan) yükle, değerleri kaydet. */
class PluginSettingsViewModel(
    private val plugins: PluginRepository,
    private val runtime: PluginRuntime,
    private val settings: PluginSettingsRepository,
    private val pluginId: Long,
) : ViewModel() {

    val plugin: StateFlow<InstalledPlugin?> =
        plugins.plugins.map { list -> list.firstOrNull { it.id == pluginId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val values: StateFlow<Map<String, JsonElement>> =
        settings.observe(pluginId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _layout = MutableStateFlow<PluginLayoutState>(PluginLayoutState.Loading)
    val layout: StateFlow<PluginLayoutState> = _layout.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _layout.value = PluginLayoutState.Loading
        viewModelScope.launch {
            val code = plugins.loadCode(pluginId)
            val fields = code?.let { runtime.settingsLayout(it, "settings-$pluginId") }
            _layout.value = if (fields == null) PluginLayoutState.Failed else PluginLayoutState.Ready(fields)
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { plugins.setPluginEnabled(pluginId, enabled) }
    }

    /** [value] null: değer silinir (eklentinin varsayılanına döner). */
    fun setValue(key: String, value: JsonElement?) {
        viewModelScope.launch { settings.set(pluginId, key, value) }
    }
}
