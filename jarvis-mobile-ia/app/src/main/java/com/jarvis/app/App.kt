package com.jarvis.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.service.voice.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

object Store {
    const val KEY="or_key"; const val MODEL="or_model"; const val DEF_MODEL="openrouter/auto"
    private fun p(c:Context)=c.getSharedPreferences("jarvis",Context.MODE_PRIVATE)
    fun get(c:Context,k:String,d:String="")=p(c).getString(k,d)?:d
    fun set(c:Context,k:String,v:String)=p(c).edit().putString(k,v).apply()
}

object Brain {
    private val history=ArrayList<Pair<String,String>>()
    private const val SYSTEM="""Você é JARVIS, assistente pessoal Android. Responda em português do Brasil e seja objetivo.
Responda SOMENTE JSON: {"say":"texto","actions":[...]}.
Ações: open_app(app), click(text), type(text), scroll(dir), tap(x,y), back, home, recents, notifications, screenshot, lock, flash(on), volume(dir), call(who), whatsapp(to,text), search(q), url(url), alarm(h,m), timer(s), media(key), read_screen.
Para tarefas na tela, use a captura fornecida e retorne ações reais. Não diga que executou algo se não houver ação.
Se for conversa normal, actions=[].
"""
    fun remember(u:String,s:String){synchronized(history){history.add(u to s);while(history.size>10)history.removeAt(0)}}
    fun parse(raw:String):Pair<String,JSONArray>{
        val s=raw.replace(Regex("(?s)<think>.*?</think>"),"").trim();val i=s.indexOf('{');val j=s.lastIndexOf('}')
        if(i>=0&&j>i)try{val o=JSONObject(s.substring(i,j+1));return o.optString("say","") to (o.optJSONArray("actions")?:JSONArray())}catch(_:Exception){}
        return s.take(600) to JSONArray()
    }
    private fun online(c:Context):Boolean{val cm=c.getSystemService(ConnectivityManager::class.java);val n=cm.activeNetwork?:return false;return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}
    suspend fun ask(c:Context,user:String,screen:Boolean=false)=withContext(Dispatchers.IO){
        val key=Store.get(c,Store.KEY).trim()
        if(key.isBlank()) return@withContext JSONObject().put("say","Configure a chave do OpenRouter em Configurações.").put("actions",JSONArray()).toString()
        if(!online(c)) return@withContext JSONObject().put("say","Estou sem internet para acessar o OpenRouter.").put("actions",JSONArray()).toString()
        val models=Store.get(c,Store.MODEL,Store.DEF_MODEL).split(',').map{it.trim()}.filter{it.isNotBlank()}
        var err="OpenRouter não respondeu."
        for(m in models)try{return@withContext call(c,key,m,user,screen)}catch(e:Exception){err=e.message?:err}
        JSONObject().put("say","Falha no OpenRouter: "+err).put("actions",JSONArray()).toString()
    }
    private fun call(c:Context,key:String,model:String,user:String,screen:Boolean):String{
        val msgs=JSONArray().put(JSONObject().put("role","system").put("content",SYSTEM))
        synchronized(history){history.takeLast(8).forEach{msgs.put(JSONObject().put("role","user").put("content",it.first));msgs.put(JSONObject().put("role","assistant").put("content",it.second))}}
        val content=JSONArray().put(JSONObject().put("type","text").put("text",user))
        if(screen)ScreenShareService.latestBase64()?.let{content.put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,"+it)))}
        msgs.put(JSONObject().put("role","user").put("content",content))
        val body=JSONObject().put("model",model).put("messages",msgs).put("max_tokens",1400).put("temperature",0.25)
        val cn=URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        cn.requestMethod="POST";cn.connectTimeout=15000;cn.readTimeout=90000;cn.doOutput=true
        cn.setRequestProperty("Authorization","Bearer "+key);cn.setRequestProperty("Content-Type","application/json");cn.setRequestProperty("X-Title","JARVIS Mobile IA")
        cn.outputStream.use{it.write(body.toString().toByteArray())};val code=cn.responseCode;val stream=if(code in 200..299)cn.inputStream else cn.errorStream
        val txt=stream?.bufferedReader()?.use{it.readText()}?:"";if(code !in 200..299)throw Exception("HTTP "+code)
        return JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","")
    }
}

class Voice(c:Context){
    private var tts:TextToSpeech?=null;private var ready=false;private var pending:String?=null;private var done:(()->Unit)?=null
    init{tts=TextToSpeech(c.applicationContext){if(it==TextToSpeech.SUCCESS){tts?.language=Locale("pt","BR");tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){ } override fun onDone(id:String?){done?.let{Handler(Looper.getMainLooper()).post(it)};done=null} override fun onError(id:String?){done?.let{Handler(Looper.getMainLooper()).post(it)};done=null}});ready=true;pending?.let{say(it);pending=null}}}}
    fun say(s:String,onDone:(()->Unit)?=null){if(s.isBlank()){onDone?.invoke();return};if(!ready){pending=s;done=onDone;return};done=onDone;tts?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis")}
    fun shutdown(){tts?.stop();tts?.shutdown()}
}
class Listener(private val c:Context){
    private var sr:SpeechRecognizer?=null
    companion object{fun google(c:Context):ComponentName?{val x=ComponentName("com.google.android.googlequicksearchbox","com.google.android.voicesearch.serviceapi.GoogleRecognitionService");return try{c.packageManager.getServiceInfo(x,0);x}catch(_:Exception){null}}}
    private fun kill(){try{sr?.cancel();sr?.destroy()}catch(_:Exception){};sr=null}
    fun stop(){kill()}
    fun start(final:(String)->Unit,fail:(Int)->Unit,partial:((String)->Unit)?=null){
        kill();if(!SpeechRecognizer.isRecognitionAvailable(c)){fail(-1);return};val g=google(c);sr=if(g!=null)SpeechRecognizer.createSpeechRecognizer(c,g)else SpeechRecognizer.createSpeechRecognizer(c)
        val r=sr?:return;r.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(p:Bundle?){}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(v:Float){}
            override fun onBufferReceived(b:ByteArray?){}
            override fun onEndOfSpeech(){}
            override fun onEvent(t:Int,p:Bundle?){}
            override fun onError(e:Int){if(sr===r){kill();fail(e)}}
            override fun onResults(b:Bundle?){if(sr===r){kill();final(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:"")}}
            override fun onPartialResults(b:Bundle?){partial?.invoke(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:"")}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)})
    }
}

