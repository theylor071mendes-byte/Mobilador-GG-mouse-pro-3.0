package com.mobiladorprox.app.ui.optimization

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Apenas controla quando a tela deve reconsultar as informações reais do
 * aparelho (RAM, Bluetooth, USB, etc.). Os dados em si são lidos
 * diretamente do Android no momento da composição, via DeviceInfoProvider.
 */
class OptimizationViewModel : ViewModel() {
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    fun refresh() {
        _refreshTick.value += 1
    }
}
