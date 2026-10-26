package com.mobiladorprox.app.service

import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.mobiladorprox.app.data.model.MouseButtons

/**
 * View transparente e focável usada apenas para "suspender" o cursor do
 * mouse (API oficial de Captura de Ponteiro, disponível desde o Android 8.0)
 * e ler seu movimento relativo, que é então traduzido em pequenos gestos de
 * câmera pelo [MapperAccessibilityService]. Não captura toques de dedo
 * (FLAG_NOT_TOUCHABLE na janela que a hospeda), apenas o mouse.
 *
 * Também detecta os cliques do mouse (esquerdo/direito/meio) enquanto a
 * captura está ativa, repassando-os como "teclas virtuais" — é o único
 * canal oficial para ler cliques de mouse durante a captura de ponteiro.
 */
class PointerCaptureView(
    context: Context,
    private val onDelta: (dx: Float, dy: Float) -> Unit,
    private val onButton: (code: Int, pressed: Boolean) -> Unit = { _, _ -> }
) : View(context) {

    private var lastButtonState = 0

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            runCatching { requestPointerCapture() }
        }
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        // Em modo de captura, getX()/getY() representam o movimento relativo
        // do mouse desde o último evento, não uma posição absoluta na tela.
        onDelta(event.x, event.y)
        handleButtons(event.buttonState)
        return true
    }

    private fun handleButtons(buttonState: Int) {
        diffButton(buttonState, MotionEvent.BUTTON_PRIMARY, MouseButtons.LEFT)
        diffButton(buttonState, MotionEvent.BUTTON_SECONDARY, MouseButtons.RIGHT)
        diffButton(buttonState, MotionEvent.BUTTON_TERTIARY, MouseButtons.MIDDLE)
        lastButtonState = buttonState
    }

    private fun diffButton(buttonState: Int, flag: Int, code: Int) {
        val wasDown = (lastButtonState and flag) != 0
        val isDown = (buttonState and flag) != 0
        if (isDown != wasDown) onButton(code, isDown)
    }
}
