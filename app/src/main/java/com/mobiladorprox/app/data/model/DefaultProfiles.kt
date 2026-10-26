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
 * editável depois — as posições, teclas e cliques são apenas um ponto de
 * partida calibrável.
 *
 * Clique esquerdo do mouse = atirar, clique direito = mirar (segurados
 * durante a captura do mouse/câmera); W/A/S/D formam um único analógico
 * virtual (em vez de 4 toques fixos independentes).
 */
object DefaultProfiles {

    fun defaultBindings(): List<BindingTemplate> = listOf(
        BindingTemplate("fire", "Atirar (clique esquerdo)", MouseButtons.LEFT, GestureType.HOLD, 0.85f, 0.80f),
        BindingTemplate("aim", "Mirar (clique direito)", MouseButtons.RIGHT, GestureType.HOLD, 0.70f, 0.78f),
        BindingTemplate("jump", "Pular", KeyEvent.KEYCODE_C, GestureType.TAP, 0.78f, 0.60f),
        BindingTemplate("crouch", "Agachar / Deitar", KeyEvent.KEYCODE_CTRL_LEFT, GestureType.TAP, 0.78f, 0.70f),
        BindingTemplate("reload", "Recarregar", KeyEvent.KEYCODE_R, GestureType.TAP, 0.88f, 0.65f),
        BindingTemplate("menu", "Abrir menu / mochila", KeyEvent.KEYCODE_TAB, GestureType.TAP, 0.95f, 0.06f),
        // Representativo: o analógico é acionado pela combinação de W/A/S/D,
        // não apenas pela tecla W armazenada aqui (ver MapperAccessibilityService).
        BindingTemplate("move", "Movimento (WASD)", KeyEvent.KEYCODE_W, GestureType.ANALOG_STICK, 0.16f, 0.78f),
        BindingTemplate("camera", "Suspender mouse / Câmera", KeyEvent.KEYCODE_CAPS_LOCK, GestureType.LOOK_TOGGLE, 0.60f, 0.45f)
    )
}
