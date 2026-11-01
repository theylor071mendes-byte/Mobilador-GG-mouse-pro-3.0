package com.mendes.mousepro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mendes.mousepro.R
import com.mendes.mousepro.ui.MainActivity
import kotlin.math.abs

/**
 * Serviço em primeiro plano que exibe:
 *  - Bolinha arrastável (bubble) sobre qualquer app
 *  - Mini-painel com controles ao tocar na bolinha
 *  - Interceptação de movimento do mouse via onGenericMotionEvent
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null

    // Estado do mouse
    private var mouseCaptureActive = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    // Sensibilidade (ajustável pelo painel)
    var sensitivity: Float = 1.0f

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addBubble()
        isRunning = true
    }

    override fun onDestroy() {
        removePanel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        isRunning = false
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Bubble arrastável
    // ─────────────────────────────────────────────────────────────────────

    private fun addBubble() {
        if (bubble != null) return

        val view = buildBubble()
        val params = WindowManager.LayoutParams(
            dp(60), dp(60),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(200)
        }

        view.setOnTouchListener(BubbleDragListener())
        bubble = view
        bubbleParams = params
        runCatching { windowManager.addView(view, params) }
    }

    private fun buildBubble(): View {
        val container = FrameLayout(this).apply {
            // Fundo circular vermelho
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFDD2222.toInt())
                setStroke(dp(2), 0xFFFF5555.toInt())
            }
        }

        val label = TextView(this).apply {
            text = "M"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        container.addView(label, FrameLayout.LayoutParams(-1, -1))
        return container
    }

    private inner class BubbleDragListener : View.OnTouchListener {
        private var downRawX = 0f; private var downRawY = 0f
        private var startX = 0; private var startY = 0
        private var dragged = false

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            val p = bubbleParams ?: return false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX; downRawY = ev.rawY
                    startX = p.x; startY = p.y; dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downRawX).toInt()
                    val dy = (ev.rawY - downRawY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) dragged = true
                    p.x = (startX + dx).coerceAtLeast(0)
                    p.y = (startY + dy).coerceAtLeast(0)
                    runCatching { windowManager.updateViewLayout(v, p) }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) togglePanel()
                }
            }
            return true
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Painel flutuante
    // ─────────────────────────────────────────────────────────────────────

    private fun togglePanel() {
        if (panel != null) removePanel() else showPanel()
    }

    private fun showPanel() {
        if (panel != null) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(0xF0171717.toInt())
                setStroke(dp(1), 0xFF333333.toInt())
                cornerRadius = dp(16).toFloat()
            }
        }

        // Cabeçalho
        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))

            addView(TextView(this@OverlayService).apply {
                text = "🖱"
                textSize = 20f
                setPadding(0, 0, dp(8), 0)
            })
            addView(TextView(this@OverlayService).apply {
                text = "MENDES MOUSE PRO"
                textSize = 14f
                setTextColor(0xFFFF3333.toInt())
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@OverlayService).apply {
                text = "✕"
                textSize = 18f
                setTextColor(0xFF888888.toInt())
                setPadding(dp(8), 0, 0, 0)
                setOnClickListener { removePanel() }
            })
        })

        // Separador
        container.addView(View(this).apply {
            setBackgroundColor(0xFF2A2A2A.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply { bottomMargin = dp(14) }
        })

        // Status do mouse
        val mouseStatusLabel = TextView(this).apply {
            text = if (mouseCaptureActive) "● Mouse: ATIVO" else "○ Mouse: Inativo"
            textSize = 13f
            setTextColor(if (mouseCaptureActive) 0xFF00CC44.toInt() else 0xFF888888.toInt())
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(mouseStatusLabel)

        // Botão câmera
        val camBtn = buildPanelButton(
            if (mouseCaptureActive) "⏸  Desativar câmera" else "▶  Ativar câmera livre",
            if (mouseCaptureActive) 0xFF003311.toInt() else 0xFF330000.toInt(),
            if (mouseCaptureActive) 0xFF00CC44.toInt() else 0xFFFF3333.toInt()
        ) {
            mouseCaptureActive = !mouseCaptureActive
            mouseStatusLabel.text = if (mouseCaptureActive) "● Mouse: ATIVO" else "○ Mouse: Inativo"
            mouseStatusLabel.setTextColor(if (mouseCaptureActive) 0xFF00CC44.toInt() else 0xFF888888.toInt())
            toast(if (mouseCaptureActive) "Câmera livre ativada" else "Câmera livre desativada")
        }
        container.addView(camBtn, marginParams(b = dp(8)))

        // Sensibilidade rápida
        container.addView(buildSensRow())

        // Separador
        container.addView(View(this).apply {
            setBackgroundColor(0xFF2A2A2A.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                topMargin = dp(12); bottomMargin = dp(12)
            }
        })

        // Fechar serviço
        container.addView(buildPanelButton("■  Encerrar painel", 0xFF1A0000.toInt(), 0xFFFF3333.toInt()) {
            removePanel()
            stopSelf()
        })

        val params = WindowManager.LayoutParams(
            dp(260), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val bp = bubbleParams
            x = (bp?.x ?: dp(16)) + dp(70)
            y = bp?.y ?: dp(200)
        }

        panel = container
        runCatching { windowManager.addView(container, params) }
    }

    private fun buildSensRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(this@OverlayService).apply {
                text = "Sens:"
                textSize = 13f
                setTextColor(0xFF888888.toInt())
                minWidth = dp(48)
            })

            val levels = listOf("0.5×" to 0.5f, "1×" to 1.0f, "2×" to 2.0f, "3×" to 3.0f)
            levels.forEach { (label, value) ->
                addView(TextView(this@OverlayService).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    setTextColor(if (sensitivity == value) Color.WHITE else 0xFF888888.toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setColor(if (sensitivity == value) 0xFFCC0000.toInt() else 0xFF222222.toInt())
                        cornerRadius = dp(6).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) }
                    setOnClickListener {
                        sensitivity = value
                        // Fecha e reabre o painel para atualizar
                        removePanel(); showPanel()
                    }
                })
            }
        }
    }

    private fun buildPanelButton(label: String, bgColor: Int, textColor: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(bgColor)
                setStroke(1, textColor and 0x66FFFFFF.toInt())
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun removePanel() {
        val p = panel ?: return
        runCatching { windowManager.removeView(p) }
        panel = null
    }

    private fun marginParams(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(l, t, r, b) }

    // ─────────────────────────────────────────────────────────────────────
    // Notificação de primeiro plano
    // ─────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val NOTIF_ID = 9901
        private const val CHANNEL_ID = "mendes_mouse_overlay"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
