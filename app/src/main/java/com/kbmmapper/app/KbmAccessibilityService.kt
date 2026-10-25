package com.kbmmapper.app

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de Acessibilidade que traduz combinações de teclas de um teclado
 * físico (USB/Bluetooth) conectado ao aparelho em ações de navegação do
 * sistema Android (Voltar, Início, Apps recentes, Notificações, Ajustes
 * rápidos).
 *
 * Não usa root, não acessa a internet, não lê o conteúdo da tela
 * (canRetrieveWindowContent = false) e só é ativado se o usuário habilitar
 * manualmente o serviço em Ajustes > Acessibilidade — exatamente como
 * qualquer app de acessibilidade legítimo (leitores de tela, mapeadores de
 * botão, etc).
 *
 * O mouse (cursor, clique, rolagem) já funciona nativamente no Android
 * 9+ assim que um mouse USB/Bluetooth é conectado — não precisa de
 * nenhum código extra para isso.
 */
class KbmAccessibilityService : AccessibilityService() {

    private var altPressed = false
    private var ctrlPressed = false

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Não precisamos observar eventos de tela, só eventos de tecla.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Serviço interrompido pelo sistema")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "KbmAccessibilityService conectado")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN

        when (event.keyCode) {
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                altPressed = down
            }
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                ctrlPressed = down
            }

            // Esc -> Voltar
            KeyEvent.KEYCODE_ESCAPE -> {
                if (down && prefs.getBoolean(KEY_BACK, true)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return true
                }
            }

            // Tecla Windows/Meta sozinha -> Início
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                if (!down && prefs.getBoolean(KEY_HOME, true)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return true
                }
            }

            // Alt + Tab -> Apps recentes
            KeyEvent.KEYCODE_TAB -> {
                if (down && altPressed && prefs.getBoolean(KEY_RECENTS, true)) {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                    return true
                }
            }

            // Ctrl + N -> Notificações
            KeyEvent.KEYCODE_N -> {
                if (down && ctrlPressed && prefs.getBoolean(KEY_NOTIFICATIONS, true)) {
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    return true
                }
            }

            // F1 -> Ajustes rápidos
            KeyEvent.KEYCODE_F1 -> {
                if (down && prefs.getBoolean(KEY_QUICK_SETTINGS, true)) {
                    performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                    return true
                }
            }
        }

        return super.onKeyEvent(event)
    }

    companion object {
        private const val TAG = "KbmMapper"
        const val PREFS_NAME = "kbm_mapper_prefs"
        const val KEY_BACK = "map_back"
        const val KEY_HOME = "map_home"
        const val KEY_RECENTS = "map_recents"
        const val KEY_NOTIFICATIONS = "map_notifications"
        const val KEY_QUICK_SETTINGS = "map_quick_settings"
    }
}
