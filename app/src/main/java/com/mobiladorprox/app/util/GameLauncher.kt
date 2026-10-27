package com.mobiladorprox.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mobiladorprox.app.service.MapperAccessibilityService
import com.mobiladorprox.app.service.OverlayService

/**
 * Ponto único para iniciar o mapeador de um jogo: usado tanto pela tela
 * "Jogos" (Iniciar jogo) quanto pelo Menu (Editar Keymaps), que agora abre
 * o editor direto em cima do jogo em execução.
 */
object GameLauncher {

    fun permissionsReady(context: Context): Boolean =
        DeviceInfoProvider.isAccessibilityServiceEnabled(context, MapperAccessibilityService::class.java.name) &&
            DeviceInfoProvider.canDrawOverlays(context)

    /** Sobe o painel flutuante (OverlayService) com o perfil do jogo e abre o jogo. */
    fun startGame(context: Context, packageName: String, profileId: Long, enterEditMode: Boolean = false) {
        val serviceIntent = Intent(context, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_PROFILE_ID, profileId)
            if (enterEditMode) putExtra(OverlayService.EXTRA_START_EDIT_MODE, true)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            context.startActivity(it)
        }
    }
}