object Rules{
    private fun a(t:String,vararg p:Pair<String,Any>)=JSONObject().put("type",t).apply{p.forEach{put(it.first,it.second)}}
    private fun arr(vararg o:JSONObject)=JSONArray().apply{for(x in o)put(x)}
    fun match(raw:String):Pair<String,JSONArray>?{
        val t=raw.lowercase().trim();fun has(vararg x:String)=x.any{t.contains(it)}
        if(has("lanterna"))return(if(has("deslig","apaga"))"Lanterna desligada." else "Lanterna ligada.") to arr(a("flash","on" to !has("deslig","apaga")))
        if(has("volume"))return(if(has("aument","subir","mais","alto"))"Volume aumentado." else "Volume diminuído.") to arr(a("volume","dir" to if(has("aument","subir","mais","alto"))"up" else "down"))
        if(t=="voltar")return"Voltando." to arr(a("back"));if(t=="início"||t=="inicio"||t=="home")return"Tela inicial." to arr(a("home"))
        if(has("recentes"))return"Apps recentes." to arr(a("recents"));if(has("notificações","notificacoes"))return"Abrindo notificações." to arr(a("notifications"))
        if(has("ler a tela","o que tem na tela","ler tela"))return"" to arr(a("read_screen"))
        Regex("""^(?:abra|abrir|abre)\s+(.+)$""").find(t)?.let{return"Abrindo." to arr(a("open_app","app" to it.groupValues[1]))}
        Regex("""^(?:pesquise|pesquisa|buscar|procure)\s+(.+)$""").find(t)?.let{return"Pesquisando." to arr(a("search","q" to it.groupValues[1]))}
        return null
    }
}
object Actions{
    private const val NEED=" Ative a acessibilidade do JARVIS para controlar a tela."
    private fun go(c:Context,i:Intent){(JarvisA11yService.inst?:c).startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    suspend fun run(c:Context,a:JSONArray):String{
        var x="";for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;x+=exec(c,o);delay(350)};return x
    }
    private fun global(s:JarvisA11yService?,a:Int)=if(s==null)NEED else{s.performGlobalAction(a);""}
    private suspend fun exec(c:Context,o:JSONObject):String{
        val s=JarvisA11yService.inst
        return when(o.optString("type")){
            "back"->global(s,AccessibilityService.GLOBAL_ACTION_BACK)
            "home"->global(s,AccessibilityService.GLOBAL_ACTION_HOME)
            "recents"->global(s,AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications"->global(s,AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "screenshot"->global(s,AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            "lock"->global(s,AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            "click"->if(s==null)NEED else if(s.clickText(o.optString("text")))" " else " Não achei o texto na tela."
            "tap"->if(s==null)NEED else if(s.tap(o.optDouble("x").toFloat(),o.optDouble("y").toFloat()))" " else " Não consegui tocar."
            "type"->if(s==null)NEED else if(s.typeText(o.optString("text")))" " else " Não achei o campo de texto."
            "scroll"->{if(s==null)NEED else{s.swipe(o.optString("dir")!="up");""}}
            "read_screen"->if(s==null)NEED else " Na tela: "+s.screenText().take(450)
            "open_app"->openApp(c,o.optString("app"))
            "search"->{go(c,Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(o.optString("q")))));""}
            "url"->{go(c,Intent(Intent.ACTION_VIEW,Uri.parse(o.optString("url"))));""}
            "flash"->{val cm=c.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager;val id=cm.cameraIdList.firstOrNull()?:return@exec " Sem lanterna.";cm.setTorchMode(id,o.optBoolean("on",true));""}
            "volume"->{val am=c.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager;am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,if(o.optString("dir")=="up")1 else -1,android.media.AudioManager.FLAG_SHOW_UI);""}
            "media"->{val k=when(o.optString("key")){"next"->KeyEvent.KEYCODE_MEDIA_NEXT;"prev"->KeyEvent.KEYCODE_MEDIA_PREVIOUS;else->KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE};val am=c.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager;am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,k));am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,k));""}
            "alarm"->{go(c,Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,o.optInt("h")).putExtra(AlarmClock.EXTRA_MINUTES,o.optInt("m")));""}
            "timer"->{go(c,Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,o.optInt("s")));""}
            else->""
        }
    }
    private fun openApp(c:Context,n:String):String{
        val q=n.lowercase().trim();val pm=c.packageManager;val list=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
        val hit=list.firstOrNull{it.loadLabel(pm).toString().lowercase().contains(q)}?:return " Não achei o app "+n+"."
        go(c,pm.getLaunchIntentForPackage(hit.activityInfo.packageName)?:return " Não consegui abrir.")
        return ""
    }
}
object Jarvis{
    suspend fun handle(c:Context,t:String,screen:Boolean=false):String{
        val r=Rules.match(t);val p=if(r!=null)r else Brain.parse(Brain.ask(c,t,screen));Brain.remember(t,p.first);return (p.first+Actions.run(c,p.second)).trim().ifBlank{"Feito."}
    }
}

