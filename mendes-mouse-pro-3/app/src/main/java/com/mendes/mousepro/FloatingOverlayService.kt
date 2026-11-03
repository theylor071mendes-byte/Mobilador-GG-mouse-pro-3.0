package com.mendes.mousepro
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.*
import android.widget.TextView
import kotlin.math.abs
class FloatingOverlayService:Service(){
 private var wm:WindowManager?=null;private var bubble:View?=null
 override fun onCreate(){super.onCreate();wm=getSystemService(WINDOW_SERVICE) as WindowManager
 val v=TextView(this).apply{text="M";textSize=20f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(Color.rgb(220,25,45));setStroke(3,Color.WHITE)}}
 val t=if(android.os.Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
 val p=WindowManager.LayoutParams(58,58,t,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=28;y=180}
 var sx=0;var sy=0;var tx=0f;var ty=0f
 v.setOnTouchListener{_,e->when(e.action){MotionEvent.ACTION_DOWN->{sx=p.x;sy=p.y;tx=e.rawX;ty=e.rawY;true};MotionEvent.ACTION_MOVE->{p.x=sx+(e.rawX-tx).toInt();p.y=sy+(e.rawY-ty).toInt();wm?.updateViewLayout(v,p);true};MotionEvent.ACTION_UP->{if(abs(e.rawX-tx)<8&&abs(e.rawY-ty)<8)startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true};else->false}}
 bubble=v;wm?.addView(v,p)
 }
 override fun onDestroy(){bubble?.let{wm?.removeView(it)};bubble=null;super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
