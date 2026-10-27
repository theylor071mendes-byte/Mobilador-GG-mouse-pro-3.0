package com.mobiladorprox.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.mobiladorprox.app.MainActivity
import com.mobiladorprox.app.MobiladorApplication
import com.mobiladorprox.app.R
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Serviço em primeiro plano responsável pelo painel flutuante (bola
 * arrastável + mini-painel) e pelo editor de keymaps que roda direto em
 * cima do jogo selecionado ([EditOverlayView]), com o jogo real visível
 * atrás. Também captura o mouse ("Suspender mouse") e converte o
 * movimento em gestos de câmera, acumulados por frame para suavidade.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var handleView: View? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var captureView: PointerCaptureView? = null

    private var editOverlayView: EditOverlayView? = null
    private var editOverlayParams: WindowManager.LayoutParams? = null
    private var wasMapperActiveBeforeEdit = false

    // Suavização exponencial aplicada ao movimento bruto do mouse antes de
    // virar gesto de câmera (ver PerformanceProfile.lookResponsiveness).
    private var smoothedDx = 0f
    private var smoothedDy = 0f

    // Acumula os deltas brutos do mouse e só despacha o gesto de câmera uma
    // vez por frame (via Choreographer), em vez de a cada evento bruto do
    // ponteiro — reduz a fila de gestos da Acessibilidade e deixa o
    // movimento da câmera mais suave e com menos latência percebida.
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var frameFlushScheduled = false
    private val frameCallback = Choreographer.FrameCallback {
        frameFlushScheduled = false
        flushPointerDelta()
    }

    private val repository get() = (application as MobiladorApplication).repository
    private val preferences get() = (application as MobiladorApplication).preferences

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Permissão de sobreposição não concedida; encerrando serviço.")
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        addFloatingHandle()
        loadGlobalSettings()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1L)?.takeIf { it > 0 }
        val startInEditMode = intent?.getBooleanExtra(EXTRA_START_EDIT_MODE, false) ?: false
        if (profileId != null) {
            loadProfileIntoState(profileId, activate = !startInEditMode, onLoaded = {
                if (startInEditMode) enterEditMode()
            })
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (frameFlushScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameFlushScheduled = false
        }
        removeEditOverlay()
        removeCaptureWindow()
        removePanel()
        handleView?.let { safeRemoveView(it) }
        handleView = null
        MapperState.isMapperActive = false
        serviceScope.cancel()
        isRunning = false
    }

    // ---------------------------------------------------------------------
    // Notificação em primeiro plano
    // ---------------------------------------------------------------------

    private fun startForegroundNotification() {
        val channelId = "mobilador_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, contentIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ---------------------------------------------------------------------
    // Botão flutuante arrastável
    // ---------------------------------------------------------------------

    private fun addFloatingHandle() {
        if (handleView != null) return

        val size = dpToPx(56)
        val handle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(COLOR_NEON_PURPLE, COLOR_NEON_BLUE)
                orientation = GradientDrawable.Orientation.TL_BR
            }
        }
        val label = TextView(this).apply {
            text = "MX"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        handle.addView(
            label,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX)
                    val dy = (event.rawY - initialTouchY)
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = initialX + dx.roundToInt()
                    params.y = initialY + dy.roundToInt()
                    safeUpdateView(handle, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(handle, params)
        handleView = handle
        handleParams = params
    }

    // ---------------------------------------------------------------------
    // Mini-painel de controles
    // ---------------------------------------------------------------------

    private fun togglePanel() {
        if (panelView != null) removePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(18).toFloat()
                setColor(OverlayStyle.PANEL_BG)
                setStroke(dpToPx(1), COLOR_NEON_PURPLE)
            }
        }

        // Atalho principal ao tocar na bola: editar os keymaps dentro do próprio jogo.
        container.addView(simpleButton("✎ Editar Keymaps (no jogo)") { enterEditMode() })
        container.addView(spacerView())
        container.addView(simpleButton("Configurações do mouse") { openScreenInApp(Routes.MOUSE_SETTINGS) })
        container.addView(spacerView())

        container.addView(panelButton(
            labelOn = "Mapeador: ATIVO",
            labelOff = "Mapeador: INATIVO",
            isOn = { MapperState.isMapperActive },
            onToggle = { MapperState.isMapperActive = !MapperState.isMapperActive }
        ))
        container.addView(spacerView())
        container.addView(panelButton(
            labelOn = "Suspender mouse: ATIVO",
            labelOff = "Suspender mouse",
            isOn = { MapperState.isCameraCaptureActive },
            onToggle = { toggleCameraCapture() }
        ))
        container.addView(spacerView())
        container.addView(simpleButton("Fechar painel") { removePanel() })
        container.addView(simpleButton("Encerrar mapeador") { stopSelf() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (handleParams?.x ?: 24) + dpToPx(64)
            y = handleParams?.y ?: 300
        }

        windowManager.addView(container, params)
        panelView = container
    }

    private fun removePanel() {
        panelView?.let { safeRemoveView(it) }
        panelView = null
    }

    private fun spacerView(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6))
    }

    private fun panelButton(labelOn: String, labelOff: String, isOn: () -> Boolean, onToggle: () -> Unit): TextView {
        val button = OverlayStyle.pillButton(this, if (isOn()) labelOn else labelOff, COLOR_NEON_BLUE, isOn())
        fun refresh() {
            button.text = if (isOn()) labelOn else labelOff
            val bg = button.background as? GradientDrawable
            bg?.setColor(if (isOn()) COLOR_NEON_BLUE else OverlayStyle.CHIP_IDLE_BG)
            button.setTextColor(if (isOn()) Color.WHITE else COLOR_NEON_BLUE)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        button.layoutParams = lp
        button.gravity = Gravity.CENTER
        button.setOnClickListener {
            onToggle()
            refresh()
        }
        return button
    }

    private fun simpleButton(label: String, action: () -> Unit): TextView {
        val button = OverlayStyle.pillButton(this, label, COLOR_NEON_PURPLE, false)
        button.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        button.gravity = Gravity.CENTER
        button.setOnClickListener { action() }
        return button
    }

    // ---------------------------------------------------------------------
    // Editor de keymaps dentro do jogo (overlay nativo sobre a tela real)
    // ---------------------------------------------------------------------

    private fun enterEditMode() {
        if (editOverlayView != null) return
        val profileId = MapperState.activeProfileId
        if (profileId == null) {
            openScreenInApp(Routes.GAME_PICKER)
            return
        }
        removePanel()

        wasMapperActiveBeforeEdit = MapperState.isMapperActive
        MapperState.isMapperActive = false

        val view = EditOverlayView(this, object : EditOverlayView.Callbacks {
            override fun onSave(bindings: List<KeyBindingEntity>) {
                serviceScope.launch {
                    repository.replaceBindings(profileId, bindings)
                    val fresh = repository.getProfile(profileId)
                    if (fresh != null) MapperState.loadProfile(fresh, repository.getBindingsOnce(profileId))
                }
            }

            override fun onExit() {
                removeEditOverlay()
            }

            override fun onToggleMouseCapture() {
                toggleCameraCapture()
            }

            override fun isMouseCaptureActive(): Boolean = MapperState.isCameraCaptureActive

            override fun isAccessibilityReady(): Boolean = MapperAccessibilityService.isRunning
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        runCatching {
            windowManager.addView(view, params)
            editOverlayView = view
            editOverlayParams = params
            handleView?.visibility = View.GONE
        }.onFailure {
            Log.e(TAG, "Falha ao abrir o editor de keymaps dentro do jogo", it)
        }

        serviceScope.launch {
            val bindings = repository.getBindingsOnce(profileId)
            editOverlayView?.setBindings(bindings)
        }
    }

    private fun removeEditOverlay() {
        editOverlayView?.let { safeRemoveView(it) }
        editOverlayView = null
        editOverlayParams = null
        handleView?.visibility = View.VISIBLE
        MapperState.isMapperActive = wasMapperActiveBeforeEdit
    }

    // ---------------------------------------------------------------------
    // Atalhos para abrir o app a partir do painel flutuante
    // ---------------------------------------------------------------------

    private fun openScreenInApp(route: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, route)
        }
        runCatching { startActivity(intent) }
    }

    // ---------------------------------------------------------------------
    // Perfil ativo e preferências globais
    // ---------------------------------------------------------------------

    private fun loadProfileIntoState(profileId: Long, activate: Boolean, onLoaded: () -> Unit = {}) {
        serviceScope.launch {
            val profile = repository.getProfile(profileId) ?: return@launch
            val bindings = repository.getBindingsOnce(profileId)
            MapperState.loadProfile(profile, bindings)
            if (activate) MapperState.isMapperActive = true
            onLoaded()
        }
    }

    private fun loadGlobalSettings() {
        serviceScope.launch {
            MapperState.mouseSensitivity = preferences.mouseSensitivity.first()
            MapperState.lookResponsiveness = preferences.performanceProfile.first().lookResponsiveness
        }
    }

    // ---------------------------------------------------------------------
    // Captura do mouse (controle de câmera)
    // ---------------------------------------------------------------------

    private fun toggleCameraCapture() {
        if (MapperState.isCameraCaptureActive) {
            removeCaptureWindow()
        } else {
            addCaptureWindow()
        }
    }

    private fun addCaptureWindow() {
        if (captureView != null) return
        if (!MapperAccessibilityService.isRunning) {
            Log.w(TAG, "Serviço de Acessibilidade inativo; ative-o antes de usar a câmera.")
            return
        }

        smoothedDx = 0f
        smoothedDy = 0f
        val view = PointerCaptureView(
            this,
            onDelta = { dx, dy -> onPointerDelta(dx, dy) },
            onButton = { code, pressed -> MapperAccessibilityService.instance?.handleMouseButton(code, pressed) }
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            windowManager.addView(view, params)
            captureView = view
            MapperState.isCameraCaptureActive = true
        }.onFailure {
            Log.e(TAG, "Falha ao ativar a captura do mouse", it)
        }
    }

    private fun removeCaptureWindow() {
        captureView?.let {
            runCatching { it.releasePointerCapture() }
            safeRemoveView(it)
        }
        captureView = null
        MapperState.isCameraCaptureActive = false
    }

    /**
     * Acumula o movimento bruto do mouse recebido da captura de ponteiro.
     * Em vez de despachar um gesto a cada evento (que pode chegar em
     * frequência muito maior que a taxa de atualização da tela, sobrecarregando
     * a fila de gestos da Acessibilidade), agenda um único flush no próximo
     * frame (Choreographer) — menor latência percebida e câmera mais suave.
     */
    private fun onPointerDelta(rawDx: Float, rawDy: Float) {
        pendingDx += rawDx
        pendingDy += rawDy
        if (!frameFlushScheduled) {
            frameFlushScheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    /**
     * Aplica sensibilidade global e suavização exponencial (média móvel) ao
     * movimento acumulado do mouse antes de virar gesto de câmera. A
     * suavização reduz o quanto um tremor da mão é percebido no movimento
     * gerado — não elimina qualquer tremor do próprio jogo ou da mira em si.
     */
    private fun flushPointerDelta() {
        val rawDx = pendingDx
        val rawDy = pendingDy
        pendingDx = 0f
        pendingDy = 0f
        if (rawDx == 0f && rawDy == 0f) return

        val alpha = MapperState.lookResponsiveness.coerceIn(0.05f, 1f)
        smoothedDx += (rawDx - smoothedDx) * alpha
        smoothedDy += (rawDy - smoothedDy) * alpha

        val binding = MapperState.cameraBinding
        val centerX = binding?.xPercent ?: 0.6f
        val centerY = binding?.yPercent ?: 0.45f
        val sensitivity = MapperState.mouseSensitivity / 5f // 5.0 é o valor neutro (~1x)
        val scaledDx = smoothedDx * sensitivity
        val scaledDy = smoothedDy * sensitivity
        MapperAccessibilityService.instance?.dispatchLookSwipe(centerX, centerY, scaledDx, scaledDy)
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    private fun safeRemoveView(view: View) {
        runCatching { windowManager.removeView(view) }
    }

    private fun safeUpdateView(view: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "MobiladorOverlay"
        private const val NOTIFICATION_ID = 7421
        const val ACTION_STOP = "com.mobiladorprox.app.action.STOP_OVERLAY"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_START_EDIT_MODE = "extra_start_edit_mode"

        private val COLOR_NEON_PURPLE = Color.parseColor("#9D4DFF")
        private val COLOR_NEON_BLUE = Color.parseColor("#00E5FF")

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
