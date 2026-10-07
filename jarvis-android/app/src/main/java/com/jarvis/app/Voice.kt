package com.jarvis.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class Voice(c:Context){
    private var tts:TextToSpeech?=null
    @Volatile private var ready=false
    private var pending:Pair<String,(() -> Unit)?>?=null
    private var done:(() -> Unit)?=null
    init{tts=TextToSpeech(c.applicationContext){st->if(st==TextToSpeech.SUCCESS){tts?.language=Locale("pt","BR");tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){ };override fun onDone(id:String?){fire()};override fun onError(id:String?){fire()}});ready=true;pending?.let{p->pending=null;say(p.first,p.second)}}}}
    private fun fire(){val d=done;done=null;d?.let{Handler(Looper.getMainLooper()).post(it)}}
    fun say(t:String,onDone:(()->Unit)?=null){if(t.isBlank()){onDone?.invoke();return};if(!ready){pending=t to onDone;return};done=onDone;tts?.speak(t,TextToSpeech.QUEUE_FLUSH,null,"jarvis")}
    fun shutdown(){tts?.stop();tts?.shutdown()}
}
class Listener(private val c:Context){
    private var sr:SpeechRecognizer?=null
    companion object{fun google(c:Context):ComponentName?{val g=ComponentName("com.google.android.googlequicksearchbox","com.google.android.voicesearch.serviceapi.GoogleRecognitionService");return try{c.packageManager.getServiceInfo(g,0);g}catch(_:Exception){null}}}
    private fun kill(){try{sr?.cancel();sr?.destroy()}catch(_:Exception){};sr=null}
    fun stop(){kill()}
    fun start(onFinal:(String)->Unit,onFail:(Int)->Unit,onPartial:((String)->Unit)?=null){
        kill();if(!SpeechRecognizer.isRecognitionAvailable(c)){onFail(-1);return}
        val g=google(c);val r=if(g!=null)SpeechRecognizer.createSpeechRecognizer(c,g)else SpeechRecognizer.createSpeechRecognizer(c);sr=r
        r.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(p:Bundle?){}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(v:Float){}
            override fun onBufferReceived(b:ByteArray?){}
            override fun onEndOfSpeech(){}
            override fun onEvent(t:Int,p:Bundle?){}
            override fun onError(e:Int){if(sr===r){kill();onFail(e)}}
            override fun onResults(b:Bundle?){if(sr===r){kill();onFinal(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:"")}}
            override fun onPartialResults(b:Bundle?){onPartial?.invoke(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:"")}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)})
    }
}