package com.mobiladorprox.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.mobiladorprox.app.data.model.GestureType

/**
 * Serviço de Acessibilidade (API pública do Android, sem root) responsável
 * por traduzir teclas físicas (teclado USB/Bluetooth) em toques simulados
 * na tela, de acordo com o perfil carregado em [MapperState].
 *
 * Limitações conhecidas e assumidas (ver tela "Permissões"):
 * - Os toques são simulados por gestos (dispatchGesture); não há acesso ao
 *   motor do jogo nem leitura de memória — a precisão depende da calibração
 *   feita pelo usuário para o layout do jogo e a resolução do aparelho.
 * - "Pressionar e segurar" é implementado encadeando segmentos curtos de
 *   toque contínuo (API oficial StrokeDescription.continueStroke); a
 *   liberação da tecla pode levar até ~300ms para refletir na tela.
 * - O controle de câmera via mouse usa pequenos arrastos repetidos
 *   (não é um cursor nativo do jogo), então a suavidade é diferente da de
 *   um mouse real em PC.
 */
class MapperAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val activeHolds = mutableMapOf<String, HoldState>()

    private data class HoldState(var stroke: GestureDescription.StrokeDescription, var active: Boolean)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 50
        }
        instance = this
        Log.d(TAG, "MapperAccessibilityService conectado")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        MapperState.clear()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Este app não lê o conteúdo da tela; só reage a eventos de tecla.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Serviço interrompido pelo sistema")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!MapperState.isMapperActive) return super.onKeyEvent(event)

        val binding = MapperState.bindingsByKeyCode[event.keyCode] ?: return super.onKeyEvent(event)
        val gestureType = runCatching { GestureType.valueOf(binding.gestureType) }.getOrNull()
            ?: return super.onKeyEvent(event)

        when (gestureType) {
            GestureType.TAP -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    dispatchTap(binding.xPercent, binding.yPercent)
                }
                return true
            }
            GestureType.HOLD -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                        startHold(binding.actionId, binding.xPercent, binding.yPercent)
                    }
                    KeyEvent.ACTION_UP -> stopHold(binding.actionId)
                }
                return true
            }
            GestureType.LOOK_TOGGLE -> {
                // Tratado pela UI/overlay (alterna a captura do mouse), não por gesto de toque.
                return false
            }
        }
    }

    /** Converte porcentagem de tela (0f..1f) em pixels reais do aparelho. */
    private fun percentToPixels(xPercent: Float, yPercent: Float): Pair<Float, Float> {
        val metrics = resources.displayMetrics
        val x = (xPercent.coerceIn(0f, 1f)) * metrics.widthPixels
        val y = (yPercent.coerceIn(0f, 1f)) * metrics.heightPixels
        return x to y
    }

    fun dispatchTap(xPercent: Float, yPercent: Float) {
        val (x, y) = percentToPixels(xPercent, yPercent)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun startHold(actionId: String, xPercent: Float, yPercent: Float) {
        if (activeHolds.containsKey(actionId)) return
        val (x, y) = percentToPixels(xPercent, yPercent)
        val path = Path().apply { moveTo(x, y) }
        val initialStroke = GestureDescription.StrokeDescription(path, 0, HOLD_SEGMENT_MS, true)
        val state = HoldState(initialStroke, true)
        activeHolds[actionId] = state
        dispatchGesture(GestureDescription.Builder().addStroke(initialStroke).build(), null, null)
        scheduleHoldContinuation(actionId, x, y)
    }

    private fun scheduleHoldContinuation(actionId: String, x: Float, y: Float) {
        handler.postDelayed({
            val state = activeHolds[actionId] ?: return@postDelayed
            if (!state.active) return@postDelayed
            val path = Path().apply { moveTo(x, y) }
            val nextStroke = state.stroke.continueStroke(path, 0, HOLD_SEGMENT_MS, true)
            state.stroke = nextStroke
            dispatchGesture(GestureDescription.Builder().addStroke(nextStroke).build(), null, null)
            scheduleHoldContinuation(actionId, x, y)
        }, HOLD_SEGMENT_MS - HOLD_RENEW_MARGIN_MS)
    }

    private fun stopHold(actionId: String) {
        val state = activeHolds[actionId] ?: return
        state.active = false
        activeHolds.remove(actionId)
        // O segmento atual (willContinue=true) termina sozinho em até
        // HOLD_SEGMENT_MS, soltando o toque naturalmente na tela.
    }

    /** Usado pelo OverlayService durante a captura do mouse para mover a câmera. */
    fun dispatchLookSwipe(centerXPercent: Float, centerYPercent: Float, dxPixels: Float, dyPixels: Float) {
        if (dxPixels == 0f && dyPixels == 0f) return
        val (cx, cy) = percentToPixels(centerXPercent, centerYPercent)
        val endX = (cx + dxPixels).coerceIn(0f, resources.displayMetrics.widthPixels.toFloat())
        val endY = (cy + dyPixels).coerceIn(0f, resources.displayMetrics.heightPixels.toFloat())
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, LOOK_STROKE_MS)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        activeHolds.clear()
        instance = null
        MapperState.clear()
    }

    companion object {
        private const val TAG = "MobiladorMapper"
        private const val TAP_DURATION_MS = 45L
        private const val HOLD_SEGMENT_MS = 300L
        private const val HOLD_RENEW_MARGIN_MS = 60L
        private const val LOOK_STROKE_MS = 16L

        @Volatile
        var instance: MapperAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
