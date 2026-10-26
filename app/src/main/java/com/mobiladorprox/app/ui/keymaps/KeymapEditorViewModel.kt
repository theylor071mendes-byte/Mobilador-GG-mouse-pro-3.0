package com.mobiladorprox.app.ui.keymaps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType
import com.mobiladorprox.app.data.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O editor trabalha sobre uma cópia local dos vínculos (carregada do banco
 * ao abrir o perfil). Arrastar/renomear/adicionar só muda essa cópia; nada
 * é persistido até o usuário tocar em "Salvar configuração" — exceto
 * "Restaurar padrão", que é uma ação destrutiva explícita e grava na hora.
 */
class KeymapEditorViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val profileId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: StateFlow<GameProfileEntity?> = profileId.filterNotNull()
        .flatMapLatest { id -> repository.observeProfile(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val profilesForGame: StateFlow<List<GameProfileEntity>> = profile
        .filterNotNull()
        .flatMapLatest { p -> repository.observeProfilesForPackage(p.packageName) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bindings = MutableStateFlow<List<KeyBindingEntity>>(emptyList())
    val bindings: StateFlow<List<KeyBindingEntity>> = _bindings.asStateFlow()

    private val _unsavedChanges = MutableStateFlow(false)
    val unsavedChanges: StateFlow<Boolean> = _unsavedChanges.asStateFlow()

    /** Carrega um perfil (chamado ao abrir a tela e ao trocar via "Carregar perfil"). */
    fun load(id: Long) {
        if (profileId.value == id) return
        profileId.value = id
        viewModelScope.launch {
            _bindings.value = repository.getBindingsOnce(id)
            _unsavedChanges.value = false
        }
    }

    fun updateBindingPosition(bindingId: Long, xPercent: Float, yPercent: Float) {
        _bindings.value = _bindings.value.map {
            if (it.id == bindingId) it.copy(xPercent = xPercent.coerceIn(0f, 1f), yPercent = yPercent.coerceIn(0f, 1f)) else it
        }
        _unsavedChanges.value = true
    }

    fun updateBindingKey(bindingId: Long, newKeyCode: Int) {
        _bindings.value = _bindings.value.map { if (it.id == bindingId) it.copy(keyCode = newKeyCode) else it }
        _unsavedChanges.value = true
    }

    fun renameBinding(bindingId: Long, newLabel: String) {
        _bindings.value = _bindings.value.map { if (it.id == bindingId) it.copy(label = newLabel) else it }
        _unsavedChanges.value = true
    }

    fun removeBinding(bindingId: Long) {
        _bindings.value = _bindings.value.filterNot { it.id == bindingId }
        _unsavedChanges.value = true
    }

    /** IDs negativos identificam bindings novos ainda não salvos no banco. */
    fun addBinding(label: String, keyCode: Int, gestureType: GestureType) {
        val tempId = -System.currentTimeMillis()
        _bindings.value = _bindings.value + KeyBindingEntity(
            id = tempId,
            profileId = profileId.value ?: 0,
            actionId = "custom_${System.currentTimeMillis()}",
            label = label,
            keyCode = keyCode,
            gestureType = gestureType.name,
            xPercent = 0.5f,
            yPercent = 0.5f
        )
        _unsavedChanges.value = true
    }

    fun saveConfiguration(onSaved: () -> Unit = {}) {
        val id = profileId.value ?: return
        viewModelScope.launch {
            repository.replaceBindings(id, _bindings.value)
            _bindings.value = repository.getBindingsOnce(id)
            _unsavedChanges.value = false
            onSaved()
        }
    }

    fun createNewProfile(name: String, onCreated: (Long) -> Unit) {
        val currentProfile = profile.value ?: return
        viewModelScope.launch {
            val id = repository.createProfile(name, currentProfile.packageName, currentProfile.gameLabel)
            onCreated(id)
        }
    }

    fun restoreDefaults() {
        val id = profileId.value ?: return
        viewModelScope.launch {
            repository.restoreDefaults(id)
            _bindings.value = repository.getBindingsOnce(id)
            _unsavedChanges.value = false
        }
    }
}
