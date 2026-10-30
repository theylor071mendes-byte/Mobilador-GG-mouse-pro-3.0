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
 * por traduzir teclas físicas (teclado USB/Bluetooth) e cliques do mouse em
 * toques simulados na tela, de acordo com o perfil carregado em [MapperState].
 *
 * Multi-toque real: o Android só mantém vários toques simulados "vivos" ao
 * mesmo tempo (ex.: analógico + outro botão pressionado) se TODOS eles forem
 * incluídos juntos em um único GestureDescription a cada chamada de
 * dispatchGesture(). Chamar dispatchGesture() de forma independente para
 * cada toque (como antes) faz o sistema cancelar silenciosamente qualquer
 * outro toque "em andamento" (willContinue=true) que não tenha sido incluído
 * na mesma chamada — era exatamente isso que fazia o analógico parar sempre
 * que outro botão era pressionado. Por isso, todo toque virtual ativo
 * (analógico, cada botão "segurar", tap e arrasto de câmera) fica registrado
 * em [virtualTouches] e é redespachado em conjunto por [dispatchCombined].
 *
 * Limitações conhecidas e assumidas (ver tela "Permissões"):
 * - Os toques são simulados por gestos (dispatchGesture); não há acesso ao
 *   motor do jogo nem leitura de memória — a precisão depende da calibração
 *   feita pelo usuário para o layout do jogo e a resolução do aparelho.
 * - "Pressionar e segurar" é implementado encadeando segmentos curtos de
 *   toque contínuo (API oficial StrokeDescription.continueStroke); a
 *   liberação da tecla pode levar até ~300ms para refletir na tela.
 * - O número de toques simultâneos reais é limitado pelo próprio aparelho
 *   (GestureDescription.getMaxStrokeCount(), no mínimo 2 garantido pela API
 *   do Android); além desse limite, toques extras são ignorados e avisados
 *   no log, pois não é algo que o app possa contornar.
 * - O controle de câmera via mouse usa pequenos arrastos repetidos
 *   (não é um cursor nativo do jogo), então a suavidade é diferente da de
 *   um mouse real em PC.
 */
class MapperAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    // Registro único de todos os "dedos" virtuais ativos agora (analógico,
    // cada HOLD, e as entradas transitórias de tap/câmera enquanto duram).
    // A ORDEM de inserção é preservada (LinkedHashMap) para que, se o
    // aparelho suportar menos toques simultâneos do que os pedidos, os mais
    // antigos (geralmente o analógico, que começou primeiro) tenham
    // prioridade sobre um toque recém-iniciado.
    private val virtualTouches = LinkedHashMap<String, VirtualTouch>()
    private var transientCounter = 0

    // Analógico virtual (WASD combinado).
    private val pressedWasd = mutableSetOf<Int>()

    private class VirtualTouch(
        var stroke: GestureDescription.StrokeDescription,
        var x: Float,
        var y: Float
    )

    /**
     * Avisa o [OverlayService] quando o app em primeiro plano muda (pacote da
     * janela ativa), para que o mapeador possa se encerrar sozinho ao sair do
     * jogo selecionado. Não lê texto nem elementos da tela — apenas o nome do
     * pacote já presente no próprio evento de acessibilidade.
     */
    var onForegroundAppChanged: ((String) -> Unit)? = null

    /**
     * Avisa o [OverlayService] que o usuário pressionou a tecla/botão
     * configurado para o componente "Visualização de câmera", para que a
     * captura/ocultação do cursor seja ligada ou desligada de verdade.
     */
    var onToggleCameraRequested: (() -> Unit)? = null

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
        releaseAllInputs()
        instance = null
        onForegroundAppChanged = null
        onToggleCameraRequested = null
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
        // O sistema pode interromper o serviço a qualquer momento (troca de
        // app, permissão revogada, etc.): solta qualquer toque simulado em
        // andamento para não deixar o personagem "andando sozinho".
        releaseAllInputs()
    }

    /**
     * Solta de verdade qualquer entrada simulada em andamento (WASD/analógico
     * e qualquer botão "segurar"), finalizando TODOS os toques ativos juntos,
     * em uma única chamada de gesto, com willContinue=false. Chamado ao
     * soltar uma tecla, ao fechar o editor de keymaps, ao perder o foco do
     * jogo selecionado e ao interromper o próprio Serviço de Acessibilidade —
     * para nunca deixar um toque "preso" na tela.
     */
    fun releaseAllInputs() {
        pressedWasd.clear()
        if (virtualTouches.isNotEmpty()) {
            val builder = GestureDescription.Builder()
            strokeLimitedEntries().forEach { (_, touch) ->
                val path = Path().apply { moveTo(touch.x, touch.y) }
                builder.addStroke(touch.stroke.continueStroke(path, 0, RELEASE_STROKE_MS, false))
            }
            runCatching { dispatchGesture(builder.build(), null, null) }
        }
        virtualTouches.clear()
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
                // Componente "Visualização de câmera": a tecla/botão vinculado
                // apenas liga/desliga a captura do mouse (ver OverlayService);
                // não simula toque na tela, então é tratado à parte do
                // registro de toques virtuais.
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    onToggleCameraRequested?.invoke()
                }
                return true
            }
            GestureType.ANALOG_STICK -> {
                // Tratado exclusivamente via WASD acima; outras teclas não acionam o analógico.
                return false
            }
        }
    }

    /**
     * Chamado pelo [com.mobiladorprox.app.service.OverlayService] quando o clique de um
     * mouse físico é detectado (seja em modo normal, seja durante a captura do ponteiro —
     * ver [PointerCaptureView]). Reaproveita o mesmo mapa de vínculos do teclado: cliques
     * usam códigos sintéticos negativos ([com.mobiladorprox.app.data.model.MouseButtons])
     * que nunca colidem com um KeyEvent.KEYCODE_* real.
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
            GestureType.LOOK_TOGGLE -> if (pressed) onToggleCameraRequested?.invoke()
            GestureType.ANALOG_STICK -> Unit
        }
    }

    // ---------------------------------------------------------------------
    // Despacho combinado: o coração da correção de multi-toque.
    // ---------------------------------------------------------------------

    /**
     * Limita a quantidade de toques simultâneos ao que o aparelho realmente
     * suporta (GestureDescription.getMaxStrokeCount(), garantido >= 2 pela
     * API do Android). Os mais antigos (ordem de início) têm prioridade.
     */
    private fun strokeLimitedEntries(): List<Map.Entry<String, VirtualTouch>> {
        val maxStrokes = runCatching { GestureDescription.getMaxStrokeCount() }
            .getOrDefault(2)
            .coerceAtLeast(1)
        val entries = virtualTouches.entries.toList()
        if (entries.size > maxStrokes) {
            Log.w(
                TAG,
                "Pedidos ${entries.size} toques simultâneos, mas este aparelho só suporta " +
                    "$maxStrokes; os mais recentes serão ignorados neste quadro."
            )
        }
        return entries.take(maxStrokes)
    }

    /** Redespacha TODOS os toques virtuais ativos juntos, em uma única chamada. */
    private fun dispatchCombined() {
        if (virtualTouches.isEmpty()) return
        val builder = GestureDescription.Builder()
        strokeLimitedEntries().forEach { (_, touch) -> builder.addStroke(touch.stroke) }
        runCatching { dispatchGesture(builder.build(), null, null) }
    }

    /**
     * Despacha um toque de duração fixa e não-contínuo (tap ou arrasto de
     * câmera de um quadro) junto com todos os outros toques ativos no mesmo
     * instante, e remove o registro logo em seguida — a animação do próprio
     * gesto já tem duração própria, não precisa de continuação.
     */
    private fun dispatchTransient(idPrefix: String, stroke: GestureDescription.StrokeDescription, x: Float, y: Float) {
        val id = "$idPrefix:${transientCounter++}"
        virtualTouches[id] = VirtualTouch(stroke, x, y)
        dispatchCombined()
        virtualTouches.remove(id)
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
        val radius = ANALOG_RADIUS_PX * binding.sizePercent.coerceIn(0.4f, 2.5f)
        val targetX = centerX + (dx / length) * radius
        val targetY = centerY + (dy / length) * radius

        val touch = virtualTouches[ANALOG_TOUCH_ID]
        if (touch == null) {
            // Primeiro toque: desce no centro do analógico e já arrasta até a direção pedida.
            val path = Path().apply { moveTo(centerX, centerY); lineTo(targetX, targetY) }
            val stroke = GestureDescription.StrokeDescription(path, 0, ANALOG_MOVE_MS, true)
            virtualTouches[ANALOG_TOUCH_ID] = VirtualTouch(stroke, targetX, targetY)
            dispatchCombined()
            scheduleAnalogContinuation()
        } else {
            // Mudou de direção (ex.: soltou D, continua segurando W): arrasta do ponto
            // atual até o novo alvo, sem soltar o toque — e sem afetar nenhum outro
            // toque virtual ativo, pois todos são redespachados juntos.
            val path = Path().apply { moveTo(touch.x, touch.y); lineTo(targetX, targetY) }
            touch.stroke = touch.stroke.continueStroke(path, 0, ANALOG_MOVE_MS, true)
            touch.x = targetX
            touch.y = targetY
            dispatchCombined()
        }
    }

    private fun scheduleAnalogContinuation() {
        handler.postDelayed({
            val touch = virtualTouches[ANALOG_TOUCH_ID] ?: return@postDelayed
            // Mantém o toque parado na posição atual (sem andar) até a próxima mudança de tecla.
            val path = Path().apply { moveTo(touch.x, touch.y) }
            touch.stroke = touch.stroke.continueStroke(path, 0, HOLD_SEGMENT_MS, true)
            dispatchCombined()
            scheduleAnalogContinuation()
        }, HOLD_SEGMENT_MS - HOLD_RENEW_MARGIN_MS)
    }

    private fun releaseAnalogStick() {
        val touch = virtualTouches[ANALOG_TOUCH_ID] ?: return
        // Correção de bug: sem este passo, o último segmento ficava sempre em
        // "willContinue=true" e o toque simulado nunca soltava de fato na
        // tela — o personagem continuava andando sozinho mesmo depois de
        // soltar todas as teclas WASD. Aqui finalizamos o gesto explicitamente
        // (willContinue=false) para soltar o toque no ponto atual.
        val path = Path().apply { moveTo(touch.x, touch.y) }
        touch.stroke = touch.stroke.continueStroke(path, 0, RELEASE_STROKE_MS, false)
        dispatchCombined()
        virtualTouches.remove(ANALOG_TOUCH_ID)
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
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS, false)
        dispatchTransient("tap", stroke, x, y)
    }

    private fun startHold(actionId: String, xPercent: Float, yPercent: Float) {
        val id = holdTouchId(actionId)
        if (virtualTouches.containsKey(id)) return
        val (x, y) = percentToPixels(xPercent, yPercent)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, HOLD_SEGMENT_MS, true)
        virtualTouches[id] = VirtualTouch(stroke, x, y)
        dispatchCombined()
        scheduleHoldContinuation(actionId, x, y)
    }

    private fun scheduleHoldContinuation(actionId: String, x: Float, y: Float) {
        val id = holdTouchId(actionId)
        handler.postDelayed({
            val touch = virtualTouches[id] ?: return@postDelayed
            val path = Path().apply { moveTo(x, y) }
            touch.stroke = touch.stroke.continueStroke(path, 0, HOLD_SEGMENT_MS, true)
            dispatchCombined()
            scheduleHoldContinuation(actionId, x, y)
        }, HOLD_SEGMENT_MS - HOLD_RENEW_MARGIN_MS)
    }

    private fun stopHold(actionId: String) {
        val id = holdTouchId(actionId)
        val touch = virtualTouches[id] ?: return
        // Mesma correção do analógico: finaliza o gesto com willContinue=false
        // para garantir que o toque realmente solte na tela, sem cancelar
        // nenhum outro toque virtual que continue ativo (ex.: o analógico).
        val path = Path().apply { moveTo(touch.x, touch.y) }
        touch.stroke = touch.stroke.continueStroke(path, 0, RELEASE_STROKE_MS, false)
        dispatchCombined()
        virtualTouches.remove(id)
    }

    private fun holdTouchId(actionId: String) = "hold:$actionId"

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
        val stroke = GestureDescription.StrokeDescription(path, 0, LOOK_STROKE_MS, false)
        dispatchTransient("look", stroke, endX, endY)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAllInputs()
        handler.removeCallbacksAndMessages(null)
        onForegroundAppChanged = null
        onToggleCameraRequested = null
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
        private const val ANALOG_TOUCH_ID = "analog"

        private val WASD_CODES = setOf(
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
        )

        @Volatile
        var instance: MapperAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
