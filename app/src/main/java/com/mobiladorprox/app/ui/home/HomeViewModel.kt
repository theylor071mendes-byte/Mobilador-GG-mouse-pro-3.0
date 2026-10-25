package com.mobiladorprox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.repository.ProfileRepository
import com.mobiladorprox.app.settings.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ProfileRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    val profiles: StateFlow<List<GameProfileEntity>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Perfil atualmente selecionado para iniciar o mapeador (o último usado, ou o primeiro da lista). */
    val activeProfile: StateFlow<GameProfileEntity?> = combine(
        profiles,
        preferences.lastProfileId
    ) { list, lastId ->
        list.firstOrNull { it.id == lastId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectProfile(profileId: Long) {
        viewModelScope.launch { preferences.setLastProfileId(profileId) }
    }
}
