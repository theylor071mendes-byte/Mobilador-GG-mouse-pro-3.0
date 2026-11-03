package com.mendes.mousepro
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
data class KeyMapping(val keyCode:Int,val xPercent:Float,val yPercent:Float)
object MappingStore {
 private const val P="mendes_mapper"; private const val K="mappings"
 fun all(c:Context):List<KeyMapping>{ val a=JSONArray(c.getSharedPreferences(P,0).getString(K,"[]")?:"[]"); return (0 until a.length()).mapNotNull{i->a.optJSONObject(i)?.let{KeyMapping(it.optInt("key"),it.optDouble("x").toFloat(),it.optDouble("y").toFloat())}} }
 fun save(c:Context,v:List<KeyMapping>){val a=JSONArray();v.forEach{a.put(JSONObject().put("key",it.keyCode).put("x",it.xPercent).put("y",it.yPercent))};c.getSharedPreferences(P,0).edit().putString(K,a.toString()).apply()}
}
