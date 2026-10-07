package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*

class AssistActivity:Activity(){
    private lateinit var tv:TextView
    private lateinit var voice:Voice
    private lateinit var ls:Listener
    private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob())
    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.gravity=Gravity.BOTTOM;box.setBackgroundColor(0x66000000);box.setOnClickListener{finish()}
        tv=TextView(this);tv.textSize=20f;tv.setTextColor(Color.WHITE);tv.setPadding(48,48,48,96);tv.text="Jarvis: ouvindo…"
        val bg=GradientDrawable();bg.setColor(0xEE101828.toInt());bg.cornerRadii=floatArrayOf(48f,48f,48f,48f,0f,0f,0f,0f);tv.background=bg
        box.addView(tv,LinearLayout.LayoutParams(-1,-2));setContentView(box)
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){startActivity(Intent(this,MainActivity::class.java));finish();return}
        voice=Voice(this);ls=Listener(this);WakeService.pause()
        ls.start({handle(it)},{tv.text="Não ouvi nada.";end(1200)},{tv.text=it})
    }
    private fun handle(t:String){if(t.isBlank()){end(300);return};tv.text="Você: $t";scope.launch{val r=try{Jarvis.handle(this@AssistActivity,t)}catch(e:Exception){"Erro: ${e.message}"};tv.text=r;voice.say(r){end(500)}}}
    private fun end(ms:Long){Handler(Looper.getMainLooper()).postDelayed({finish()},ms)}
    override fun onDestroy(){if(::ls.isInitialized)ls.stop();if(::voice.isInitialized)voice.shutdown();scope.cancel();WakeService.resume();super.onDestroy()}
}