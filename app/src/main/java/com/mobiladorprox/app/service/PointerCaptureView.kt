package com.mobiladorprox.app.service

import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.mobiladorprox.app.data.model.MouseButtons

/**
 * View transparente usada para "suspender" o cursor do mouse (API oficial de
 * Captura de Ponteiro, disponível desde o Android 8.0) e ler seu movimento
 * relativo, traduzido em gestos de câmera pelo [MapperAccessibilityService].
 *
 * ⚠️  A janela que hospeda esta view NÃO pode ter FLAG_NOT_FOCUSABLE:
 * sem foco, [requestPointerCapture] nunca é chamado e o mouse não é capturado.
 * A view em si é focável ([isFocusableInTouchMode] = true) e chama
 * [requestFocus] + [requestPointerCapture] assim que é anexada à janela
 * (via [onAttachedToWindow]) para garantir a sequência correta independente
 * de quando / se [onWindowFocusChanged] dispara.
 */
class PointerCaptureView(
    context: Context,
    private val onDelta: (dx: Float, dy: Float) -> Unit,
    private val onButton: (code: Int, pressed: Boolean) -> Unit = { _, _ -> }
) : View(context) {

    private var lastButtonState = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Pedir foco imediatamente ao ser adicionado; depois pede a captura.
        post {
            requestFocus()
            runCatching { requestPointerCapture() }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            runCatching { requestPointerCapture() }
        }
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        // Em modo de captura, getX()/getY() = movimento relativo do mouse
        // desde o último evento, não uma posição absoluta na tela.
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
