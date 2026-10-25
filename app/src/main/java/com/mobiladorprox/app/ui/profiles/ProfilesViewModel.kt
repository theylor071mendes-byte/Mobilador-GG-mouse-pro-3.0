package com.mobiladorprox.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.model.GameType
import com.mobiladorprox.app.data.repository.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(private val repository: ProfileRepository) : ViewModel() {

    val profiles: StateFlow<List<GameProfileEntity>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProfile(name: String, gameType: GameType, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createProfile(name, gameType)
            onCreated(id)
        }
    }

    fun duplicateProfile(profileId: Long, newName: String) {
        viewModelScope.launch { repository.duplicateProfile(profileId, newName) }
    }

    fun renameProfile(profileId: Long, newName: String) {
        viewModelScope.launch { repository.renameProfile(profileId, newName) }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch { repository.deleteProfile(profileId) }
    }

    fun restoreDefaults(profileId: Long) {
        viewModelScope.launch { repository.restoreDefaults(profileId) }
    }
}
