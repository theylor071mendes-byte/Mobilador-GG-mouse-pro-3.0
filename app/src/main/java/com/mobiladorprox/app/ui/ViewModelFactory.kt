package com.mobiladorprox.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobiladorprox.app.data.repository.ProfileRepository
import com.mobiladorprox.app.settings.AppPreferences
import com.mobiladorprox.app.ui.home.HomeViewModel
import com.mobiladorprox.app.ui.keymaps.KeymapEditorViewModel
import com.mobiladorprox.app.ui.mouse.MouseSettingsViewModel
import com.mobiladorprox.app.ui.optimization.OptimizationViewModel
import com.mobiladorprox.app.ui.performance.PerformanceViewModel

/**
 * Fábrica manual de ViewModels (sem framework de injeção de dependências),
 * para manter o projeto simples, previsível e fácil de compilar.
 */
class ViewModelFactory(
    private val repository: ProfileRepository,
    private val preferences: AppPreferences
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository, preferences) as T

            modelClass.isAssignableFrom(KeymapEditorViewModel::class.java) ->
                KeymapEditorViewModel(repository) as T

            modelClass.isAssignableFrom(PerformanceViewModel::class.java) ->
                PerformanceViewModel(preferences) as T

            modelClass.isAssignableFrom(MouseSettingsViewModel::class.java) ->
                MouseSettingsViewModel(preferences) as T

            modelClass.isAssignableFrom(OptimizationViewModel::class.java) ->
                OptimizationViewModel() as T

            else -> throw IllegalArgumentException("ViewModel desconhecido: ${modelClass.name}")
        }
    }
}
