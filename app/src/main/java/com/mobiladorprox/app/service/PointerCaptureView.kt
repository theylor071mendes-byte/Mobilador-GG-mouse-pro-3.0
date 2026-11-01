package com.mobiladorprox.app.service

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.mobiladorprox.app.data.model.MouseButtons

/**
 * View transparente que intercepta todos os eventos de mouse enquanto o
 * cursor está "suspenso" (câmera livre ativa).
 *
 * ## Por que não usar requestPointerCapture()
 * A API oficial de captura de ponteiro ([View.requestPointerCapture]) exige
 * que a janela tenha recebido um toque REAL antes do pedido — caso contrário
 * o Android simplesmente ignora a chamada sem nenhuma mensagem de erro.
 * Em overlays de serviço (TYPE_APPLICATION_OVERLAY) isso nunca acontece de
 * forma confiável porque o jogo é quem recebe os toques, não esta camada.
 *
 * ## Solução: onGenericMotionEvent com SOURCE_MOUSE
 * Qualquer janela overlay pode interceptar eventos genéricos de motion
 * (mouse, joystick…) usando [onGenericMotionEvent]. Desde que a janela esteja
 * no topo da pilha de Z, o sistema entrega TODOS os eventos de mouse a ela
 * antes de passá-los para a janela de baixo — sem precisar de nenhum pedido
 * de captura formal e sem precisar de foco de janela.
 *
 * O movimento relativo do mouse é lido em [MotionEvent.AXIS_RELATIVE_X] /
 * [MotionEvent.AXIS_RELATIVE_Y] (Android 8+), que contêm o deslocamento
 * bruto do sensor, não a posição absoluta do cursor na tela. Isso é exatamente
 * o que precisamos para mover a câmera.
 *
 * ## Limitação de hover
 * Enquanto esta janela estiver ativa, eventos de hover (HOVER_MOVE) do mouse
 * chegam aqui em vez de chegar ao jogo. Retornamos `true` para consumi-los e
 * evitar que o jogo trate o hover como toque. O botão físico do mouse gera
 * ACTION_BUTTON_PRESS/RELEASE que também é interceptado aqui e traduzido em
 * ação mapeada via [onButton].
 *
 * ## Flags de janela corretas
 * A janela que hospeda esta view deve usar:
 *   FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN
 * Não precisa de foco de teclado — só recebe eventos de ponteiro.
 * FLAG_NOT_TOUCHABLE NÃO deve ser usado aqui pois impede a entrega de
 * GenericMotionEvent ao overlay.
 */
class PointerCaptureView(
    context: Context,
    private val onDelta: (dx: Float, dy: Float) -> Unit,
    private val onButton: (code: Int, pressed: Boolean) -> Unit = { _, _ -> }
) : View(context) {

    private var lastButtonState = 0

    init {
        // Não precisa de foco de teclado — só intercepta eventos de ponteiro.
        isFocusable = false
        isFocusableInTouchMode = false
    }

    /**
     * Intercepta movimento do mouse (SOURCE_MOUSE).
     * Retorna true para consumir o evento e impedir que o jogo processe
     * o hover como toque ou clique.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE == 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                // AXIS_RELATIVE_X/Y = deslocamento bruto do sensor desde o
                // último evento. Disponível em Android 8.0+ (API 26).
                val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                if (dx != 0f || dy != 0f) onDelta(dx, dy)
                handleButtons(event.buttonState)
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                handleButtons(event.buttonState)
                return true
            }
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_EXIT -> return true
        }
        return false
    }

    /**
     * Também intercepta toques diretos para o caso de o mouse enviar
     * ACTION_DOWN/UP em vez de ACTION_BUTTON_PRESS/RELEASE em alguns dispositivos.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE == 0) return false
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