class JarvisA11yService:AccessibilityService(){
    companion object{var inst:JarvisA11yService?=null}
    override fun onServiceConnected(){inst=this};override fun onUnbind(i:Intent?):Boolean{inst=null;return super.onUnbind(i)};override fun onAccessibilityEvent(e:AccessibilityEvent?){};override fun onInterrupt(){}
    private fun find(n:AccessibilityNodeInfo?,t:String):AccessibilityNodeInfo?{if(n==null)return null;val a=n.text?.toString()?.lowercase()?:"";val d=n.contentDescription?.toString()?.lowercase()?:"";if(a.contains(t)||d.contains(t))return n;for(i in 0 until n.childCount)find(n.getChild(i),t)?.let{return it};return null}
    fun clickText(t:String):Boolean{val n=find(rootInActiveWindow,t.lowercase())?:return false;var x:AccessibilityNodeInfo?=n;while(x!=null){if(x.isClickable)return x.performAction(AccessibilityNodeInfo.ACTION_CLICK);x=x.parent};val r=Rect();n.getBoundsInScreen(r);return tap(r.centerX().toFloat(),r.centerY().toFloat())}
    fun typeText(t:String):Boolean{val r=rootInActiveWindow?:return false;val n=r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?:editable(r)?:return false;return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,t)})}
    private fun editable(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(n.isEditable)return n;for(i in 0 until n.childCount)editable(n.getChild(i))?.let{return it};return null}
    fun tap(x:Float,y:Float)=dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(Path().apply{moveTo(x,y)},0,60)).build(),null,null)
    fun swipe(down:Boolean){val m=resources.displayMetrics;val x=m.widthPixels/2f;val y1=m.heightPixels*(if(down).72f else .28f);val y2=m.heightPixels*(if(down).28f else .72f);dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(Path().apply{moveTo(x,y1);lineTo(x,y2)},0,350)).build(),null,null)}
    fun screenText():String{val s=StringBuilder();fun w(n:AccessibilityNodeInfo?,d:Int){if(n==null||d>25||s.length>700)return;n.text?.let{s.append(it).append(". ")};for(i in 0 until n.childCount)w(n.getChild(i),d+1)};w(rootInActiveWindow,0);return s.toString()}
}

