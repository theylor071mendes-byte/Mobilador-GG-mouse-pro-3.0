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

    // Analógico virtual (WASD combinado).
    private val pressedWasd = mutableSetOf<Int>()
    private var analogHoldState: HoldState? = null
    private var analogCurrentX = 0f
    private var analogCurrentY = 0f

    private data class HoldState(
        var stroke: GestureDescription.StrokeDescription,
        var active: Boolean,
        var lastX: Float = 0f,
        var lastY: Float = 0f
    )

    /**
     * Avisa o [OverlayService] quando o app em primeiro plano muda (pacote da
     * janela ativa), para que o mapeador possa se encerrar sozinho ao sair do
     * jogo selecionado. Não lê texto nem elementos da tela — apenas o nome do
     * pacote já presente no próprio evento de acessibilidade.
     */
    var onForegroundAppChanged: ((String) -> Unit)? = null

    /**
     * Pacote da janela realmente ativa agora (consulta ao vivo, não um evento
     * antigo guardado). Usado para reconfirmar, no momento de decidir, se o
     * usuário de fato saiu do jogo — evita encerrar o mapeador por causa de um
     * "flash" passageiro do launcher durante a própria animação de abertura
     * do jogo (quando a janela do jogo nunca deixou de estar ativa de verdade).
     * Não lê texto nem elementos da tela, só o nome do pacote da janela.
     */
    fun currentForegroundPackage(): String? =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

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
        onForegroundAppChanged = null
        pressedWasd.clear()
        analogHoldState = null
        MapperState.clear()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Este app não lê o conteúdo da tela; usa só o nome do pacote da
        // janela em primeiro plano (já incluído no próprio evento) para
        // saber quando o usuário saiu do jogo selecionado.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg -> onForegroundAppChanged?.invoke(pkg) }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Serviço interrompido pelo sistema")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!MapperState.isMapperActive) return super.onKeyEvent(event)

        if (event.keyCode in WASD_CODES && MapperState.analogStickBinding != null) {
            handleAnalogKey(event)
            return true
        }

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
            GestureType.ANALOG_STICK -> {
                // Tratado exclusivamente via WASD acima; outras teclas não acionam o analógico.
                return false
            }
        }
    }

    /**
     * Chamado pelo [com.mobiladorprox.app.service.OverlayService] quando o clique de um
     * mouse físico é detectado durante a captura do ponteiro (ver [PointerCaptureView]).
     * Reaproveita o mesmo mapa de vínculos do teclado: cliques usam códigos sintéticos
     * negativos ([com.mobiladorprox.app.data.model.MouseButtons]) que nunca colidem com um
     * KeyEvent.KEYCODE_* real.
     */
    fun handleMouseButton(code: Int, pressed: Boolean) {
        if (!MapperState.isMapperActive) return
        val binding = MapperState.bindingsByKeyCode[code] ?: return
        val gestureType = runCatching { GestureType.valueOf(binding.gestureType) }.getOrNull() ?: return
        when (gestureType) {
            GestureType.TAP -> if (pressed) dispatchTap(binding.xPercent, binding.yPercent)
            GestureType.HOLD -> {
                if (pressed) startHold(binding.actionId, binding.xPercent, binding.yPercent)
                else stopHold(binding.actionId)
            }
            GestureType.LOOK_TOGGLE, GestureType.ANALOG_STICK -> Unit
        }
    }

    // ---------------------------------------------------------------------
    // Analógico virtual (WASD combinado)
    // ---------------------------------------------------------------------

    private fun handleAnalogKey(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> pressedWasd.add(event.keyCode)
            KeyEvent.ACTION_UP -> pressedWasd.remove(event.keyCode)
        }
        updateAnalogStick()
    }

    private fun updateAnalogStick() {
        val binding = MapperState.analogStickBinding ?: return
        var dx = 0f
        var dy = 0f
        if (KeyEvent.KEYCODE_W in pressedWasd) dy -= 1f
        if (KeyEvent.KEYCODE_S in pressedWasd) dy += 1f
        if (KeyEvent.KEYCODE_A in pressedWasd) dx -= 1f
        if (KeyEvent.KEYCODE_D in pressedWasd) dx += 1f

        if (dx == 0f && dy == 0f) {
            releaseAnalogStick()
            return
        }

        val length = kotlin.math.hypot(dx, dy)
        val (centerX, centerY) = percentToPixels(binding.xPercent, binding.yPercent)
        val targetX = centerX + (dx / length) * ANALOG_RADIUS_PX
        val targetY = centerY + (dy / length) * ANALOG_RADIUS_PX

        val state = analogHoldState
        if (state == null) {
            // Primeiro toque: desce no centro do analógico e já arrasta até a direção pedida.
            val path = Path().apply { moveTo(centerX, centerY); lineTo(targetX, targetY) }
            val stroke = GestureDescription.StrokeDescription(path, 0, ANALOG_MOVE_MS, true)
            analogHoldState = HoldState(stroke, true)
            analogCurrentX = targetX
            analogCurrentY = targetY
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
            scheduleAnalogContinuation()
        } else {
            // Mudou de direção (ex.: soltou D, continua segurando W): arrasta do ponto
            // atual até o novo alvo, sem soltar o toque.
            val path = Path().apply { moveTo(analogCurrentX, analogCurrentY); lineTo(targetX, targetY) }
            val nextStroke = state.stroke.continueStroke(path, 0, ANALOG_MOVE_MS, true)
            state.stroke = nextStroke
            analogCurrentX = targetX
            analogCurrentY = targetY
            dispatchGesture(GestureDescription.Builder().addStroke(nextStroke).build(), null, null)
        }
    }

    private fun scheduleAnalogContinuation() {
        handler.postDelayed({
            val state = analogHoldState ?: return@postDelayed
            if (!state.active) return@postDelayed
            // Mantém o toque parado na posição atual (sem andar) até a próxima mudança de tecla.
            val path = Path().apply { moveTo(analogCurrentX, analogCurrentY) }
            val nextStroke = state.stroke.continueStroke(path, 0, HOLD_SEGMENT_MS, true)
            state.stroke = nextStroke
            dispatchGesture(GestureDescription.Builder().addStroke(nextStroke).build(), null, null)
            scheduleAnalogContinuation()
        }, HOLD_SEGMENT_MS - HOLD_RENEW_MARGIN_MS)
    }

    private fun releaseAnalogStick() {
        val state = analogHoldState ?: return
        state.active = false
        analogHoldState = null
        // Correção de bug: sem este passo, o último segmento ficava sempre em
        // "willContinue=true" e o toque simulado nunca soltava de fato na
        // tela — o personagem continuava andando sozinho mesmo depois de
        // soltar todas as teclas WASD. Aqui finalizamos o gesto explicitamente
        // (willContinue=false) para soltar o toque no ponto atual.
        val path = Path().apply { moveTo(analogCurrentX, analogCurrentY) }
        val finalStroke = state.stroke.continueStroke(path, 0, RELEASE_STROKE_MS, false)
        dispatchGesture(GestureDescription.Builder().addStroke(finalStroke).build(), null, null)
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
        val state = HoldState(initialStroke, true, x, y)
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
        val state = activeHolds.remove(actionId) ?: return
        state.active = false
        // Mesma correção do analógico: finaliza o gesto com willContinue=false
        // para garantir que o toque realmente solte na tela.
        val path = Path().apply { moveTo(state.lastX, state.lastY) }
        val finalStroke = state.stroke.continueStroke(path, 0, RELEASE_STROKE_MS, false)
        dispatchGesture(GestureDescription.Builder().addStroke(finalStroke).build(), null, null)
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
        pressedWasd.clear()
        analogHoldState = null
        onForegroundAppChanged = null
        instance = null
        MapperState.clear()
    }

    companion object {
        private const val TAG = "MobiladorMapper"
        private const val TAP_DURATION_MS = 45L
        private const val HOLD_SEGMENT_MS = 300L
        private const val HOLD_RENEW_MARGIN_MS = 60L
        private const val RELEASE_STROKE_MS = 16L
        private const val LOOK_STROKE_MS = 16L
        private const val ANALOG_RADIUS_PX = 140f
        private const val ANALOG_MOVE_MS = 60L

        private val WASD_CODES = setOf(
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
        )

        @Volatile
        var instance: MapperAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
