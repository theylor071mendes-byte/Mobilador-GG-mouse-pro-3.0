package com.mendes.mousepro.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import com.mendes.mousepro.R
import com.mendes.mousepro.service.OverlayService

class MainActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0D0D0D.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
            setBackgroundColor(0xFF0D0D0D.toInt())
        }
        scroll.addView(root)

        // ── Logo / Header ─────────────────────────────────────────────────
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            background = getDrawable(R.drawable.card_bg)
        }

        headerCard.addView(TextView(this).apply {
            text = "🖱"
            textSize = 48f
            gravity = Gravity.CENTER
        })

        headerCard.addView(TextView(this).apply {
            text = "MENDES MOUSE PRO"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF3333.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, 0)
        })

        headerCard.addView(TextView(this).apply {
            text = "v1.1.0  •  Mapeador de Mouse e Teclado"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(4), 0, 0)
        })

        root.addView(headerCard, marginParams(0, 0, 0, dp(20)))

        // ── Status card ───────────────────────────────────────────────────
        val statusCard = buildStatusCard()
        root.addView(statusCard, marginParams(0, 0, 0, dp(16)))

        // ── Botões principais ─────────────────────────────────────────────
        root.addView(sectionLabel("CONTROLES"))

        root.addView(redButton("▶  Iniciar Painel Flutuante") {
            if (!Settings.canDrawOverlays(this)) {
                toast("Primeiro conceda a permissão de sobreposição")
            } else {
                startService(Intent(this, OverlayService::class.java))
                toast("Painel flutuante ativado!")
            }
        }, marginParams(0, dp(8), 0, dp(10)))

        root.addView(outlineButton("■  Parar Painel") {
            stopService(Intent(this, OverlayService::class.java))
            toast("Painel encerrado")
        }, marginParams(0, 0, 0, dp(20)))

        // ── Permissões ────────────────────────────────────────────────────
        root.addView(sectionLabel("PERMISSÕES"))

        root.addView(buildPermissionCard(
            "Sobreposição de Tela",
            "Obrigatória para exibir o painel sobre o jogo",
            { Settings.canDrawOverlays(this) }
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, marginParams(0, dp(8), 0, dp(10)))

        // ── Info ──────────────────────────────────────────────────────────
        root.addView(sectionLabel("COMO USAR"))

        root.addView(buildInfoCard(), marginParams(0, dp(8), 0, dp(20)))

        // ── Rodapé ────────────────────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "Mendes Mouse Pro  •  Para uso pessoal"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF444444.toInt())
        })

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        // Recria a tela para atualizar status de permissões
        recreate()
    }

    // ── Helpers de UI ────────────────────────────────────────────────────────

    private fun buildStatusCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = getDrawable(R.drawable.card_bg)
        }

        card.addView(TextView(this).apply {
            text = "STATUS"
            textSize = 11f
            setTextColor(0xFF888888.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        })

        val overlayOk = Settings.canDrawOverlays(this)
        card.addView(statusRow("Permissão de Sobreposição", overlayOk))
        card.addView(statusRow("Serviço de Overlay", OverlayService.isRunning))

        return card
    }

    private fun statusRow(label: String, ok: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)

            addView(TextView(this@MainActivity).apply {
                text = if (ok) "●" else "○"
                textSize = 14f
                setTextColor(if (ok) 0xFF00CC44.toInt() else 0xFFCC3333.toInt())
                minWidth = dp(24)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                setTextColor(0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = if (ok) "OK" else "Pendente"
                textSize = 12f
                setTextColor(if (ok) 0xFF00CC44.toInt() else 0xFFCC3333.toInt())
            })
        }
    }

    private fun buildPermissionCard(
        title: String,
        description: String,
        check: () -> Boolean,
        action: () -> Unit
    ): LinearLayout {
        val ok = check()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(14), dp(14))
            background = getDrawable(R.drawable.card_bg)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(0xFFEEEEEE.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        textCol.addView(TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(textCol)

        if (!ok) {
            card.addView(TextView(this).apply {
                text = "Conceder →"
                textSize = 13f
                setTextColor(0xFFFF3333.toInt())
                setPadding(dp(12), 0, 0, 0)
                setOnClickListener { action() }
            })
        } else {
            card.addView(TextView(this).apply {
                text = "✓"
                textSize = 18f
                setTextColor(0xFF00CC44.toInt())
                setPadding(dp(12), 0, 0, 0)
            })
        }

        return card
    }

    private fun buildInfoCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = getDrawable(R.drawable.card_bg)
        }

        val steps = listOf(
            "1" to "Conceda a permissão de sobreposição de tela",
            "2" to "Toque em \"Iniciar Painel Flutuante\"",
            "3" to "Abra o jogo desejado",
            "4" to "A bolinha vermelha aparece sobre o jogo — toque para abrir o painel",
            "5" to "Conecte o mouse e o teclado via OTG ou Bluetooth",
            "6" to "Use os botões do painel para ligar/desligar recursos"
        )

        steps.forEach { (num, text) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(5), 0, dp(5))
            }
            row.addView(TextView(this).apply {
                this.text = num
                textSize = 13f
                setTextColor(0xFFFF3333.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                minWidth = dp(24)
                gravity = Gravity.CENTER_HORIZONTAL
            })
            row.addView(TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(0xFFCCCCCC.toInt())
                setPadding(dp(8), 0, 0, 0)
            })
            card.addView(row)
        }

        return card
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(0xFF666666.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        letterSpacing = 0.12f
    }

    private fun redButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        background = getDrawable(R.drawable.btn_red)
        setPadding(0, dp(16), 0, dp(16))
        setOnClickListener { onClick() }
    }

    private fun outlineButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(0xFFFF3333.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        background = getDrawable(R.drawable.btn_outline)
        setPadding(0, dp(14), 0, dp(14))
        setOnClickListener { onClick() }
    }

    private fun marginParams(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(l, t, r, b)
        }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
