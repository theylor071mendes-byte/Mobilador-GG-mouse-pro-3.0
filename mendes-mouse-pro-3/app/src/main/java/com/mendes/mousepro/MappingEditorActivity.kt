package com.mendes.mousepro

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.KeyEvent
import android.widget.*

class MappingEditorActivity : Activity() {

    private val values = mutableListOf<KeyMapping>()
    private lateinit var fieldKey: EditText
    private lateinit var fieldX: EditText
    private lateinit var fieldY: EditText
    private lateinit var outputView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        values.addAll(MappingStore.all(this))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            setBackgroundColor(Color.rgb(15, 15, 18))
        }

        fieldKey = makeField("Código da tecla (ex.: espaço = 62)")
        fieldX   = makeField("X em % da tela (0–100)")
        fieldY   = makeField("Y em % da tela (0–100)")
        root.addView(fieldKey)
        root.addView(fieldX)
        root.addView(fieldY)

        root.addView(Button(this).apply {
            text = "SALVAR MAPEAMENTO"
            setOnClickListener {
                val a = fieldKey.text.toString().toIntOrNull()
                val b = fieldX.text.toString().toFloatOrNull()
                val c = fieldY.text.toString().toFloatOrNull()
                if (a == null || b == null || c == null || b !in 0f..100f || c !in 0f..100f) {
                    Toast.makeText(this@MappingEditorActivity, "Valores inválidos", Toast.LENGTH_SHORT).show()
                } else {
                    values.removeAll { it.keyCode == a }
                    values.add(KeyMapping(a, b, c))
                    MappingStore.save(this@MappingEditorActivity, values)
                    refresh()
                }
            }
        })

        outputView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
        }
        root.addView(outputView)

        root.addView(Button(this).apply {
            text = "APAGAR TODOS"
            setOnClickListener {
                values.clear()
                MappingStore.save(this@MappingEditorActivity, values)
                refresh()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    private fun makeField(hint: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
    }

    private fun refresh() {
        outputView.text = if (values.isEmpty()) "Nenhum mapeamento"
        else values.joinToString("\n") {
            "${KeyEvent.keyCodeToString(it.keyCode)} → X ${it.xPercent}% / Y ${it.yPercent}%"
        }
    }
}
