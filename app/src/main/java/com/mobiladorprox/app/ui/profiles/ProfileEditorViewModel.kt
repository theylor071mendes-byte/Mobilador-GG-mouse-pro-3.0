package com.mobiladorprox.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType
import com.mobiladorprox.app.data.model.PresetMode
import com.mobiladorprox.app.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileEditorViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val profileId = MutableStateFlow<Long?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val profile: StateFlow<GameProfileEntity?> = profileId.filterNotNull()
        .flatMapLatest { id -> repository.observeProfile(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bindings: StateFlow<List<KeyBindingEntity>> = profileId.filterNotNull()
        .flatMapLatest { id -> repository.observeBindings(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(id: Long) {
        if (profileId.value != id) profileId.value = id
    }

    fun renameProfile(newName: String) {
        val id = profileId.value ?: return
        viewModelScope.launch { repository.renameProfile(id, newName) }
    }

    fun applyPreset(preset: PresetMode) {
        val id = profileId.value ?: return
        viewModelScope.launch { repository.applyPreset(id, preset) }
    }

    /**
     * Ajuste manual de um ou mais sliders. Sempre marca o perfil como
     * "Personalizado", já que deixou de corresponder exatamente a uma
     * predefinição nomeada.
     */
    fun updateSettings(
        sensitivityHorizontal: Float,
        sensitivityVertical: Float,
        cursorSpeed: Float,
        dpiMultiplier: Float
    ) {
        val id = profileId.value ?: return
        viewModelScope.launch {
            repository.updateProfileSettings(
                id, sensitivityHorizontal, sensitivityVertical, cursorSpeed, dpiMultiplier, PresetMode.PERSONALIZADO
            )
        }
    }

    fun restoreDefaults() {
        val id = profileId.value ?: return
        viewModelScope.launch { repository.restoreDefaults(id) }
    }

    fun updateBindingKey(binding: KeyBindingEntity, newKeyCode: Int) {
        viewModelScope.launch { repository.updateBinding(binding.copy(keyCode = newKeyCode)) }
    }

    fun addCustomBinding(label: String, keyCode: Int, gestureType: GestureType) {
        val id = profileId.value ?: return
        val actionId = "custom_${System.currentTimeMillis()}"
        viewModelScope.launch {
            repository.addBinding(id, actionId, label, keyCode, gestureType, 0.5f, 0.5f)
        }
    }
}
