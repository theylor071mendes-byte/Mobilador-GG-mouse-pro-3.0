package com.mobiladorprox.app.data.model

import android.view.KeyEvent

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
 * Conjunto padrão de vínculos de tecla usado ao criar um perfil novo ou ao
 * restaurar um perfil existente. Pensado para jogos de tiro em terceira/
 * primeira pessoa no estilo battle royale (como o Free Fire), mas totalmente
 * editável depois — as posições são apenas um ponto de partida calibrável.
 */
object DefaultProfiles {

    fun defaultBindings(): List<BindingTemplate> = listOf(
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
}
