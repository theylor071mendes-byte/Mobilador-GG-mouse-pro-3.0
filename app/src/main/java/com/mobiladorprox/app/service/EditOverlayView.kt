package com.mobiladorprox.app.service

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType
import com.mobiladorprox.app.data.model.MouseButtons
import com.mobiladorprox.app.data.model.PollingRate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Overlay nativo (sem Compose) exibido sobre o jogo de verdade: o jogo
 * continua visível atrás (visualização real, "FPS"), e esta camada
 * transparente mostra os botões virtuais do perfil ativo para editar ao
 * vivo — arrastar para reposicionar, tocar para renomear/trocar
 * tecla-ou-clique/redimensionar/excluir, tocar duas vezes em área vazia ou
 * "+ Componente" para criar um novo.
 */
class EditOverlayView(
    context: Context,
    private val callbacks: Callbacks
) : FrameLayout(context) {

    interface Callbacks {
        fun onSave(bindings: List<KeyBindingEntity>)
        fun onExit()
        fun onToggleMouseCapture()
        fun isMouseCaptureActive(): Boolean
        fun isAccessibilityReady(): Boolean

        fun getMouseSensitivity(): Float
        fun setMouseSensitivity(value: Float)
        fun getPollingRate(): PollingRate
        fun setPollingRate(rate: PollingRate)

        /** Velocidade horizontal da câmera no controle livre, 0 a 1000 (500 = padrão). */
        fun getFreeLookSpeedX(): Float
        fun setFreeLookSpeedX(value: Float)

        /** Velocidade vertical da puxada (olhar p/ cima/baixo) no controle livre, 0 a 1000 (500 = padrão). */
        fun getFreeLookSpeedY(): Float
        fun setFreeLookSpeedY(value: Float)
    }

    private val bindings = mutableListOf<KeyBindingEntity>()
    private val chipViews = mutableMapOf<Long, View>()
    private var dirty = false
    private var previewMode = false
    private var editorWidth = 0
    private var editorHeight = 0
    private var nextTempId = -1L

    /** Botão selecionado (tocado uma vez) exibindo o X de exclusão rápida por cima. */
    private var selectedBindingId: Long? = null
    private var deleteBadge: TextView? = null

    private val chipLayer = FrameLayout(context)
    private val toolbarScroll: HorizontalScrollView
    private lateinit var mouseToggleChip: TextView
    private lateinit var cameraSpeedGearChip: TextView
    private val reopenChip: TextView

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!previewMode) addComponent(e.x, e.y)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Tocar em área vazia do editor desseleciona o botão atual (some o X).
            if (selectedBindingId != null) clearSelection()
            return true
        }
    })

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(chipLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        chipLayer.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }

        toolbarScroll = buildToolbar()
        addView(
            toolbarScroll,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(26)
            }
        )

        reopenChip = OverlayStyle.pillButton(context, "✎ Editar", OverlayStyle.NEON_BLUE, true).apply {
            visibility = View.GONE
            setOnClickListener { setPreviewMode(false) }
        }
        addView(
            reopenChip,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(26)
                rightMargin = dp(14)
            }
        )
    }

    fun setBindings(initial: List<KeyBindingEntity>) {
        bindings.clear()
        bindings.addAll(initial)
        dirty = false
        renderChips()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        editorWidth = w
        editorHeight = h
        renderChips()
    }

    private fun dp(v: Int) = OverlayStyle.dp(context, v)

    // ---------------------------------------------------------------------
    // Barra superior
    // ---------------------------------------------------------------------

    private fun buildToolbar(): HorizontalScrollView {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(OverlayStyle.PANEL_BG)
                setStroke(dp(1), OverlayStyle.NEON_PURPLE)
            }
        }
        fun space() = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 1) }

        row.addView(OverlayStyle.pillButton(context, "Sair", OverlayStyle.NEON_PURPLE, false).apply {
            setOnClickListener { confirmExitIfNeeded() }
        })
        row.addView(space())
        row.addView(OverlayStyle.pillButton(context, "Salvar", OverlayStyle.NEON_BLUE, true).apply {
            setOnClickListener { saveNow() }
        })
        row.addView(space())
        row.addView(OverlayStyle.pillButton(context, "+ Componente", OverlayStyle.NEON_PURPLE, true).apply {
            setOnClickListener { addComponent(null, null) }
        })
        row.addView(space())
        row.addView(OverlayStyle.pillButton(context, "Visualização", OverlayStyle.NEON_BLUE, false).apply {
            setOnClickListener { setPreviewMode(true) }
        })
        row.addView(space())
        row.addView(OverlayStyle.pillButton(context, "Mouse", OverlayStyle.NEON_PURPLE, false).apply {
            setOnClickListener { openMouseSettingsDialog() }
        })
        row.addView(space())
        mouseToggleChip = OverlayStyle.pillButton(context, "Suspender mouse", OverlayStyle.NEON_GREEN, false).apply {
            setOnClickListener {
                if (!callbacks.isAccessibilityReady()) {
                    toast("Ative o Serviço de Acessibilidade para usar a câmera")
                } else {
                    callbacks.onToggleMouseCapture()
                    refreshMouseToggle()
                }
            }
        }
        row.addView(mouseToggleChip)
        row.addView(space())
        cameraSpeedGearChip = OverlayStyle.pillButton(context, "⚙", OverlayStyle.NEON_GREEN, true).apply {
            visibility = View.GONE
            setOnClickListener { openFreeLookSpeedDialog() }
        }
        row.addView(cameraSpeedGearChip)
        refreshMouseToggle()

        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun mouseToggleLabel() = if (callbacks.isMouseCaptureActive()) "Suspender mouse: ATIVO (câmera livre)" else "Suspender mouse"

    fun refreshMouseToggle() {
        val active = callbacks.isMouseCaptureActive()
        mouseToggleChip.text = mouseToggleLabel()
        val bg = mouseToggleChip.background as? GradientDrawable
        bg?.setColor(if (active) OverlayStyle.NEON_GREEN else OverlayStyle.CHIP_IDLE_BG)
        bg?.setStroke(dp(1), OverlayStyle.NEON_GREEN)
        mouseToggleChip.setTextColor(if (active) Color.BLACK else OverlayStyle.NEON_GREEN)
        cameraSpeedGearChip.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun setPreviewMode(preview: Boolean) {
        previewMode = preview
        clearSelection()
        toolbarScroll.visibility = if (preview) View.GONE else View.VISIBLE
        reopenChip.visibility = if (preview) View.VISIBLE else View.GONE
        renderChips()
    }

    private fun confirmExitIfNeeded() {
        if (!dirty) {
            callbacks.onExit()
            return
        }
        showOverlayDialog(
            AlertDialog.Builder(context)
                .setTitle("Sair sem salvar?")
                .setMessage("Há alterações não salvas nos keymaps deste perfil.")
                .setPositiveButton("Salvar e sair") { _, _ -> saveNow(); callbacks.onExit() }
                .setNegativeButton("Sair sem salvar") { _, _ -> callbacks.onExit() }
                .setNeutralButton("Cancelar", null)
                .create()
        )
    }

    private fun saveNow() {
        callbacks.onSave(bindings.toList())
        markSaved()
        toast("Configuração salva")
    }

    // ---------------------------------------------------------------------
    // Chips (botões virtuais)
    // ---------------------------------------------------------------------

    private fun renderChips() {
        chipLayer.removeAllViews()
        chipViews.clear()
        if (selectedBindingId != null && bindings.none { it.id == selectedBindingId }) selectedBindingId = null
        if (editorWidth <= 0 || editorHeight <= 0) return

        bindings.forEach { binding ->
            val chip = buildChip(binding)
            chipViews[binding.id] = chip
            chipLayer.addView(chip, chipLayoutParams(binding))
        }
        renderDeleteBadge()
    }

    /** Analógico vem maior por padrão (cruz com 4 pontas), demais botões continuam circulares e menores. */
    private fun componentSizePx(binding: KeyBindingEntity): Int {
        val base = if (binding.gestureType == GestureType.ANALOG_STICK.name) 96 else 40
        return dp((base * binding.sizePercent).roundToInt())
    }

    private fun chipLayoutParams(binding: KeyBindingEntity): FrameLayout.LayoutParams {
        val size = componentSizePx(binding)
        return FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (binding.xPercent * editorWidth).roundToInt() - size / 2
            topMargin = (binding.yPercent * editorHeight).roundToInt() - size / 2
        }
    }

    private fun buildChip(binding: KeyBindingEntity): View {
        val isAnalog = binding.gestureType == GestureType.ANALOG_STICK.name
        val chip: View = if (isAnalog) {
            AnalogStickView(context).apply { dim = previewMode }
        } else {
            TextView(context).apply {
                text = binding.label.take(2).uppercase()
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                textSize = if (previewMode) 9f else 11f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(OverlayStyle.NEON_PURPLE)
                    alpha = if (previewMode) 110 else 230
                    if (!previewMode) setStroke(dp(1), Color.WHITE)
                }
            }
        }
        if (!previewMode) {
            chip.setOnTouchListener(ChipDragListener(binding.id))
        }
        return chip
    }

    /**
     * Visual do analógico virtual (WASD) no estilo de referência pedido: uma
     * cruz com um ponto direcional em cima/baixo/esquerda/direita ao redor de
     * um centro, sobre uma base translúcida — em vez do chip circular simples
     * usado pelos demais botões. Desenhado em Canvas para ficar leve.
     */
    private inner class AnalogStickView(ctx: Context) : View(ctx) {
        var dim: Boolean = false
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat() }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        private fun withAlpha(color: Int, alpha: Int) =
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f
            val cy = h / 2f
            val armLen = minOf(w, h) * 0.40f
            val dotR = minOf(w, h) * 0.13f
            val mul = if (dim) 110 else 235

            basePaint.color = withAlpha(Color.BLACK, (mul * 0.45f).roundToInt())
            canvas.drawCircle(cx, cy, minOf(w, h) * 0.48f, basePaint)

            linePaint.color = withAlpha(Color.WHITE, mul / 2)
            canvas.drawLine(cx - armLen, cy, cx + armLen, cy, linePaint)
            canvas.drawLine(cx, cy - armLen, cx, cy + armLen, linePaint)

            dotPaint.color = withAlpha(OverlayStyle.NEON_BLUE, mul)
            canvas.drawCircle(cx, cy - armLen, dotR, dotPaint)
            canvas.drawCircle(cx, cy + armLen, dotR, dotPaint)
            canvas.drawCircle(cx - armLen, cy, dotR, dotPaint)
            canvas.drawCircle(cx + armLen, cy, dotR, dotPaint)

            dotPaint.color = withAlpha(Color.WHITE, mul)
            canvas.drawCircle(cx, cy, dotR * 0.55f, dotPaint)
        }
    }

    private inner class ChipDragListener(private val bindingId: Long) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startLeft = 0
        private var startTop = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startLeft = lp.leftMargin
                    startTop = lp.topMargin
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                        if (!moved) clearSelection()
                        moved = true
                    }
                    val size = v.layoutParams.width
                    lp.leftMargin = (startLeft + dx).roundToInt().coerceIn(-size / 2, editorWidth - size / 2)
                    lp.topMargin = (startTop + dy).roundToInt().coerceIn(-size / 2, editorHeight - size / 2)
                    v.layoutParams = lp
                    updateBindingPosition(bindingId, lp, size)
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (selectedBindingId == bindingId) {
                            bindings.find { it.id == bindingId }?.let { openBindingDialog(it) }
                        } else {
                            selectChip(bindingId)
                        }
                    }
                }
            }
            return true
        }
    }

    // ---------------------------------------------------------------------
    // Seleção + X de exclusão rápida
    // ---------------------------------------------------------------------

    private fun selectChip(bindingId: Long) {
        selectedBindingId = bindingId
        renderDeleteBadge()
    }

    private fun clearSelection() {
        if (selectedBindingId == null) return
        selectedBindingId = null
        deleteBadge?.let { chipLayer.removeView(it) }
        deleteBadge = null
    }

    private fun renderDeleteBadge() {
        deleteBadge?.let { chipLayer.removeView(it) }
        deleteBadge = null
        val id = selectedBindingId ?: return
        val chip = chipViews[id] ?: return
        val chipLp = chip.layoutParams as? FrameLayout.LayoutParams ?: return
        val badgeSize = dp(22)
        val badge = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(OverlayStyle.DANGER_RED)
                setStroke(dp(1), Color.WHITE)
            }
            setOnClickListener {
                bindings.removeAll { it.id == id }
                dirty = true
                clearSelection()
                renderChips()
            }
        }
        val lp = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
            leftMargin = chipLp.leftMargin + chipLp.width - badgeSize * 2 / 3
            topMargin = chipLp.topMargin - badgeSize / 3
        }
        chipLayer.addView(badge, lp)
        deleteBadge = badge
    }

    private fun updateBindingPosition(bindingId: Long, lp: FrameLayout.LayoutParams, size: Int) {
        val index = bindings.indexOfFirst { it.id == bindingId }
        if (index < 0) return
        val xPercent = ((lp.leftMargin + size / 2f) / editorWidth).coerceIn(0f, 1f)
        val yPercent = ((lp.topMargin + size / 2f) / editorHeight).coerceIn(0f, 1f)
        bindings[index] = bindings[index].copy(xPercent = xPercent, yPercent = yPercent)
        dirty = true
    }

    private fun addComponent(x: Float?, y: Float?) {
        val xPercent = if (x != null && editorWidth > 0) (x / editorWidth).coerceIn(0f, 1f) else 0.5f
        val yPercent = if (y != null && editorHeight > 0) (y / editorHeight).coerceIn(0f, 1f) else 0.5f
        val binding = KeyBindingEntity(
            id = nextTempId--,
            profileId = bindings.firstOrNull()?.profileId ?: 0L,
            actionId = "custom_${System.currentTimeMillis()}",
            label = "Novo",
            keyCode = MouseButtons.LEFT,
            gestureType = GestureType.TAP.name,
            xPercent = xPercent,
            yPercent = yPercent
        )
        bindings.add(binding)
        dirty = true
        renderChips()
        openBindingDialog(binding, isNew = true)
    }

    // ---------------------------------------------------------------------
    // Diálogo nativo de edição do botão
    // ---------------------------------------------------------------------

    private fun openBindingDialog(binding: KeyBindingEntity, isNew: Boolean = false) {
        val pad = dp(18)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val labelField = EditText(context).apply {
            setText(binding.label)
            hint = "Nome da ação"
        }
        root.addView(labelField)

        val gestureGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val gestureOptions = listOf(
            GestureType.TAP to "Toque",
            GestureType.HOLD to "Segurar",
            GestureType.LOOK_TOGGLE to "Câmera",
            GestureType.ANALOG_STICK to "Analógico"
        )
        val currentGesture = runCatching { GestureType.valueOf(binding.gestureType) }.getOrNull() ?: GestureType.TAP
        gestureOptions.forEach { (type, label) ->
            val rb = RadioButton(context).apply {
                text = label
                id = View.generateViewId()
                isChecked = type == currentGesture
                tag = type
            }
            gestureGroup.addView(rb)
        }
        root.addView(sectionLabel("Tipo de gesto"))
        root.addView(gestureGroup)

        var selectedKeyCode = binding.keyCode
        val isMouseInitially = MouseButtons.isMouseButton(binding.keyCode)
        val keyboardModeBtn = TextView(context)
        val mouseModeBtn = TextView(context)
        val inputDetail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun refreshKeyLabel(keyCaptureBtn: TextView) {
            keyCaptureBtn.text = "Tecla: " + keyLabelFor(selectedKeyCode)
        }

        fun buildKeyboardDetail(): View {
            val btn = TextView(context).apply {
                setPadding(0, dp(8), 0, dp(8))
                setTextColor(OverlayStyle.NEON_BLUE)
            }
            refreshKeyLabel(btn)
            btn.setOnClickListener {
                btn.text = "Pressione a tecla…"
                btn.isFocusableInTouchMode = true
                btn.requestFocus()
                btn.setOnKeyListener { _, _, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        selectedKeyCode = event.keyCode
                        refreshKeyLabel(btn)
                        true
                    } else false
                }
            }
            return btn
        }

        fun buildMouseDetail(): View {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val options = listOf(MouseButtons.LEFT to "Esquerdo", MouseButtons.RIGHT to "Direito", MouseButtons.MIDDLE to "Meio")
            options.forEach { (code, label) ->
                row.addView(
                    OverlayStyle.pillButton(context, label, OverlayStyle.NEON_BLUE, selectedKeyCode == code).apply {
                        setOnClickListener {
                            selectedKeyCode = code
                            inputDetail.removeAllViews()
                            inputDetail.addView(buildMouseDetail())
                        }
                        (layoutParams as? LinearLayout.LayoutParams)?.rightMargin = dp(6)
                    }
                )
            }
            return row
        }

        fun showKeyboardMode() {
            inputDetail.removeAllViews()
            inputDetail.addView(buildKeyboardDetail())
        }
        fun showMouseMode() {
            inputDetail.removeAllViews()
            inputDetail.addView(buildMouseDetail())
        }

        keyboardModeBtn.apply {
            text = "Teclado"
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(OverlayStyle.NEON_PURPLE)
            setOnClickListener { showKeyboardMode() }
        }
        mouseModeBtn.apply {
            text = "Mouse"
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(OverlayStyle.NEON_PURPLE)
            setOnClickListener { showMouseMode() }
        }
        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(keyboardModeBtn)
            addView(mouseModeBtn)
        }
        root.addView(sectionLabel("Tecla ou clique do mouse"))
        root.addView(modeRow)
        root.addView(inputDetail)
        if (isMouseInitially) showMouseMode() else showKeyboardMode()

        root.addView(sectionLabel("Tamanho do botão"))
        val sizeSeek = SeekBar(context).apply {
            max = 12
            progress = (((binding.sizePercent - 0.6f) / 0.1f).roundToInt()).coerceIn(0, 12)
        }
        root.addView(sizeSeek)

        if (!isNew) {
            val deleteBtn = TextView(context).apply {
                text = "Excluir botão"
                setTextColor(Color.parseColor("#FF4F64"))
                setPadding(0, dp(10), 0, dp(4))
            }
            root.addView(deleteBtn)
            deleteBtn.setOnClickListener {
                bindings.removeAll { it.id == binding.id }
                dirty = true
                if (selectedBindingId == binding.id) clearSelection()
                renderChips()
            }
        }

        val scroll = android.widget.ScrollView(context).apply { addView(root) }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (isNew) "Novo botão" else "Editar botão")
            .setView(scroll)
            .setPositiveButton("Pronto") { _, _ ->
                val index = bindings.indexOfFirst { it.id == binding.id }
                if (index >= 0) {
                    val sizePercent = 0.6f + sizeSeek.progress * 0.1f
                    val gestureType = (0 until gestureGroup.childCount)
                        .map { gestureGroup.getChildAt(it) as RadioButton }
                        .firstOrNull { it.isChecked }
                        ?.tag as? GestureType ?: GestureType.TAP
                    val newLabel = labelField.text.toString().ifBlank { binding.label }
                    bindings[index] = bindings[index].copy(
                        label = newLabel,
                        keyCode = selectedKeyCode,
                        gestureType = gestureType.name,
                        sizePercent = sizePercent
                    )
                    dirty = true
                    renderChips()
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                if (isNew) {
                    bindings.removeAll { it.id == binding.id }
                    renderChips()
                }
            }
            .create()
        showOverlayDialog(dialog)
    }

    // ---------------------------------------------------------------------
    // Configurações do mouse (dentro do jogo, junto da edição de keymaps)
    // ---------------------------------------------------------------------

    /** Chamado pela barra "Mouse" ou, ao entrar pelo Menu, automaticamente pelo Service. */
    fun openMouseSettingsDialog() {
        val pad = dp(18)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(sectionLabel("Sensibilidade do mouse"))
        val sensLabel = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f }
        var sensValue = callbacks.getMouseSensitivity()
        fun refreshSensLabel() { sensLabel.text = formatDecimal(sensValue, 2) }
        refreshSensLabel()
        root.addView(sensLabel)
        val sensSeek = SeekBar(context).apply {
            max = 190 // 1.00 a 20.00 em passos de 0,1
            progress = (((sensValue - 1f) / 0.1f).roundToInt()).coerceIn(0, 190)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    sensValue = 1f + progress * 0.1f
                    refreshSensLabel()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        root.addView(sensSeek)

        root.addView(sectionLabel("Taxa de resposta (polling rate)"))
        root.addView(TextView(context).apply {
            text = "O Android não permite alterar de fato a taxa de resposta de um mouse USB/Bluetooth; " +
                "fica salva só como referência, sem efeito simulado."
            setTextColor(Color.parseColor("#A0A0C0"))
            textSize = 11f
            setPadding(0, 0, 0, dp(6))
        })
        val pollingGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val currentPolling = callbacks.getPollingRate()
        PollingRate.values().forEach { rate ->
            pollingGroup.addView(RadioButton(context).apply {
                text = rate.label
                id = View.generateViewId()
                isChecked = rate == currentPolling
                tag = rate
            })
        }
        root.addView(pollingGroup)

        val scroll = android.widget.ScrollView(context).apply { addView(root) }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Configurações do mouse")
            .setView(scroll)
            .setPositiveButton("Pronto") { _, _ ->
                callbacks.setMouseSensitivity(sensValue)
                val chosen = (0 until pollingGroup.childCount)
                    .map { pollingGroup.getChildAt(it) as RadioButton }
                    .firstOrNull { it.isChecked }
                    ?.tag as? PollingRate
                if (chosen != null) callbacks.setPollingRate(chosen)
                toast("Configurações do mouse salvas")
            }
            .setNegativeButton("Cancelar", null)
            .create()
        showOverlayDialog(dialog)
    }

    // ---------------------------------------------------------------------
    // Controle livre de câmera ("Suspender mouse"): velocidade horizontal/vertical
    // ---------------------------------------------------------------------

    private fun openFreeLookSpeedDialog() {
        val pad = dp(18)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(sectionLabel("Controle livre de câmera"))
        root.addView(TextView(context).apply {
            text = "Com o mouse suspenso (botão verde), mova para qualquer direção para olhar livremente. " +
                "Ajuste a velocidade abaixo (0 a 1000, aceita vírgula)."
            setTextColor(Color.parseColor("#A0A0C0"))
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        })

        root.addView(sectionLabel("Velocidade horizontal da câmera"))
        val hField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(formatDecimal(callbacks.getFreeLookSpeedX(), 1))
            hint = "0 a 1000"
        }
        root.addView(hField)

        root.addView(sectionLabel("Velocidade vertical da puxada"))
        val vField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(formatDecimal(callbacks.getFreeLookSpeedY(), 1))
            hint = "0 a 1000"
        }
        root.addView(vField)

        val scroll = android.widget.ScrollView(context).apply { addView(root) }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Suspender mouse — câmera livre")
            .setView(scroll)
            .setPositiveButton("Aplicar") { _, _ ->
                parseDecimal(hField.text.toString())?.let { callbacks.setFreeLookSpeedX(it.coerceIn(0f, 1000f)) }
                parseDecimal(vField.text.toString())?.let { callbacks.setFreeLookSpeedY(it.coerceIn(0f, 1000f)) }
                toast("Velocidade da câmera livre atualizada")
            }
            .setNegativeButton("Fechar", null)
            .create()
        showOverlayDialog(dialog)
    }

    /** Aceita "," ou "." como separador decimal ao digitar. */
    private fun parseDecimal(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()

    /** Formata com vírgula (padrão brasileiro), sem casas decimais desnecessárias. */
    private fun formatDecimal(value: Float, decimals: Int): String {
        val formatted = String.format(Locale.US, "%.${decimals}f", value)
        val trimmed = if (formatted.contains('.')) formatted.trimEnd('0').trimEnd('.') else formatted
        return (trimmed.ifEmpty { "0" }).replace('.', ',')
    }

    private fun sectionLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#A0A0C0"))
        setPadding(0, dp(12), 0, dp(4))
        textSize = 12f
    }

    private fun keyLabelFor(code: Int): String =
        MouseButtons.labelFor(code) ?: KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    private fun showOverlayDialog(dialog: AlertDialog) {
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Lista atual (com as edições em memória) — usada pelo Service para persistir no "Salvar". */
    fun currentBindings(): List<KeyBindingEntity> = bindings.toList()

    fun hasUnsavedChanges(): Boolean = dirty

    fun markSaved() {
        dirty = false
    }
}