class WakeService:Service(){
    private lateinit var l:Listener;private lateinit var v:Voice;private val h=Handler(Looper.getMainLooper())
    companion object{@Volatile var paused=false;var inst:WakeService?=null;private val words=listOf("jarvis","jarves","jarvez","jarbis");fun pause(){paused=true;inst?.l?.stop()};fun resume(){paused=false;inst?.h?.postDelayed({inst?.listen()},500)}}
    override fun onCreate(){super.onCreate();val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("jarvis","JARVIS",NotificationManager.IMPORTANCE_LOW));startForeground(1,Notification.Builder(this,"jarvis").setContentTitle("JARVIS ouvindo").setContentText("Diga Jarvis").setSmallIcon(android.R.drawable.ic_btn_speak_now).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);l=Listener(this);v=Voice(this);inst=this;listen()}
    private fun listen(){if(paused)return;l.start({t->if(words.any{t.lowercase().contains(it)}){val q=words.fold(t){a,w->a.replace(w,"",true).trim()};if(q.isBlank())v.say("Sim?"){listen()}else CoroutineScope(Dispatchers.Main).launch{v.say(Jarvis.handle(this@WakeService,q)){listen()}}}else h.postDelayed({listen()},250)},{h.postDelayed({listen()},500)})}
    override fun onDestroy(){inst=null;l.stop();v.shutdown();super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}

class ScreenShareService:Service(){
    companion object{@Volatile private var latest:ByteArray?=null;fun latestBase64():String?=latest?.let{Base64.getEncoder().encodeToString(it)}}
    private var mp:MediaProjection?=null;private var reader:ImageReader?=null
    override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{
        val data=i?.getParcelableExtra<Intent>("data")?:return START_NOT_STICKY;val code=i.getIntExtra("result",Activity.RESULT_CANCELED)
        val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("screen","JARVIS Tela",NotificationManager.IMPORTANCE_LOW));startForeground(7,Notification.Builder(this,"screen").setContentTitle("JARVIS compartilhando a tela").setContentText("A captura só é enviada ao OpenRouter em uma tarefa.").setSmallIcon(android.R.drawable.ic_menu_view).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val pm=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;mp=pm.getMediaProjection(code,data);val dm=resources.displayMetrics;val w=(dm.widthPixels*.6).toInt();val h=(dm.heightPixels*.6).toInt()
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);reader?.setOnImageAvailableListener({rr->rr.acquireLatestImage()?.use{im->try{val p=im.planes[0];val ps=p.pixelStride;val pad=(p.rowStride-ps*im.width)/ps;val tmp=Bitmap.createBitmap(im.width+pad,im.height,Bitmap.Config.ARGB_8888);tmp.copyPixelsFromBuffer(p.buffer);val b=Bitmap.createBitmap(tmp,0,0,im.width,im.height);tmp.recycle();val out=ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,55,out);latest=out.toByteArray();b.recycle()}catch(_:Exception){}}},Handler(Looper.getMainLooper()))
        mp?.createVirtualDisplay("JARVIS Screen",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,null);return START_STICKY
    }
    override fun onDestroy(){mp?.stop();reader?.close();latest=null;super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}

