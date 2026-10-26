package com.mobiladorprox.app.ui.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.model.PerformanceProfile
import com.mobiladorprox.app.service.MapperState
import com.mobiladorprox.app.settings.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PerformanceViewModel(private val preferences: AppPreferences) : ViewModel() {

    val selectedProfile: StateFlow<PerformanceProfile> = preferences.performanceProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceProfile.MEDIO)

    fun selectProfile(profile: PerformanceProfile) {
        viewModelScope.launch {
            preferences.setPerformanceProfile(profile)
            // Aplica imediatamente se o mapeador já estiver em execução.
            MapperState.lookResponsiveness = profile.lookResponsiveness
        }
    }
}
