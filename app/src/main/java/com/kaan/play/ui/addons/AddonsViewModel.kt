package com.kaan.play.ui.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Eklenti ekle" penceresinin durumu. Pencere kapalıyken ViewModel'de bu değer null'dır. */
data class AddDialogState(
    val input: String = "",
    val busy: Boolean = false,
    val error: AddonException? = null,
)

class AddonsViewModel(
    private val repo: AddonRepository,
    private val pendingUrl: MutableStateFlow<String?>,
) : ViewModel() {

    /** null = henüz yüklenmedi (boş liste mesajı yanıp sönmesin). */
    val addons: StateFlow<List<Addon>?> =
        repo.addons.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _dialog = MutableStateFlow<AddDialogState?>(null)
    val dialog: StateFlow<AddDialogState?> = _dialog.asStateFlow()

    private var submitJob: Job? = null

    init {
        // Dışarıdan gelen https manifest bağlantısı: pencereyi adres dolu açar, kutuyu boşaltır.
        viewModelScope.launch {
            pendingUrl.filterNotNull().collect { url ->
                pendingUrl.value = null
                openAdd(url)
            }
        }
    }

    private var startHandled = false

    /** "Eklenti ekle" düğmesiyle gelinen açılışta pencereyi bir kez açar (yeniden oluşturmada tekrarlamaz). */
    fun startAddingOnce() {
        if (startHandled) return
        startHandled = true
        openAdd()
    }

    fun openAdd(prefill: String = "") {
        submitJob?.cancel()
        _dialog.value = AddDialogState(input = prefill)
    }

    fun dismissAdd() {
        submitJob?.cancel()
        _dialog.value = null
    }

    fun onInputChange(text: String) {
        _dialog.update { it?.copy(input = text, error = null) }
    }

    fun submit() {
        val current = _dialog.value ?: return
        if (current.busy || current.input.isBlank()) return
        _dialog.value = current.copy(busy = true, error = null)
        submitJob = viewModelScope.launch {
            try {
                repo.add(current.input)
                _dialog.value = null
            } catch (e: AddonException) {
                _dialog.update { it?.copy(busy = false, error = e) }
            }
        }
    }

    fun setEnabled(addon: Addon, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(addon.id, enabled) }
    }

    fun remove(addon: Addon) {
        viewModelScope.launch { repo.remove(addon.id) }
    }
}