class AssistActivity:Activity(){
    private lateinit var v:Voice;private lateinit var l:Listener;private lateinit var status:TextView;private var running=true;private var screen=false
    private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob())
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    override fun onCreate(b:Bundle?){super.onCreate(b);v=Voice(this);l=Listener(this);WakeService.pause();val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM;setPadding(dp(18),dp(70),dp(18),dp(18));setBackgroundColor(0xFF050B12.toInt())};root.addView(TextView(this).apply{text="JARVIS • Assistente";textSize=24f;setTextColor(0xFF38BDF8.toInt());gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,dp(70)));status=TextView(this).apply{text="Ouvindo…";textSize=19f;setTextColor(Color.WHITE);setPadding(dp(18),dp(20),dp(18),dp(20))};root.addView(status,LinearLayout.LayoutParams(-1,0,1f));val row=LinearLayout(this);row.addView(Button(this).apply{text="🖥 Tela";setOnClickListener{share()}},LinearLayout.LayoutParams(0,dp(56),1f));row.addView(Button(this).apply{text="⏹ Parar";setOnClickListener{stopNow()}},LinearLayout.LayoutParams(0,dp(56),1f));root.addView(row);setContentView(root);listen()}
    private fun listen(){if(!running)return;l.start({t->if(t.isBlank()){listen();return@start};if(Regex(".*\\b(parar|pare|encerrar|desligar)\\b.*").matches(t.lowercase())){stopNow();return@start};status.text="Você: "+t;scope.launch{val r=Jarvis.handle(this@AssistActivity,t,screen);status.text="JARVIS: "+r;v.say(r){listen()}}},{listen()},{p->if(p.isNotBlank())status.text=p})}
    private fun share(){if(screen){stopService(Intent(this,ScreenShareService::class.java));screen=false;return};val m=getSystemService(MediaProjectionManager::class.java);startActivityForResult(m.createScreenCaptureIntent(),44)}
    private fun stopNow(){running=false;l.stop();finish()}
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==44&&c==RESULT_OK&&d!=null){startForegroundService(Intent(this,ScreenShareService::class.java).putExtra("result",c).putExtra("data",d));screen=true;status.text="Tela compartilhada. Peça uma tarefa."}}
    override fun onDestroy(){l.stop();v.shutdown();scope.cancel();WakeService.resume();super.onDestroy()}
}

