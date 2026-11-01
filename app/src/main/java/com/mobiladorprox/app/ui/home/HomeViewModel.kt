package com.mobiladorprox.app.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.repository.ProfileRepository
import com.mobiladorprox.app.service.MapperAccessibilityService
import com.mobiladorprox.app.service.OverlayService
import com.mobiladorprox.app.settings.AppPreferences
import com.mobiladorprox.app.util.DeviceInfoProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado completo da tela Home para a lista de jogos. */
data class HomeUiState(
    val profiles: List<GameProfileEntity> = emptyList(),
    val activeProfileId: Long? = null
)

/** Estado do jogo selecionado — mantido para compatibilidade com MenuScreen/GameLauncher. */
data class SelectedGame(val packageName: String, val label: String, val profileId: Long)

class HomeViewModel(
    private val repository: ProfileRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    /** Lista de todos os perfis salvos + qual está ativo (selecionado). */
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeProfiles(),
        preferences.selectedProfileId
    ) { profiles, activeId ->
        HomeUiState(profiles = profiles, activeProfileId = activeId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /**
     * Compatibilidade retroativa: usado por MenuScreen e GameLauncher.
     * Retorna o jogo correspondente ao perfil ativo.
     */
    val selectedGame: StateFlow<SelectedGame?> = combine(
        preferences.selectedPackageName,
        preferences.selectedGameLabel,
        preferences.selectedProfileId
    ) { pkg, label, profileId ->
        if (pkg != null && label != null && profileId != null) SelectedGame(pkg, label, profileId) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Chamado ao escolher um jogo em "+ Adicionar jogo": garante um perfil e o
     * marca como selecionado.
     */
    fun selectGame(packageName: String, label: String, onReady: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val profile = repository.getOrCreateProfileForGame(packageName, label)
            preferences.setSelectedGame(packageName, label, profile.id)
            onReady(profile.id)
        }
    }

    /** Define o perfil ativo sem alterar os demais dados. */
    fun setActiveProfile(profile: GameProfileEntity) {
        viewModelScope.launch {
            preferences.setSelectedGame(profile.packageName, profile.gameLabel, profile.id)
        }
    }

    /** Remove um perfil salvo. */
    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            // Se era o ativo, limpa a seleção
            if (preferences.selectedProfileId.stateIn(viewModelScope).value == profileId) {
                // Obtém o próximo perfil disponível ou deixa sem seleção
                val remaining = repository.observeProfiles()
                    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
                val next = remaining.firstOrNull { it.id != profileId }
                if (next != null) {
                    preferences.setSelectedGame(next.packageName, next.gameLabel, next.id)
                }
            }
        }
    }

    /** Verifica se as permissões estão prontas e inicia o jogo, caso estejam. */
    fun launchGame(
        context: Context,
        profile: GameProfileEntity,
        onNeedAccessibility: () -> Unit,
        onNeedOverlay: () -> Unit
    ) {
        val accessibilityEnabled = DeviceInfoProvider.isAccessibilityServiceEnabled(
            context, MapperAccessibilityService::class.java.name
        )
        val overlayGranted = DeviceInfoProvider.canDrawOverlays(context)

        when {
            !accessibilityEnabled -> onNeedAccessibility()
            !overlayGranted -> onNeedOverlay()
            else -> {
                // Garante que o perfil lançado fique marcado como ativo
                viewModelScope.launch {
                    preferences.setSelectedGame(profile.packageName, profile.gameLabel, profile.id)
                }
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_PROFILE_ID, profile.id)
                }
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                context.packageManager.getLaunchIntentForPackage(profile.packageName)?.let {
                    context.startActivity(it)
                }
            }
        }
    }
}
