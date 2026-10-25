package com.mobiladorprox.app.service

import android.content.Context
import android.view.MotionEvent
import android.view.View

/**
 * View transparente e focável usada apenas para "suspender" o cursor do
 * mouse (API oficial de Captura de Ponteiro, disponível desde o Android 8.0)
 * e ler seu movimento relativo, que é então traduzido em pequenos gestos de
 * câmera pelo [MapperAccessibilityService]. Não captura toques de dedo
 * (FLAG_NOT_TOUCHABLE na janela que a hospeda), apenas o mouse.
 */
class PointerCaptureView(
    context: Context,
    private val onDelta: (dx: Float, dy: Float) -> Unit
) : View(context) {

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
        return true
    }
}
