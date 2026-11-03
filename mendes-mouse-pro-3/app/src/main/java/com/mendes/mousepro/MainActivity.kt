package com.mendes.mousepro
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.*
class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=1; setPadding(24,30,24,24); setBackgroundColor(Color.rgb(13,13,16)) }
  root.addView(TextView(this).apply { text="MENDES MOUSE PRO 3"; textSize=25f; gravity=Gravity.CENTER; setTextColor(Color.WHITE) })
  root.addView(TextView(this).apply { text="Mapeador Android • versão 3.0"; gravity=Gravity.CENTER; setTextColor(Color.LTGRAY); setPadding(0,8,0,24) })
  add(root,"ATIVAR ACESSIBILIDADE") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
  add(root,"PERMITIR SOBREPOSIÇÃO") { if(!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
  add(root,"INICIAR PAINEL FLUTUANTE") { if(Settings.canDrawOverlays(this)) startService(Intent(this,FloatingOverlayService::class.java)) else Toast.makeText(this,"Ative a sobreposição primeiro",Toast.LENGTH_LONG).show() }
  add(root,"EDITOR DE MAPEAMENTO") { startActivity(Intent(this,MappingEditorActivity::class.java)) }
  root.addView(TextView(this).apply { text="Base inicial com APIs públicas de Acessibilidade. Não injeta código em jogos nem contorna anti-cheat. Compatibilidade varia por jogo."; setTextColor(Color.LTGRAY); textSize=12f; setPadding(0,20,0,0) })
  setContentView(ScrollView(this).apply { addView(root) })
 }
 private fun add(root:LinearLayout,label:String,action:()->Unit) { root.addView(Button(this).apply { text=label; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(210,28,48)); setOnClickListener{action()} },LinearLayout.LayoutParams(-1,-2).apply{topMargin=12}) }
}
