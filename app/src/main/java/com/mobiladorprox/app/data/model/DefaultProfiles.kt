package com.mobiladorprox.app.data.model

import android.view.KeyEvent

/** Conjunto de valores numéricos aplicado por uma predefinição de sensibilidade. */
data class PresetValues(
    val sensitivityHorizontal: Float,
    val sensitivityVertical: Float,
    val cursorSpeed: Float,
    val dpiMultiplier: Float
)

/** Modelo de um vínculo de tecla usado para semear um novo perfil. */
data class BindingTemplate(
    val actionId: String,
    val label: String,
    val keyCode: Int,
    val gestureType: GestureType,
    val xPercent: Float,
    val yPercent: Float
)

/**
 * Valores padrão dos perfis e predefinições. Os números abaixo são apenas
 * pontos de partida configuráveis — não representam nenhuma promessa de
 * desempenho, apenas combinações diferentes de sensibilidade.
 */
object DefaultProfiles {

    fun presetValues(preset: PresetMode): PresetValues = when (preset) {
        PresetMode.PRECISAO -> PresetValues(0.35f, 0.35f, 0.6f, 1.2f)
        PresetMode.RUSH -> PresetValues(0.85f, 0.8f, 1.3f, 1.6f)
        PresetMode.EQUILIBRIO -> PresetValues(0.6f, 0.55f, 1.0f, 1.4f)
        PresetMode.LONGA_DISTANCIA -> PresetValues(0.3f, 0.45f, 0.7f, 1.1f)
        PresetMode.PERSONALIZADO -> PresetValues(0.6f, 0.55f, 1.0f, 1.4f)
    }

    /** Bindings padrão para o perfil Free Fire. Posições calibráveis pelo usuário. */
    fun freeFireBindings(): List<BindingTemplate> = listOf(
        BindingTemplate("fire", "Atirar", KeyEvent.KEYCODE_SPACE, GestureType.TAP, 0.85f, 0.80f),
        BindingTemplate("aim", "Mirar / Zoom", KeyEvent.KEYCODE_ALT_RIGHT, GestureType.HOLD, 0.70f, 0.78f),
        BindingTemplate("jump", "Pular", KeyEvent.KEYCODE_C, GestureType.TAP, 0.78f, 0.60f),
        BindingTemplate("crouch", "Agachar / Deitar", KeyEvent.KEYCODE_CTRL_LEFT, GestureType.TAP, 0.78f, 0.70f),
        BindingTemplate("reload", "Recarregar", KeyEvent.KEYCODE_R, GestureType.TAP, 0.88f, 0.65f),
        BindingTemplate("menu", "Abrir menu / mochila", KeyEvent.KEYCODE_TAB, GestureType.TAP, 0.95f, 0.06f),
        BindingTemplate("move_up", "Mover para frente (W)", KeyEvent.KEYCODE_W, GestureType.HOLD, 0.18f, 0.72f),
        BindingTemplate("move_down", "Mover para trás (S)", KeyEvent.KEYCODE_S, GestureType.HOLD, 0.18f, 0.85f),
        BindingTemplate("move_left", "Mover à esquerda (A)", KeyEvent.KEYCODE_A, GestureType.HOLD, 0.10f, 0.78f),
        BindingTemplate("move_right", "Mover à direita (D)", KeyEvent.KEYCODE_D, GestureType.HOLD, 0.26f, 0.78f),
        BindingTemplate("camera", "Suspender mouse / Câmera", KeyEvent.KEYCODE_CAPS_LOCK, GestureType.LOOK_TOGGLE, 0.60f, 0.45f)
    )

    /** Bindings genéricos de exemplo, pensados para serem ajustados pelo jogador. */
    fun genericGameBindings(): List<BindingTemplate> = listOf(
        BindingTemplate("action_primary", "Ação principal", KeyEvent.KEYCODE_SPACE, GestureType.TAP, 0.85f, 0.80f),
        BindingTemplate("action_secondary", "Ação secundária", KeyEvent.KEYCODE_E, GestureType.TAP, 0.70f, 0.78f),
        BindingTemplate("menu", "Abrir menu", KeyEvent.KEYCODE_TAB, GestureType.TAP, 0.95f, 0.06f),
        BindingTemplate("camera", "Suspender mouse / Câmera", KeyEvent.KEYCODE_CAPS_LOCK, GestureType.LOOK_TOGGLE, 0.60f, 0.45f)
    )

    /** Perfil personalizado começa sem nenhum vínculo — o usuário monta do zero. */
    fun emptyBindings(): List<BindingTemplate> = emptyList()

    fun bindingsFor(gameType: GameType): List<BindingTemplate> = when (gameType) {
        GameType.FREE_FIRE -> freeFireBindings()
        GameType.OUTRO_JOGO -> genericGameBindings()
        GameType.PERSONALIZADO -> emptyBindings()
    }
}