class MainActivity:Activity(){
    private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob());private lateinit var v:Voice;private lateinit var l:Listener;private lateinit var chat:LinearLayout;private lateinit var input:EditText
    private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
    private fun b(s:String,f:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{f()}}
    override fun onCreate(x:Bundle?){super.onCreate(x);v=Voice(this);l=Listener(this);build()}
    private fun build(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF050B12.toInt())};val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(12),dp(8),dp(8))};val im=ImageView(this).apply{setImageResource(R.drawable.jarvis_logo)};bar.addView(im,LinearLayout.LayoutParams(dp(46),dp(46)));bar.addView(TextView(this).apply{text="JARVIS";textSize=22f;setTextColor(0xFF38BDF8.toInt())},LinearLayout.LayoutParams(0,dp(46),1f).apply{leftMargin=dp(10)});bar.addView(b("⚙"){settings()});root.addView(bar);root.addView(TextView(this).apply{text="Assistente • OpenRouter";textSize=13f;setTextColor(0xFF94A3B8.toInt());setPadding(dp(18),0,dp(18),dp(8))})
        val sv=ScrollView(this);chat=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(12))};sv.addView(chat);root.addView(sv,LinearLayout.LayoutParams(-1,0,1f));bubble("Olá! Sou o JARVIS. Configure sua chave do OpenRouter para começar.",false)
        val tools=LinearLayout(this);tools.addView(b("🎙 Falar"){talk()},LinearLayout.LayoutParams(0,dp(50),1f));tools.addView(b("🖥 Tela"){share()},LinearLayout.LayoutParams(0,dp(50),1f));tools.addView(b("🤖 Assistente"){assistant()},LinearLayout.LayoutParams(0,dp(50),1f));root.addView(tools)
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(14))};input=EditText(this).apply{hint="Mensagem…";setTextColor(Color.WHITE);setHintTextColor(0xFF64748B.toInt());setSingleLine()};row.addView(input,LinearLayout.LayoutParams(0,dp(54),1f));row.addView(b("Enviar"){send()},LinearLayout.LayoutParams(dp(90),dp(54)));root.addView(row);setContentView(root)}
    private fun bubble(s:String,u:Boolean){val t=TextView(this).apply{text=s;textSize=16f;setTextColor(Color.WHITE);setPadding(dp(14),dp(12),dp(14),dp(12));setBackgroundColor(if(u)0xFF123B5D.toInt() else 0xFF101A28.toInt())};chat.addView(t,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(5);leftMargin=if(u)dp(38)else 0;rightMargin=if(u)0 else dp(38)})}
    private fun send(){val t=input.text.toString().trim();if(t.isBlank())return;input.setText("");bubble(t,true);scope.launch{val r=Jarvis.handle(this@MainActivity,t);bubble(r,false);v.say(r)}}
    private fun talk(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),8);return};WakeService.pause();l.start({WakeService.resume();bubble(it,true);scope.launch{val r=Jarvis.handle(this@MainActivity,it);bubble(r,false);v.say(r)}},{WakeService.resume()})}
    private fun share(){val m=getSystemService(MediaProjectionManager::class.java);startActivityForResult(m.createScreenCaptureIntent(),55)}
    private fun assistant(){try{startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))}catch(_:Exception){startActivity(Intent(Settings.ACTION_SETTINGS))}}
    private fun settings(){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(8),dp(22),dp(4))};val k=EditText(this).apply{hint="sk-or-v1-...";setText(Store.get(this@MainActivity,Store.KEY));inputType=129};val m=EditText(this).apply{hint="openrouter/auto";setText(Store.get(this@MainActivity,Store.MODEL,Store.DEF_MODEL));setSingleLine()};box.addView(TextView(this).apply{text="Chave OpenRouter";setTextColor(0xFF38BDF8.toInt())});box.addView(k);box.addView(TextView(this).apply{text="Modelo";setTextColor(0xFF38BDF8.toInt())});box.addView(m);box.addView(b("Salvar"){Store.set(this,Store.KEY,k.text.toString().trim());Store.set(this,Store.MODEL,m.text.toString().trim().ifBlank{Store.DEF_MODEL});Toast.makeText(this,"Salvo",Toast.LENGTH_SHORT).show()});box.addView(b("Ativar acessibilidade para fazer tarefas"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))});box.addView(b("Definir JARVIS como assistente padrão"){assistant()});box.addView(b("Permitir sobreposição"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+packageName)))}) ;AlertDialog.Builder(this).setTitle("JARVIS • Configurações").setView(box).setNegativeButton("Fechar",null).show()}
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==55&&c==RESULT_OK&&d!=null)startForegroundService(Intent(this,ScreenShareService::class.java).putExtra("result",c).putExtra("data",d))}
    override fun onDestroy(){scope.cancel();l.stop();v.shutdown();super.onDestroy()}
}

class JarvisVIService:VoiceInteractionService()
class JarvisVISessionService:VoiceInteractionSessionService(){override fun onNewSession(a:Bundle?)=JarvisSession(this)}
class JarvisSession(c:Context):VoiceInteractionSession(c){override fun onShow(a:Bundle?,f:Int){super.onShow(a,f);try{startAssistantActivity(Intent(context,AssistActivity::class.java))}catch(_:Exception){context.startActivity(Intent(context,AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};hide()}}
class StubRecognition:RecognitionService(){
    private var s:SpeechRecognizer?=null
    override fun onStartListening(i:Intent?,cb:Callback?){val g=Listener.google(this);if(g==null){cb?.error(SpeechRecognizer.ERROR_CLIENT);return};s?.destroy();s=SpeechRecognizer.createSpeechRecognizer(this,g);s?.setRecognitionListener(object:RecognitionListener{
        override fun onReadyForSpeech(p:Bundle?){cb?.readyForSpeech(p?:Bundle())};override fun onBeginningOfSpeech(){cb?.beginningOfSpeech()};override fun onRmsChanged(v:Float){cb?.rmsChanged(v)};override fun onBufferReceived(b:ByteArray?){cb?.bufferReceived(b?:ByteArray(0))};override fun onEndOfSpeech(){cb?.endOfSpeech()};override fun onError(e:Int){cb?.error(e)};override fun onResults(b:Bundle?){cb?.results(b?:Bundle())};override fun onPartialResults(b:Bundle?){cb?.partialResults(b?:Bundle())};override fun onEvent(t:Int,p:Bundle?){}
    });s?.startListening(i?:Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))}
    override fun onCancel(cb:Callback?){s?.cancel()};override fun onStopListening(cb:Callback?){s?.stopListening()};override fun onDestroy(){s?.destroy();super.onDestroy()}
}
