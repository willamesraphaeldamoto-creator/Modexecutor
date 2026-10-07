package com.jarvis.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity:Activity(){
 private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob());private lateinit var voice:Voice;private lateinit var listener:Listener;private lateinit var chat:LinearLayout;private lateinit var input:EditText;private lateinit var scroll:ScrollView;private lateinit var status:TextView;private var sharing=false;private val REQ_SCREEN=909
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun tv(t:String,size:Float,color:Int)=TextView(this).apply{text=t;textSize=size;setTextColor(color);setPadding(dp(8),dp(8),dp(8),dp(8))}
 private fun button(t:String,f:()->Unit)=Button(this).apply{text=t;isAllCaps=false;setOnClickListener{f()}}
 override fun onCreate(b:Bundle?){super.onCreate(b);voice=Voice(this);listener=Listener(this);buildUi()}
 private fun buildUi(){
  val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setBackgroundColor(0xFF0B0B0F.toInt())
  val top=LinearLayout(this);top.gravity=Gravity.CENTER_VERTICAL;top.setPadding(dp(12),dp(8),dp(12),dp(8));top.addView(tv("☰",25f,Color.WHITE));top.addView(tv("JARVIS",20f,0xFFEDEDED.toInt()),LinearLayout.LayoutParams(0,dp(55),1f));top.addView(button("⚙"){settings()},LinearLayout.LayoutParams(dp(55),dp(55)));root.addView(top)
  status=tv("Online • pronto",12f,0xFF8E8E93.toInt());status.gravity=Gravity.CENTER;root.addView(status)
  scroll=ScrollView(this);chat=LinearLayout(this);chat.orientation=LinearLayout.VERTICAL;chat.setPadding(dp(12),dp(10),dp(12),dp(20));scroll.addView(chat);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  addBubble("Olá! Eu sou o Jarvis. Posso conversar e, com a Acessibilidade ativada, abrir apps, tocar, digitar, clicar e rolar no celular.",false)
  val tools=LinearLayout(this);tools.setPadding(dp(8),dp(4),dp(8),dp(4));tools.addView(button("＋ Modelo"){pickModel()});tools.addView(button("🖥 Tela"){screenShare()});tools.addView(button("🎤"){talk()},LinearLayout.LayoutParams(dp(90),dp(55)));root.addView(tools)
  val bar=LinearLayout(this);bar.setPadding(dp(8),dp(4),dp(8),dp(10));input=EditText(this);input.hint="Pergunte ao Jarvis…";input.setTextColor(Color.WHITE);input.setHintTextColor(0xFF77777D.toInt());bar.addView(input,LinearLayout.LayoutParams(0,dp(58),1f));bar.addView(button("➤"){send()},LinearLayout.LayoutParams(dp(65),dp(58)));root.addView(bar);setContentView(root)
 }
 private fun addBubble(text:String,user:Boolean){val v=tv(text,15f,0xFFF1F1F3.toInt());v.setBackgroundColor(if(user)0xFF2A2A2F.toInt() else 0xFF15151A.toInt());v.setPadding(dp(14),dp(12),dp(14),dp(12));val p=LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));chat.addView(v,p);scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}
 private fun send(){val t=input.text.toString().trim();if(t.isBlank())return;input.setText("");addBubble(t,true);status.text="Pensando…";scope.launch{val r=try{Jarvis.handle(this@MainActivity,t)}catch(e:Exception){"Erro: "+e.message};addBubble(r,false);status.text="Online • pronto";voice.say(r)}}
 private fun talk(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),2);return};status.text="Ouvindo…";listener.start({t->status.text="Processando…";addBubble(t,true);scope.launch{val r=Jarvis.handle(this@MainActivity,t);addBubble(r,false);status.text="Online • pronto";voice.say(r)}},{status.text="Microfone indisponível"},{p->status.text="Ouvindo: $p"})}
 private fun screenShare(){if(sharing){stopService(Intent(this,ScreenShareService::class.java));sharing=false;status.text="Compartilhamento parado";return};val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;startActivityForResult(m.createScreenCaptureIntent(),REQ_SCREEN)}
 @Deprecated("Deprecated") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==REQ_SCREEN&&c==RESULT_OK&&d!=null){startForegroundService(Intent(this,ScreenShareService::class.java).putExtra("result",c).putExtra("data",d));sharing=true;status.text="Tela compartilhada com o Jarvis"}else if(r==7&&c==RESULT_OK&&d?.data!=null)copyModel(d.data!!)}
 private fun settings(){
  val wake=Store.get(this,"wake_words","jarvis,robo,julia");val items=arrayOf("Permissões","Acessibilidade / controle do celular","Assistente padrão","Sobrepor outros apps","Bateria sem restrição","Configurar IA / modelo","Palavras de ativação","Ligar escuta contínua")
  AlertDialog.Builder(this).setTitle("JARVIS").setItems(items){_,w->when(w){
   0->requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE),1)
   1->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
   2->startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
   3->startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
   4->startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
   5->pickModel()
   6->wakeWordsDialog(wake)
   7->toggleWake()
  }}.show()
 }
 private fun wakeWordsDialog(current:String){val e=EditText(this);e.setText(current);e.hint="jarvis, robo, julia";AlertDialog.Builder(this).setTitle("Palavras de ativação").setMessage("Separe por vírgula. O Jarvis continuará ouvindo até você dizer “parar de escutar”.").setView(e).setPositiveButton("Salvar"){_,_->Store.set(this,"wake_words",e.text.toString().lowercase());}.setNegativeButton("Cancelar",null).show()}
 private fun toggleWake(){if(Store.get(this,"wake","0")=="1"){stopService(Intent(this,WakeService::class.java));Store.set(this,"wake","0");status.text="Escuta contínua desligada"}else{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),3);return};startForegroundService(Intent(this,WakeService::class.java));Store.set(this,"wake","1");status.text="Escuta contínua ligada"}}
 private fun pickModel(){val url=EditText(this);url.hint="URL do modelo .task";url.setText(Store.get(this,Store.LOCAL_URL,Store.DEF_URL));AlertDialog.Builder(this).setTitle("Modelo local").setMessage("Baixe o modelo no celular ou escolha um arquivo .task já baixado.").setView(url).setPositiveButton("Baixar"){_,_->Store.set(this,Store.LOCAL_URL,url.text.toString());download(url.text.toString())}.setNeutralButton("Escolher arquivo"){_,_->startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),7)}.setNegativeButton("Cancelar",null).show()}
 private fun download(u:String){status.text="Baixando modelo…";Thread{try{val cn=URL(u).openConnection() as HttpURLConnection;cn.connectTimeout=20000;cn.readTimeout=60000;val name=URL(u).path.substringAfterLast('/').ifBlank{"model.task"};val dir=File(filesDir,"models").apply{mkdirs()};val f=File(dir,name);cn.inputStream.use{a->f.outputStream().use{b->a.copyTo(b)}};Store.set(this,Store.LOCAL_PATH,f.absolutePath);runOnUiThread{status.text="Modelo baixado ✓"}}catch(e:Exception){runOnUiThread{status.text="Erro: "+e.message}}}.start()}
 private fun copyModel(uri:Uri){Thread{try{val dir=File(filesDir,"models").apply{mkdirs()};val f=File(dir,"model.task");contentResolver.openInputStream(uri)!!.use{a->f.outputStream().use{b->a.copyTo(b)}};Store.set(this,Store.LOCAL_PATH,f.absolutePath);runOnUiThread{status.text="Modelo local pronto ✓"}}catch(e:Exception){runOnUiThread{status.text="Erro: "+e.message}}}.start()}
 override fun onDestroy(){scope.cancel();listener.stop();voice.shutdown();if(sharing)stopService(Intent(this,ScreenShareService::class.java));super.onDestroy()}
}