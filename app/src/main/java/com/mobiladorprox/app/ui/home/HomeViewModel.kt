package com.mobiladorprox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.repository.ProfileRepository
import com.mobiladorprox.app.settings.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SelectedGame(val packageName: String, val label: String, val profileId: Long)

class HomeViewModel(
    private val repository: ProfileRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    val selectedGame: StateFlow<SelectedGame?> = combine(
        preferences.selectedPackageName,
        preferences.selectedGameLabel,
        preferences.selectedProfileId
    ) { pkg, label, profileId ->
        if (pkg != null && label != null && profileId != null) SelectedGame(pkg, label, profileId) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Chamado ao escolher um jogo em "+ Adicionar jogo": garante um perfil e o marca como selecionado. */
    fun selectGame(packageName: String, label: String, onReady: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val profile = repository.getOrCreateProfileForGame(packageName, label)
            preferences.setSelectedGame(packageName, label, profile.id)
            onReady(profile.id)
        }
    }
}
