package com.mobiladorprox.app.ui.mouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.model.PollingRate
import com.mobiladorprox.app.service.MapperState
import com.mobiladorprox.app.settings.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MouseSettingsViewModel(private val preferences: AppPreferences) : ViewModel() {

    val sensitivity: StateFlow<Float> = preferences.mouseSensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.DEFAULT_SENSITIVITY)

    val pollingRate: StateFlow<PollingRate> = preferences.pollingRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PollingRate.UNLIMITED)

    /** Ajuste real: multiplica o delta do ponteiro capturado antes de virar gesto de câmera. */
    fun setSensitivity(value: Float) {
        val clamped = value.coerceIn(1f, 20f)
        viewModelScope.launch {
            preferences.setMouseSensitivity(clamped)
            MapperState.mouseSensitivity = clamped
        }
    }

    /**
     * O Android não expõe API pública para alterar a taxa de resposta real de um
     * mouse USB/Bluetooth. Guardamos a preferência apenas como referência do usuário;
     * nenhum comportamento é simulado como se a taxa tivesse mudado de fato.
     */
    fun setPollingRate(rate: PollingRate) {
        viewModelScope.launch { preferences.setPollingRate(rate) }
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            preferences.restoreMouseDefaults()
            MapperState.mouseSensitivity = AppPreferences.DEFAULT_SENSITIVITY
        }
    }
}
