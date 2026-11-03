package com.mendes.mousepro
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
class MapperAccessibilityService:AccessibilityService(){
 override fun onAccessibilityEvent(event:AccessibilityEvent?){}
 override fun onInterrupt(){}
 override fun onKeyEvent(event:KeyEvent):Boolean{
  if(event.action!=KeyEvent.ACTION_DOWN||event.repeatCount>0)return false
  val m=MappingStore.all(this).firstOrNull{it.keyCode==event.keyCode}?:return false
  val d=DisplayMetrics()
  @Suppress("DEPRECATION")
  (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(d)
  val p=Path().apply{moveTo(d.widthPixels*m.xPercent/100f,d.heightPixels*m.yPercent/100f)}
  dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,70)).build(),null,null)
  return true
 }
}
