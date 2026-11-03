package com.mendes.mousepro
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.KeyEvent
import android.widget.*
class MappingEditorActivity:Activity(){
 private val values=mutableListOf<KeyMapping>(); private lateinit var k:EditText; private lateinit var x:EditText; private lateinit var y:EditText; private lateinit var out:TextView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);values.addAll(MappingStore.all(this))
 val r=LinearLayout(this).apply{orientation=1;setPadding(20,24,20,20);setBackgroundColor(Color.rgb(15,15,18))}
 k=field("Código da tecla (ex.: espaço 62)");x=field("X em % da tela (0–100)");y=field("Y em % da tela (0–100)")
 r.addView(k);r.addView(x);r.addView(y)
 r.addView(Button(this).apply{text="SALVAR MAPEAMENTO";setOnClickListener{val a=k.text.toString().toIntOrNull();val b=x.text.toString().toFloatOrNull();val c=y.text.toString().toFloatOrNull();if(a==null||b==null||c==null||b !in 0f..100f||c !in 0f..100f)Toast.makeText(this@MappingEditorActivity,"Valores inválidos",Toast.LENGTH_SHORT).show() else{values.removeAll{it.keyCode==a};values.add(KeyMapping(a,b,c));MappingStore.save(this@MappingEditorActivity,values);refresh()}}})
 out=TextView(this).apply{setTextColor(Color.WHITE);setPadding(0,20,0,20)};r.addView(out)
 r.addView(Button(this).apply{text="APAGAR TODOS";setOnClickListener{values.clear();MappingStore.save(this@MappingEditorActivity,values);refresh()}})
 setContentView(ScrollView(this).apply{addView(r)});refresh()
 }
 private fun field(s:String)=EditText(this).apply{hint=s;setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);inputType=2}
 private fun refresh(){out.text=if(values.isEmpty())"Nenhum mapeamento" else values.joinToString("\n"){"${KeyEvent.keyCodeToString(it.keyCode)} -> X ${it.xPercent}% / Y ${it.yPercent}%"}}
}
