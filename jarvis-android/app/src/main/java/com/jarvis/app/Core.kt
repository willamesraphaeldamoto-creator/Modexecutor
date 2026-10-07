package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

object Jarvis{
 suspend fun handle(c:Context,text:String):String{val r=Rules.match(text);val say:String;val acts:JSONArray;if(r!=null){say=r.first;acts=r.second}else{val p=Brain.parse(Brain.ask(c,text));say=p.first;acts=p.second};Brain.remember(text,say);return(say+Actions.run(c,acts)).trim().ifBlank{"Feito."}}
}
object Rules{
 private fun a(type:String,vararg kv:Pair<String,Any>)=JSONObject().put("type",type).also{o->kv.forEach{o.put(it.first,it.second)}};private fun arr(vararg x:JSONObject)=JSONArray().also{y->x.forEach{y.put(it)}}
 fun match(raw:String):Pair<String,JSONArray>?{
  val t=raw.lowercase().trim();fun has(vararg s:String)=s.any{t.contains(it)}
  if(t in setOf("oi","olá","ola","bom dia","boa tarde","boa noite"))return"Olá. Como posso ajudar?" to JSONArray()
  if(has("voltar"))return"" to arr(a("back"));if(has("ir para casa","tela inicial","home"))return"" to arr(a("home"));if(has("aplicativos recentes","recentes"))return"" to arr(a("recents"));if(has("notificações","notificacoes"))return"" to arr(a("notifications"))
  if(has("lanterna","flash")&&!has("desligar"))return"Lanterna." to arr(a("flash","on" to true));if(has("desligar lanterna"))return"Lanterna desligada." to arr(a("flash","on" to false))
  if(has("aumentar volume","sobe o volume","volume mais"))return"" to arr(a("volume","dir" to "up"));if(has("diminuir volume","abaixar volume","volume menos"))return"" to arr(a("volume","dir" to "down"))
  if(has("próxima música","proxima musica","próxima faixa"))return"" to arr(a("media","key" to "next"));if(has("música anterior","musica anterior"))return"" to arr(a("media","key" to "prev"))
  Regex("""(?:abra|abrir|abre|inicie|iniciar)s+(?:os+|as+)?(.+)""").find(t)?.let{return"Abrindo." to arr(a("open_app","app" to it.groupValues[1]))}
  Regex("""(?:pesquise|pesquisar|pesquisa|buscar|busque)s+(?:pors+|sobres+)?(.+)""").find(t)?.let{return"Pesquisando." to arr(a("search","q" to it.groupValues[1]))}
  Regex("""(?:clique|clicar|toque|tocar)s+(?:ems+)?(.+)""").find(t)?.let{return"Clicando." to arr(a("click","text" to it.groupValues[1]))}
  Regex("""(?:digite|escreva)s+(.+)""").find(t)?.let{return"Digitando." to arr(a("type","text" to it.groupValues[1]))}
  if(has("role para baixo","rolar para baixo","desça a tela"))return"" to arr(a("swipe","down" to true));if(has("role para cima","rolar para cima","suba a tela"))return"" to arr(a("swipe","down" to false))
  Regex("""(?:abra o site|abrir site|acesse)s+(.+)""").find(t)?.let{val x=it.groupValues[1];val u=if(x.startsWith("http"))x else "https://"+x;return"Abrindo o site." to arr(a("url","url" to u))}
  Regex("""(?:crie|criar|gere|gerar)s+(?:ums+)?sites+(.+)""").find(t)?.let{return"Vou preparar um site web para você." to arr(a("make_site","prompt" to it.groupValues[1]))}
  Regex("""(?:abra|abrir)s+(?:as+)?lojas+(.+)""").find(t)?.let{return"Abrindo a loja para procurar o app." to arr(a("store","q" to it.groupValues[1]))}
  return null
 }
}
object Actions{
 private const val NEED=" Ative a acessibilidade do Jarvis nas configurações."
 private fun norm(s:String)=Normalizer.normalize(s.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").trim()
 private fun go(c:Context,i:Intent){(JarvisA11yService.inst?:c).startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
 suspend fun run(c:Context,acts:JSONArray):String{var out="";for(i in 0 until acts.length()){val a=acts.optJSONObject(i)?:continue;out+=try{exec(c,a)}catch(_:Exception){" Falhou."};delay(400)};return out}
 private fun g(s:JarvisA11yService?,a:Int)=if(s==null)NEED else{s.performGlobalAction(a);""}
 private suspend fun exec(c:Context,a:JSONObject):String{
  val s=JarvisA11yService.inst
  return when(a.optString("type")){
   "back"->g(s,AccessibilityService.GLOBAL_ACTION_BACK);"home"->g(s,AccessibilityService.GLOBAL_ACTION_HOME);"recents"->g(s,AccessibilityService.GLOBAL_ACTION_RECENTS);"notifications"->g(s,AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
   "open_app"->openApp(c,a.optString("app"));"search"->{go(c,Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(a.optString("q")))));""}
   "url"->{go(c,Intent(Intent.ACTION_VIEW,Uri.parse(a.optString("url"))));""}
   "store"->{go(c,Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/search?q="+Uri.encode(a.optString("q"))+"&c=apps")));""}
   "click"->{if(s==null)NEED else if(s.clickText(a.optString("text")))" " else" Não encontrei o botão."}
   "type"->{if(s==null)NEED else if(s.typeText(a.optString("text")))" " else" Não encontrei um campo de texto."}
   "swipe"->{if(s==null)NEED else{s.swipe(a.optBoolean("down",true));""}}
   "make_site"->makeSite(c,a.optString("prompt"))
   "flash"->{flash(c,a.optBoolean("on",true));""}
   "volume"->{val am=c.getSystemService(Context.AUDIO_SERVICE) as AudioManager;val d=if(a.optString("dir")=="up")AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER;repeat(3){am.adjustStreamVolume(AudioManager.STREAM_MUSIC,d,AudioManager.FLAG_SHOW_UI)};""}
   "media"->{val k=when(a.optString("key")){"next"->KeyEvent.KEYCODE_MEDIA_NEXT;"prev"->KeyEvent.KEYCODE_MEDIA_PREVIOUS;else->KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE};val am=c.getSystemService(Context.AUDIO_SERVICE) as AudioManager;am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,k));am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,k));""}
   else->""
  }
 }
 private fun openApp(c:Context,name:String):String{val n=norm(name);val pm=c.packageManager;val list=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);val hit=list.firstOrNull{norm(it.loadLabel(pm).toString()).contains(n)}?:return" Não achei o app "+name+".";go(c,pm.getLaunchIntentForPackage(hit.activityInfo.packageName)?:return" Não consegui abrir.");return""}
 private fun flash(c:Context,on:Boolean){val cm=c.getSystemService(Context.CAMERA_SERVICE) as CameraManager;val id=cm.cameraIdList.firstOrNull{cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true}?:return;cm.setTorchMode(id,on)}
 private fun makeSite(c:Context,prompt:String):String{val dir=java.io.File(c.filesDir,"sites").apply{mkdirs()};val f=java.io.File(dir,"site.html");val safe=prompt.replace("<","&lt;").replace(">","&gt;");f.writeText("<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font-family:sans-serif;background:#0b0b0f;color:white;padding:28px'><h1>Site criado pelo JARVIS</h1><p>"+safe+"</p><button>Começar</button></body></html>");go(c,Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file://"+f.absolutePath),"text/html").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));return" Site criado no armazenamento do Jarvis."}
}