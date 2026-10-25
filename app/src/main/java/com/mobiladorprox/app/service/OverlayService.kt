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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Serviço em primeiro plano responsável pelo painel flutuante: o botão que
 * pode ser arrastado pela tela, o mini-painel de controles (Mapeador,
 * Câmera, Modo edição) e as marcações arrastáveis usadas para calibrar a
 * posição de cada botão do jogo na tela do aparelho.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var handleView: View? = null
    private var handleParams: WindowManager.LayoutParams? = null

    private var panelView: View? = null
    private var captureView: PointerCaptureView? = null
    private val markerViews = mutableListOf<View>()

    private var editModeActive = false

    private val repository get() = (application as MobiladorApplication).repository

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
        if (profileId != null) {
            loadProfileIntoState(profileId, activate = true)
        }

        if (intent?.getBooleanExtra(EXTRA_START_EDIT_MODE, false) == true) {
            enterEditMode()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeCaptureWindow()
        removeMarkerViews()
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
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#1A1A26"))
                setStroke(dpToPx(1), COLOR_NEON_PURPLE)
            }
        }

        container.addView(panelButton(
            labelOn = "Mapeador: ATIVO",
            labelOff = "Mapeador: INATIVO",
            isOn = { MapperState.isMapperActive },
            onToggle = {
                MapperState.isMapperActive = !MapperState.isMapperActive
            }
        ))
        container.addView(panelButton(
            labelOn = "Câmera (mouse): ATIVA",
            labelOff = "Câmera (mouse): INATIVA",
            isOn = { MapperState.isCameraCaptureActive },
            onToggle = { toggleCameraCapture() }
        ))
        container.addView(panelButton(
            labelOn = "Modo edição: ATIVO",
            labelOff = "Modo edição: INATIVO",
            isOn = { editModeActive },
            onToggle = { toggleEditMode() }
        ))
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

    private fun panelButton(labelOn: String, labelOff: String, isOn: () -> Boolean, onToggle: () -> Unit): TextView {
        val button = TextView(this)
        fun refresh() {
            button.text = if (isOn()) labelOn else labelOff
            button.setBackgroundColor(if (isOn()) COLOR_NEON_BLUE else Color.parseColor("#2A2A3A"))
        }
        refresh()
        button.setTextColor(Color.WHITE)
        button.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dpToPx(8)
        button.layoutParams = lp
        button.setOnClickListener {
            onToggle()
            refresh()
        }
        return button
    }

    private fun simpleButton(label: String, action: () -> Unit): TextView {
        val button = TextView(this)
        button.text = label
        button.setTextColor(Color.WHITE)
        button.setBackgroundColor(Color.parseColor("#2A2A3A"))
        button.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dpToPx(4)
        button.layoutParams = lp
        button.setOnClickListener { action() }
        return button
    }

    // ---------------------------------------------------------------------
    // Perfil ativo
    // ---------------------------------------------------------------------

    private fun loadProfileIntoState(profileId: Long, activate: Boolean) {
        serviceScope.launch {
            val profile = repository.getProfile(profileId) ?: return@launch
            val bindings = repository.getBindingsOnce(profileId)
            MapperState.loadProfile(profile, bindings)
            if (activate) MapperState.isMapperActive = true
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

        val view = PointerCaptureView(this) { dx, dy ->
            onPointerDelta(dx, dy)
        }
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

    private fun onPointerDelta(rawDx: Float, rawDy: Float) {
        val binding = MapperState.cameraBinding
        val centerX = binding?.xPercent ?: 0.6f
        val centerY = binding?.yPercent ?: 0.45f
        val scaledDx = rawDx * MapperState.sensitivityHorizontal * MapperState.dpiMultiplier
        val scaledDy = rawDy * MapperState.sensitivityVertical * MapperState.dpiMultiplier
        MapperAccessibilityService.instance?.dispatchLookSwipe(centerX, centerY, scaledDx, scaledDy)
    }

    // ---------------------------------------------------------------------
    // Modo de edição (calibrar posição dos botões)
    // ---------------------------------------------------------------------

    private fun toggleEditMode() {
        if (editModeActive) enterNonEditMode() else enterEditMode()
    }

    private fun enterEditMode() {
        val profileId = MapperState.activeProfileId ?: return
        editModeActive = true
        serviceScope.launch {
            val bindings = repository.getBindingsOnce(profileId)
            removeMarkerViews()
            bindings.forEach { addMarkerView(it) }
        }
    }

    private fun enterNonEditMode() {
        editModeActive = false
        removeMarkerViews()
    }

    private fun addMarkerView(binding: KeyBindingEntity) {
        val metrics = resources.displayMetrics
        val size = dpToPx(40)
        val marker = TextView(this).apply {
            text = binding.label.take(2).uppercase()
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC9D4DFF"))
                setStroke(dpToPx(2), Color.WHITE)
            }
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (binding.xPercent * metrics.widthPixels).roundToInt() - size / 2
            y = (binding.yPercent * metrics.heightPixels).roundToInt() - size / 2
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        marker.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    params.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    safeUpdateView(marker, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val newXPercent = ((params.x + size / 2).toFloat() / metrics.widthPixels).coerceIn(0f, 1f)
                    val newYPercent = ((params.y + size / 2).toFloat() / metrics.heightPixels).coerceIn(0f, 1f)
                    persistMarkerPosition(binding, newXPercent, newYPercent)
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager.addView(marker, params)
            markerViews.add(marker)
        }
    }

    private fun persistMarkerPosition(binding: KeyBindingEntity, xPercent: Float, yPercent: Float) {
        serviceScope.launch {
            repository.updateBinding(binding.copy(xPercent = xPercent, yPercent = yPercent))
            val profileId = MapperState.activeProfileId
            if (profileId != null) {
                val profile = repository.getProfile(profileId)
                val bindings = repository.getBindingsOnce(profileId)
                if (profile != null) MapperState.loadProfile(profile, bindings)
            }
        }
    }

    private fun removeMarkerViews() {
        markerViews.forEach { safeRemoveView(it) }
        markerViews.clear()
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
