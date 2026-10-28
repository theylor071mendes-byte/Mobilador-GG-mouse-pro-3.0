package com.mobiladorprox.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Construtores de visual compartilhados pelos overlays nativos (painel
 * flutuante da bola e editor de keymaps dentro do jogo), para manter o
 * mesmo tema neon roxo/azul em toda a UI que roda fora da Activity, com
 * botões leves em formato de pílula.
 */
object OverlayStyle {
    val NEON_PURPLE: Int = Color.parseColor("#FF1E42") // vermelho neon principal
    val NEON_BLUE: Int = Color.parseColor("#FF5C72") // vermelho neon claro (realces)
    val NEON_GREEN: Int = Color.parseColor("#39FF6A") // indicador de "ativo" (convenção universal, fora da paleta de marca)
    val DANGER_RED: Int = Color.parseColor("#FF4F64")
    val PANEL_BG: Int = Color.parseColor("#D9140A0C")
    val CHIP_IDLE_BG: Int = Color.parseColor("#2A1416")

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun roundedBackground(context: Context, color: Int, radiusDp: Int = 16): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(color)
        }

    /** Botão em formato de pílula: preenchido (ação principal) ou contornado (estado/secundário). */
    fun pillButton(context: Context, label: String, accent: Int, filled: Boolean): TextView {
        val view = TextView(context)
        view.text = label
        view.setTextColor(if (filled) Color.WHITE else accent)
        view.textSize = 12.5f
        view.setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9))
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 24).toFloat()
            setColor(if (filled) accent else CHIP_IDLE_BG)
            setStroke(dp(context, 1), accent)
        }
        return view
    }
}
