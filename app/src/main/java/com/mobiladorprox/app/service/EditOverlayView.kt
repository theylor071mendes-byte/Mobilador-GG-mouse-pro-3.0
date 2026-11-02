package com.mobiladorprox.app.service

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType
import com.mobiladorprox.app.data.model.MouseButtons
import com.mobiladorprox.app.data.model.PollingRate
import kotlin.math.roundToInt

/**
 * Overlay nativo (sem Compose) exibido sobre o jogo de verdade: o jogo
 * continua visível atrás e esta camada transparente mostra os botões virtuais
 * para editar ao vivo — arrastar para reposicionar, toque simples para
 * selecionar (X de exclusão rápida), toque duplo para configurar.
 *
 * Toolbar reorganizada em 2 linhas para caber sem scroll em qualquer tela.
 * Botão "Câmera" com feedback de cor + rótulo dinâmico. SeekBar para
 * velocidade da câmera em vez de campo numérico.
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

        fun getFreeLookSpeedX(): Float
        fun setFreeLookSpeedX(value: Float)
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

    private var selectedBindingId: Long? = null
    private var deleteBadge: TextView? = null

    private val chipLayer = FrameLayout(context)
    private lateinit var toolbarContainer: LinearLayout
    private lateinit var cameraBtn: TextView
    private val reopenChip: TextView

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!previewMode) addComponent(e.x, e.y)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (selectedBindingId != null) clearSelection()
            return true
        }
    })

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(chipLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        chipLayer.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }

        toolbarContainer = buildToolbar()
        addView(
            toolbarContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(18)
                leftMargin = dp(12)
                rightMargin = dp(12)
            }
        )

        reopenChip = OverlayStyle.pillButton(context, "✎ Editar", OverlayStyle.NEON_BLUE, true).apply {
            visibility = View.GONE
            OverlayStyle.setSafeClick(this) { setPreviewMode(false) }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Toolbar em 2 linhas — sem scroll, sempre visível
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildToolbar(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(OverlayStyle.PANEL_BG)
                setStroke(dp(1), OverlayStyle.NEON_PURPLE)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        fun row() = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun space() = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6), 1)
        }

        // Linha 1: Sair | Salvar | Visualização | + Componente
        val row1 = row()
        row1.addView(OverlayStyle.pillButton(context, "← Sair", OverlayStyle.NEON_PURPLE, false).apply {
            OverlayStyle.setSafeClick(this) { confirmExitIfNeeded() }
        })
        row1.addView(space())
        row1.addView(OverlayStyle.pillButton(context, "✔ Salvar", OverlayStyle.NEON_BLUE, true).apply {
            OverlayStyle.setSafeClick(this) { saveNow() }
        })
        row1.addView(space())
        row1.addView(OverlayStyle.pillButton(context, "👁 Preview", OverlayStyle.NEON_PURPLE, false).apply {
            OverlayStyle.setSafeClick(this) { setPreviewMode(true) }
        })
        row1.addView(space())
        row1.addView(OverlayStyle.pillButton(context, "+ Adicionar", OverlayStyle.NEON_PURPLE, true).apply {
            OverlayStyle.setSafeClick(this) { addComponent(null, null) }
        })
        container.addView(row1)

        // Separador fino
        container.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(6); bottomMargin = dp(6)
            }
            setBackgroundColor(Color.WHITE.and(0x22FFFFFF.toInt()))
        })

        // Linha 2: Câmera (toggle com feedback visual) | ⚙ Vel. câmera | Mouse
        val row2 = row()
        cameraBtn = buildCameraButton()
        row2.addView(cameraBtn)
        row2.addView(space())
        row2.addView(OverlayStyle.pillButton(context, "⚙ Câmera", OverlayStyle.NEON_GREEN, false).apply {
            OverlayStyle.setSafeClick(this) { openFreeLookSpeedDialog() }
        })
        row2.addView(space())
        row2.addView(OverlayStyle.pillButton(context, "🖱 Mouse", OverlayStyle.NEON_PURPLE, false).apply {
            OverlayStyle.setSafeClick(this) { openMouseSettingsDialog() }
        })
        container.addView(row2)

        return container
    }

    private fun buildCameraButton(): TextView {
        val active = callbacks.isMouseCaptureActive()
        return OverlayStyle.pillButton(
            context,
            if (active) "● Câmera ATIVA" else "○ Câmera livre",
            OverlayStyle.NEON_GREEN,
            active
        ).apply {
            OverlayStyle.setSafeClick(this) {
                if (!callbacks.isAccessibilityReady()) {
                    toast("Ative o Serviço de Acessibilidade para usar a câmera")
                } else {
                    callbacks.onToggleMouseCapture()
                    refreshCameraButton()
                }
            }
        }
    }

    fun refreshCameraButton() {
        val active = callbacks.isMouseCaptureActive()
        cameraBtn.text = if (active) "● Câmera ATIVA" else "○ Câmera livre"
        val bg = cameraBtn.background as? GradientDrawable
        bg?.setColor(if (active) OverlayStyle.NEON_GREEN else OverlayStyle.CHIP_IDLE_BG)
        bg?.setStroke(dp(1), OverlayStyle.NEON_GREEN)
        cameraBtn.setTextColor(if (active) Color.BLACK else OverlayStyle.NEON_GREEN)
    }

    // Mantido para compatibilidade com chamadas externas (OverlayService)
    fun refreshMouseToggle() = refreshCameraButton()

    private fun setPreviewMode(preview: Boolean) {
        previewMode = preview
        clearSelection()
        toolbarContainer.visibility = if (preview) View.GONE else View.VISIBLE
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
        toast("Configuração salva ✔")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chips (botões virtuais)
    // ─────────────────────────────────────────────────────────────────────────

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

    private fun componentSizePx(binding: KeyBindingEntity): Int {
        val base = if (binding.gestureType == GestureType.ANALOG_STICK.name) 96 else 40
        // Range expandido: 0.4 a 3.0 (era 0.6 a 1.8)
        return dp((base * binding.sizePercent.coerceIn(0.4f, 3.0f)).roundToInt())
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
        val isCamera = binding.gestureType == GestureType.LOOK_TOGGLE.name
        val chip: View = if (isAnalog) {
            AnalogStickView(context).apply { dim = previewMode }
        } else {
            TextView(context).apply {
                text = if (isCamera) "👁" else binding.label.take(2).uppercase()
                setTextColor(if (isCamera) Color.BLACK else Color.WHITE)
                gravity = Gravity.CENTER
                textSize = if (previewMode) 9f else 11f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isCamera) OverlayStyle.NEON_GREEN else OverlayStyle.NEON_PURPLE)
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
     * Visual do analógico virtual (WASD): cruz com ponto direcional em cada
     * extremidade ao redor de um centro, sobre base translúcida.
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
            val cx = w / 2f; val cy = h / 2f
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
        private var downX = 0f; private var downY = 0f
        private var startLeft = 0; private var startTop = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startLeft = lp.leftMargin; startTop = lp.topMargin
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

    // ─────────────────────────────────────────────────────────────────────────
    // Seleção + X de exclusão rápida
    // ─────────────────────────────────────────────────────────────────────────

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
        val badgeSize = dp(24)
        val badge = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 13f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(OverlayStyle.DANGER_RED)
                setStroke(dp(1), Color.WHITE)
            }
            OverlayStyle.setSafeClick(this) {
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

    // ─────────────────────────────────────────────────────────────────────────
    // Diálogo de edição do botão
    // ─────────────────────────────────────────────────────────────────────────

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
        root.addView(sectionLabel("Nome do botão"))
        root.addView(labelField)

        val gestureGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val gestureOptions = listOf(
            GestureType.TAP to "Toque (clique rápido)",
            GestureType.HOLD to "Segurar (mantém pressionado)",
            GestureType.LOOK_TOGGLE to "Visualização de câmera (liga/desliga câmera livre)",
            GestureType.ANALOG_STICK to "Analógico (WASD → movimento do personagem)"
        )
        val defaultLabelFor = mapOf(
            GestureType.LOOK_TOGGLE to "Câmera",
            GestureType.ANALOG_STICK to "Analógico"
        )
        val currentGesture = runCatching { GestureType.valueOf(binding.gestureType) }.getOrNull() ?: GestureType.TAP
        gestureOptions.forEach { (type, label) ->
            gestureGroup.addView(RadioButton(context).apply {
                text = label
                id = View.generateViewId()
                isChecked = type == currentGesture
                tag = type
            })
        }
        root.addView(sectionLabel("Tipo de gesto"))
        root.addView(gestureGroup)

        fun currentGestureType(): GestureType =
            (0 until gestureGroup.childCount)
                .map { gestureGroup.getChildAt(it) as RadioButton }
                .firstOrNull { it.isChecked }
                ?.tag as? GestureType ?: GestureType.TAP

        // ── Seção de velocidade da câmera (SeekBar) ──────────────────────────
        var cameraSpeedXSeek: SeekBar? = null
        var cameraSpeedYSeek: SeekBar? = null
        var cameraSpeedXLabel: TextView? = null
        var cameraSpeedYLabel: TextView? = null
        val cameraSpeedSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun renderCameraSpeedSection() {
            cameraSpeedSection.removeAllViews()
            cameraSpeedXSeek = null; cameraSpeedYSeek = null
            cameraSpeedXLabel = null; cameraSpeedYLabel = null
            if (currentGestureType() != GestureType.LOOK_TOGGLE) return

            fun makeSpeedRow(labelText: String, initialValue: Float): Pair<TextView, SeekBar> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, 0)
                }
                val valueLabel = TextView(context).apply {
                    setTextColor(OverlayStyle.NEON_BLUE)
                    textSize = 13f
                    text = initialValue.roundToInt().toString()
                    minWidth = dp(42)
                    gravity = Gravity.END
                }
                val seek = SeekBar(context).apply {
                    max = 1000
                    progress = initialValue.roundToInt().coerceIn(0, 1000)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                            valueLabel.text = p.toString()
                        }
                        override fun onStartTrackingTouch(sb: SeekBar) {}
                        override fun onStopTrackingTouch(sb: SeekBar) {}
                    })
                }
                row.addView(seek)
                row.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
                row.addView(valueLabel)
                cameraSpeedSection.addView(sectionLabel(labelText))
                cameraSpeedSection.addView(row)
                return valueLabel to seek
            }

            val (lx, sx) = makeSpeedRow(
                "Velocidade horizontal (0–1000)",
                binding.cameraSpeedX ?: callbacks.getFreeLookSpeedX()
            )
            cameraSpeedXLabel = lx; cameraSpeedXSeek = sx

            val (ly, sy) = makeSpeedRow(
                "Velocidade vertical (0–1000)",
                binding.cameraSpeedY ?: callbacks.getFreeLookSpeedY()
            )
            cameraSpeedYLabel = ly; cameraSpeedYSeek = sy

            cameraSpeedSection.addView(TextView(context).apply {
                text = "A tecla/botão abaixo liga e desliga a câmera livre. " +
                    "Clique do mouse só funciona enquanto a câmera estiver ativa " +
                    "(limitação do Android)."
                setTextColor(Color.parseColor("#A0A0C0"))
                textSize = 11f
                setPadding(0, dp(6), 0, dp(4))
            })
        }
        renderCameraSpeedSection()
        root.addView(cameraSpeedSection)

        // Auto-label ao trocar tipo de gesto em novo botão
        var lastAutoLabel: String? = if (isNew) defaultLabelFor[currentGesture] else null
        gestureGroup.setOnCheckedChangeListener { _, _ ->
            renderCameraSpeedSection()
            if (!isNew) return@setOnCheckedChangeListener
            val type = currentGestureType()
            val suggestion = defaultLabelFor[type]
            val current = labelField.text.toString()
            when {
                suggestion != null && (current.isBlank() || current == "Novo" || current == lastAutoLabel) -> {
                    labelField.setText(suggestion)
                    lastAutoLabel = suggestion
                }
                suggestion == null && current == lastAutoLabel -> {
                    labelField.setText("Novo")
                    lastAutoLabel = null
                }
            }
        }

        // ── Seleção de tecla ou botão do mouse ───────────────────────────────
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
            OverlayStyle.setSafeClick(btn) {
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
                        OverlayStyle.setSafeClick(this) {
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

        fun showKeyboardMode() { inputDetail.removeAllViews(); inputDetail.addView(buildKeyboardDetail()) }
        fun showMouseMode() { inputDetail.removeAllViews(); inputDetail.addView(buildMouseDetail()) }

        keyboardModeBtn.apply {
            text = "⌨ Teclado"
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(OverlayStyle.NEON_PURPLE)
            OverlayStyle.setSafeClick(this) { showKeyboardMode() }
        }
        mouseModeBtn.apply {
            text = "🖱 Mouse"
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(OverlayStyle.NEON_PURPLE)
            OverlayStyle.setSafeClick(this) { showMouseMode() }
        }
        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(keyboardModeBtn)
            addView(mouseModeBtn)
        }
        root.addView(sectionLabel("Tecla ou botão do mouse"))
        root.addView(modeRow)
        root.addView(inputDetail)
        if (isMouseInitially) showMouseMode() else showKeyboardMode()

        // ── Tamanho do botão — SeekBar com range expandido ───────────────────
        root.addView(sectionLabel("Tamanho do componente"))
        val sizeValueLabel = TextView(context).apply {
            setTextColor(OverlayStyle.NEON_BLUE)
            textSize = 13f
        }
        // Range: 0.4 a 3.0 em passos de 0.1 → max = 26
        val sizeSeek = SeekBar(context).apply {
            max = 26
            val initialProgress = (((binding.sizePercent - 0.4f) / 0.1f).roundToInt()).coerceIn(0, 26)
            progress = initialProgress
            sizeValueLabel.text = "%.1f×".format(0.4f + initialProgress * 0.1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    sizeValueLabel.text = "%.1f×".format(0.4f + p * 0.1f)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        val sizeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sizeSeek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
            addView(sizeValueLabel)
        }
        root.addView(sizeRow)

        // Botão excluir (só em edições de botões existentes)
        if (!isNew) {
            val deleteBtn = TextView(context).apply {
                text = "🗑 Excluir este botão"
                setTextColor(Color.parseColor("#FF4F64"))
                setPadding(0, dp(10), 0, dp(4))
            }
            root.addView(deleteBtn)
            OverlayStyle.setSafeClick(deleteBtn) {
                bindings.removeAll { it.id == binding.id }
                dirty = true
                if (selectedBindingId == binding.id) clearSelection()
                renderChips()
            }
        }

        val scroll = android.widget.ScrollView(context).apply { addView(root) }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (isNew) "Novo componente" else "Editar componente")
            .setView(scroll)
            .setPositiveButton("✔ Pronto") { _, _ ->
                val index = bindings.indexOfFirst { it.id == binding.id }
                if (index >= 0) {
                    val sizePercent = 0.4f + sizeSeek.progress * 0.1f
                    val gestureType = currentGestureType()
                    val newLabel = labelField.text.toString().ifBlank { binding.label }
                    val cameraSpeedX = if (gestureType == GestureType.LOOK_TOGGLE) {
                        cameraSpeedXSeek?.progress?.toFloat()?.coerceIn(0f, 1000f)
                    } else null
                    val cameraSpeedY = if (gestureType == GestureType.LOOK_TOGGLE) {
                        cameraSpeedYSeek?.progress?.toFloat()?.coerceIn(0f, 1000f)
                    } else null
                    bindings[index] = bindings[index].copy(
                        label = newLabel,
                        keyCode = selectedKeyCode,
                        gestureType = gestureType.name,
                        sizePercent = sizePercent,
                        cameraSpeedX = cameraSpeedX,
                        cameraSpeedY = cameraSpeedY
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

    // ─────────────────────────────────────────────────────────────────────────
    // Configurações do mouse
    // ─────────────────────────────────────────────────────────────────────────

    fun openMouseSettingsDialog() {
        val pad = dp(18)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(sectionLabel("Sensibilidade do mouse"))
        val sensLabel = TextView(context).apply { setTextColor(OverlayStyle.NEON_BLUE); textSize = 16f }
        var sensValue = callbacks.getMouseSensitivity()
        fun refreshSensLabel() { sensLabel.text = "%.1f×".format(sensValue) }
        refreshSensLabel()
        root.addView(sensLabel)
        val sensRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sensSeek = SeekBar(context).apply {
            max = 190
            progress = (((sensValue - 1f) / 0.1f).roundToInt()).coerceIn(0, 190)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    sensValue = 1f + progress * 0.1f
                    refreshSensLabel()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        sensRow.addView(sensSeek)
        root.addView(sensRow)

        root.addView(sectionLabel("Taxa de resposta (referência)"))
        root.addView(TextView(context).apply {
            text = "O Android não permite alterar a taxa do hardware; fica salvo como referência."
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
            .setTitle("🖱 Configurações do mouse")
            .setView(scroll)
            .setPositiveButton("✔ Aplicar") { _, _ ->
                callbacks.setMouseSensitivity(sensValue)
                val chosen = (0 until pollingGroup.childCount)
                    .map { pollingGroup.getChildAt(it) as RadioButton }
                    .firstOrNull { it.isChecked }
                    ?.tag as? PollingRate
                if (chosen != null) callbacks.setPollingRate(chosen)
                toast("Configurações do mouse salvas")
            }
            .setNegativeButton("Fechar", null)
            .create()
        showOverlayDialog(dialog)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Velocidade da câmera livre (global) — SeekBar
    // ─────────────────────────────────────────────────────────────────────────

    private fun openFreeLookSpeedDialog() {
        val pad = dp(18)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(sectionLabel("Câmera livre — velocidade global"))
        root.addView(TextView(context).apply {
            text = "Configuração padrão usada por todos os componentes que não têm velocidade própria. " +
                "Com o mouse suspenso, mova-o para qualquer direção para olhar livremente."
            setTextColor(Color.parseColor("#A0A0C0"))
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        })

        fun makeRow(labelText: String, initialValue: Float): Pair<TextView, SeekBar> {
            val valueLabel = TextView(context).apply {
                setTextColor(OverlayStyle.NEON_BLUE); textSize = 14f
                text = initialValue.roundToInt().toString()
                minWidth = dp(42); gravity = Gravity.END
            }
            val seek = SeekBar(context).apply {
                max = 1000
                progress = initialValue.roundToInt().coerceIn(0, 1000)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { valueLabel.text = p.toString() }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(seek); addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) }); addView(valueLabel)
            }
            root.addView(sectionLabel(labelText)); root.addView(row)
            return valueLabel to seek
        }

        val (_, hSeek) = makeRow("Velocidade horizontal (0–1000)", callbacks.getFreeLookSpeedX())
        val (_, vSeek) = makeRow("Velocidade vertical / puxada (0–1000)", callbacks.getFreeLookSpeedY())

        val scroll = android.widget.ScrollView(context).apply { addView(root) }
        val dialog = AlertDialog.Builder(context)
            .setTitle("⚙ Câmera livre — velocidade")
            .setView(scroll)
            .setPositiveButton("✔ Aplicar") { _, _ ->
                callbacks.setFreeLookSpeedX(hSeek.progress.toFloat())
                callbacks.setFreeLookSpeedY(vSeek.progress.toFloat())
                toast("Velocidade da câmera atualizada")
            }
            .setNegativeButton("Fechar", null)
            .create()
        showOverlayDialog(dialog)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitários
    // ─────────────────────────────────────────────────────────────────────────

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

    fun currentBindings(): List<KeyBindingEntity> = bindings.toList()
    fun hasUnsavedChanges(): Boolean = dirty
    fun markSaved() { dirty = false }
}
