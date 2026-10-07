package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Path
import android.graphics.Rect
import android.os.*
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class WakeService : Service() {
    private val h=Handler(Looper.getMainLooper())
    private lateinit var ls: Listener
    private lateinit var voice: Voice
    private var busy=false
    companion object {
        @Volatile var paused=false
        var inst: WakeService?=null
        fun pause(){paused=true; inst?.ls?.stop()}
        fun resume(){paused=false; inst?.kick(600)}
        private val WORDS=listOf("jarvis","jarves","jarvez","jarbis","já vis")
        fun isWake(t:String)=WORDS.any{t.lowercase().contains(it)}
        fun strip(t:String):String{val l=t.lowercase(); for(w in WORDS){val i=l.indexOf(w);if(i>=0)return t.substring(i+w.length).trim(' ',',','.','!')};return t}
    }
    override fun onCreate(){
        super.onCreate()
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("jarvis","Jarvis",NotificationManager.IMPORTANCE_LOW))
        val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val n=Notification.Builder(this,"jarvis").setContentTitle("Jarvis ouvindo").setContentText("Diga Jarvis e o comando").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi).build()
        if(Build.VERSION.SDK_INT>=29)startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1,n)
        ls=Listener(this);voice=Voice(this);inst=this;kick(500)
    }
    override fun onStartCommand(i:Intent?,f:Int,id:Int)=START_STICKY
    override fun onBind(i:Intent?):IBinder?=null
    fun kick(d:Long){h.removeCallbacksAndMessages(null);h.postDelayed({listen()},d)}
    private fun listen(){if(paused||busy)return;ls.start({t->if(isWake(t))exec(strip(t))else kick(200)},{kick(500)})}
    private fun exec(cmd:String){if(cmd.isBlank()){kick(300);return};busy=true;GlobalScope.launch(Dispatchers.Main){val r=try{Jarvis.handle(this@WakeService,cmd)}catch(e:Exception){"Deu erro: ${e.message}"};voice.say(r){busy=false;kick(400)}}}
    override fun onDestroy(){inst=null;h.removeCallbacksAndMessages(null);ls.stop();voice.shutdown();super.onDestroy()}
}

class JarvisA11yService:AccessibilityService(){
    companion object{var inst:JarvisA11yService?=null}
    override fun onServiceConnected(){inst=this}
    override fun onUnbind(i:Intent?):Boolean{inst=null;return super.onUnbind(i)}
    override fun onAccessibilityEvent(e:AccessibilityEvent?){}
    override fun onInterrupt(){}
    private fun find(n:AccessibilityNodeInfo?,t:String):AccessibilityNodeInfo?{if(n==null)return null;val a=n.text?.toString()?.lowercase()?:"";val d=n.contentDescription?.toString()?.lowercase()?:"";if(a.contains(t)||d.contains(t))return n;for(i in 0 until n.childCount)find(n.getChild(i),t)?.let{return it};return null}
    fun clickText(t:String):Boolean{val n=find(rootInActiveWindow,t.lowercase().trim())?:return false;var x:AccessibilityNodeInfo?=n;while(x!=null){if(x.isClickable)return x.performAction(AccessibilityNodeInfo.ACTION_CLICK);x=x.parent};val r=Rect();n.getBoundsInScreen(r);return tap(r.centerX().toFloat(),r.centerY().toFloat())}
    fun typeText(t:String):Boolean{val r=rootInActiveWindow?:return false;val f=r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?:return false;return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,t)})}
    private fun gesture(p:Path,d:Long)=dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,d)).build(),null,null)
    fun tap(x:Float,y:Float)=gesture(Path().apply{moveTo(x,y)},60)
    fun swipe(down:Boolean){val m=resources.displayMetrics;val x=m.widthPixels/2f;val y1=m.heightPixels*(if(down).7f else .3f);val y2=m.heightPixels*(if(down).3f else .7f);gesture(Path().apply{moveTo(x,y1);lineTo(x,y2)},350)}
    fun screenText():String{val sb=StringBuilder();fun w(n:AccessibilityNodeInfo?,d:Int){if(n==null||d>20||sb.length>600)return;n.text?.let{sb.append(it).append(". ")};for(i in 0 until n.childCount)w(n.getChild(i),d+1)};w(rootInActiveWindow,0);return sb.toString()}
}
class JarvisVIService:VoiceInteractionService()
class JarvisVISessionService:VoiceInteractionSessionService(){override fun onNewSession(a:Bundle?)=JarvisSession(this)}
class JarvisSession(c:Context):VoiceInteractionSession(c){override fun onShow(a:Bundle?,f:Int){super.onShow(a,f);try{startAssistantActivity(Intent(context,AssistActivity::class.java))}catch(_:Exception){context.startActivity(Intent(context,AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};hide()}}
class StubRecognition:RecognitionService(){
    private var sr:SpeechRecognizer?=null
    override fun onStartListening(i:Intent?,cb:Callback?){val r=SpeechRecognizer.createSpeechRecognizer(this);sr=r;r.setRecognitionListener(object:RecognitionListener{
        override fun onReadyForSpeech(p:Bundle?){cb?.readyForSpeech(p?:Bundle())};override fun onBeginningOfSpeech(){};override fun onRmsChanged(v:Float){};override fun onBufferReceived(b:ByteArray?){ };override fun onEndOfSpeech(){cb?.endOfSpeech()};override fun onError(e:Int){cb?.error(e)};override fun onResults(b:Bundle?){cb?.results(b?:Bundle())};override fun onPartialResults(b:Bundle?){cb?.partialResults(b?:Bundle())};override fun onEvent(t:Int,p:Bundle?){}
    });r.startListening(i?:Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))}
    override fun onCancel(cb:Callback?){sr?.cancel()};override fun onStopListening(cb:Callback?){sr?.stopListening()};override fun onDestroy(){sr?.destroy();super.onDestroy()}
}