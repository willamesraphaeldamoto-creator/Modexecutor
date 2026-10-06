package com.jarvismobile.ia;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private EditText command;
    private LinearLayout messages;
    private TextView status;
    private JarvisDbHelper db;
    private Button apiButton;
    private static final String OR_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String DEFAULT_MODEL = "nvidia/nemotron-3-nano-omni:free";
    private TextToSpeech tts;
    private static final int VOICE_REQ = 71;
    private static final int IMAGE_REQ = 72;
    private static final String MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf";
    private static final String MODEL_NAME = "qwen2.5-0.5b-instruct-q4_0.gguf";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new JarvisDbHelper(this);
        tts = new TextToSpeech(this, x -> { if (x == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("pt","BR")); });
        buildUi();
        restoreHistory();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable box(int c, int r) { GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp(r)); return g; }
    private TextView tv(String s,float z,int c){ TextView t=new TextView(this); t.setText(s); t.setTextSize(z); t.setTextColor(c); return t; }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,18,18));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(16),dp(10),dp(16),dp(6));
        TextView title=tv("JARVIS",22,Color.WHITE); title.setTypeface(null,1); top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        apiButton=new Button(this); apiButton.setText("⚙ IA / API"); apiButton.setOnClickListener(v->showApiConfig()); top.addView(apiButton,new LinearLayout.LayoutParams(dp(110),dp(50)));
        Button voice=new Button(this); voice.setText("🎙"); voice.setOnClickListener(v->startVoice()); top.addView(voice,new LinearLayout.LayoutParams(dp(58),dp(50)));
        Button image=new Button(this); image.setText("🖼"); image.setOnClickListener(v->pickImage()); top.addView(image,new LinearLayout.LayoutParams(dp(58),dp(50)));
        root.addView(top);

        ScrollView sc=new ScrollView(this);
        messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(dp(12),dp(8),dp(12),dp(8)); sc.addView(messages);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bottom=new LinearLayout(this); bottom.setOrientation(LinearLayout.VERTICAL); bottom.setPadding(dp(10),dp(6),dp(10),dp(8));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        command=new EditText(this); command.setHint("Fale ou escreva para JARVIS"); command.setTextColor(Color.WHITE); command.setHintTextColor(Color.GRAY); command.setBackground(box(Color.rgb(45,45,45),26)); command.setPadding(dp(14),dp(5),dp(14),dp(5));
        row.addView(command,new LinearLayout.LayoutParams(0,dp(54),1));
        Button send=new Button(this); send.setText("↑"); send.setOnClickListener(v->sendText()); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(54),dp(54)); sp.setMargins(dp(6),0,0,0); row.addView(send,sp);
        bottom.addView(row);
        status=tv("Histórico local ativo • modelo ainda não instalado",12,Color.LTGRAY); bottom.addView(status);
        Button model=new Button(this); model.setText("BAIXAR / CONTINUAR MODELO LOCAL"); model.setOnClickListener(v->downloadModel()); bottom.addView(model,new LinearLayout.LayoutParams(-1,dp(46)));
        Button access=new Button(this); access.setText("Controle autorizado do celular"); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); bottom.addView(access,new LinearLayout.LayoutParams(-1,dp(42)));
        Button clear=new Button(this); clear.setText("Limpar histórico"); clear.setOnClickListener(v->{db.clearHistory(); messages.removeAllViews(); addMessage("Histórico apagado.",false);}); bottom.addView(clear,new LinearLayout.LayoutParams(-1,dp(40)));
        root.addView(bottom); setContentView(root);
    }

    private void addMessage(String s,boolean user){ TextView b=tv(s,16,Color.WHITE); b.setPadding(dp(14),dp(10),dp(14),dp(10)); b.setBackground(box(user?Color.rgb(50,50,50):Color.rgb(34,34,34),18)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2); p.gravity=user?Gravity.END:Gravity.START; p.setMargins(dp(2),dp(4),dp(2),dp(4)); messages.addView(b,p); }
    private void save(String role,String content,String media){ db.add(role,content,media); }

    private void restoreHistory(){
        android.database.Cursor c=db.history();
        try { while(c.moveToNext()) addMessage(c.getString(c.getColumnIndexOrThrow("content")), "user".equals(c.getString(c.getColumnIndexOrThrow("role")))); }
        finally { c.close(); }
        if(messages.getChildCount()==0) addMessage("Olá! Eu sou o JARVIS. Você pode escrever, falar ou enviar uma imagem.",false);
    }

    private void showApiConfig(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8),dp(4),dp(8),0);
        EditText key=new EditText(this); key.setHint("OPENROUTER_API_KEY"); key.setInputType(0x00000081); key.setText(db.get("openrouter_key")); box.addView(key);
        EditText model=new EditText(this); model.setHint("Modelo"); model.setText(db.get("openrouter_model").isEmpty()?DEFAULT_MODEL:db.get("openrouter_model")); box.addView(model);
        CheckBox enabled=new CheckBox(this); enabled.setText("Usar OpenRouter quando configurado"); enabled.setChecked("1".equals(db.get("openrouter_enabled"))); box.addView(enabled);
        new AlertDialog.Builder(this).setTitle("Configurar IA").setMessage("A chave fica salva somente no banco privado do app. Não coloque sua chave no GitHub.")
        .setView(box).setPositiveButton("Salvar", (d,w)->{db.put("openrouter_key",key.getText().toString().trim());db.put("openrouter_model",model.getText().toString().trim());db.put("openrouter_enabled",enabled.isChecked()?"1":"0"); status.setText("Configuração da IA salva no aparelho.");})
        .setNeutralButton("Testar", (d,w)->testOpenRouter(key.getText().toString().trim(),model.getText().toString().trim())).setNegativeButton("Cancelar",null).show();
    }

    private void testOpenRouter(String key,String model){
        if(key.isEmpty()){addMessage("Informe uma chave OpenRouter para testar.",false);return;}
        new Thread(()->{
            try{
                String body="{\"model\":\""+json(model.isEmpty()?DEFAULT_MODEL:model)+"\",\"messages\":[{\"role\":\"user\",\"content\":\"Responda apenas: JARVIS online.\"}],\"max_tokens\":20}";
                String out=http(body,key);
                runOnUiThread(()->{addMessage("Teste da IA: "+out,false);speak(out);});
            }catch(Exception e){runOnUiThread(()->addMessage("Falha no teste da IA: "+e.getMessage(),false));}
        }).start();
    }

    private String json(String s){return s.replace("\\\\","\\\\\\\\").replace("\"","\\\"");}
    private String http(String body,String key) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(OR_URL).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setDoOutput(true);
        c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("X-Title","JARVIS Mobile IA");
        try(OutputStream o=c.getOutputStream()){o.write(body.getBytes("UTF-8"));}
        int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream(); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);c.disconnect();
        String raw=out.toString("UTF-8"); if(code<200||code>=300)throw new IOException("HTTP "+code+" "+raw); return extract(raw,"content");
    }
    private String extract(String raw,String field){
        String marker="\""+field+"\":\""; int p=raw.indexOf(marker); if(p<0)return raw; p+=marker.length(); StringBuilder s=new StringBuilder(); boolean esc=false;
        for(int i=p;i<raw.length();i++){char ch=raw.charAt(i);if(esc){s.append(ch);esc=false;}else if(ch=='\\')esc=true;else if(ch=='\"')break;else s.append(ch);}return s.toString();
    }

    private void sendText(){ String s=command.getText().toString().trim(); if(s.isEmpty())return; command.setText(""); addMessage(s,true); save("user",s,""); respond(s); }
    private void respond(String s){
        String l=s.toLowerCase(Locale.ROOT);
        String r;
        if(l.contains("config")){ r="Abrindo as configurações."; startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        else if(l.contains("wifi")){ r="Abrindo o Wi‑Fi."; startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        else if(l.contains("modelo") && l.contains("baix")){ r="Vou iniciar o download do modelo local."; downloadModel(); }
        else { String key=db.get("openrouter_key"); if("1".equals(db.get("openrouter_enabled")) && !key.isEmpty()){ askOpenRouter(s,key); return; } r="Recebi: “"+s+"”. O histórico está salvo neste aparelho. Configure uma IA em ⚙ IA / API para obter respostas reais."; }
        addMessage(r,false); save("assistant",r,""); speak(r);
    }

    private void speak(String s){ if(tts!=null) tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis"); }

    private void startVoice(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},90); return; }
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");
        try{startActivityForResult(i,VOICE_REQ);}catch(Exception e){ addMessage("Reconhecimento de voz não disponível neste aparelho.",false); }
    }

    private void pickImage(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(i,IMAGE_REQ);}catch(Exception e){ addMessage("Não foi possível abrir a galeria.",false); }
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null)return;
        if(req==VOICE_REQ){ ArrayList<String> a=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if(a!=null&&!a.isEmpty()){command.setText(a.get(0));sendText();} }
        else if(req==IMAGE_REQ){ String uri=String.valueOf(data.getData()); String m="Imagem selecionada: "+uri; addMessage(m,true); save("user",m,uri); speak("Imagem recebida."); }
    }

    private File model(){return new File(getFilesDir(),MODEL_NAME);}
    private File part(){return new File(getFilesDir(),MODEL_NAME+".part");}

    private void downloadModel(){
        if(model().exists()&&model().length()>100000000){status.setText("Modelo local instalado • pronto para a próxima etapa");return;}
        status.setText("Baixando modelo…");
        new Thread(()->{
            File p=part(); long have=p.exists()?p.length():0;
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(MODEL_URL).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setInstanceFollowRedirects(true);
                if(have>0)c.setRequestProperty("Range","bytes="+have+"-"); c.connect(); int code=c.getResponseCode(); boolean resume=have>0&&code==206; if(have>0&&!resume){have=0;p.delete();}
                if(code!=200&&code!=206)throw new IOException("HTTP "+code);
                long len=c.getContentLengthLong(), total=resume&&len>0?have+len:len, done=resume?have:0;
                try(InputStream in=new BufferedInputStream(c.getInputStream(),262144); FileOutputStream out=new FileOutputStream(p,resume)){
                    byte[] b=new byte[262144]; int n; long last=0;
                    while((n=in.read(b))!=-1){out.write(b,0,n);done+=n;long now=System.currentTimeMillis();if(now-last>800){last=now;final long d=done,t=total;runOnUiThread(()->status.setText(t>0?("Baixando modelo… "+(d*100/t)+"%"):("Baixando modelo… "+d/1048576+" MB")));}}
                } c.disconnect();
                if(!p.renameTo(model()))throw new IOException("Não foi possível finalizar o arquivo");
                runOnUiThread(()->status.setText("Modelo local instalado • salvo neste aparelho"));
            }catch(Exception e){runOnUiThread(()->status.setText("Download pausado: "+e.getMessage()+" • toque novamente para continuar"));}
        }).start();
    }

    @Override protected void onDestroy(){ if(tts!=null){tts.stop();tts.shutdown();} super.onDestroy(); }
}
    private void askOpenRouter(String prompt,String key){
        addMessage("JARVIS está pensando…",false);
        new Thread(()->{try{
            String model=db.get("openrouter_model"); if(model.isEmpty())model=DEFAULT_MODEL;
            String body="{\"model\":\""+json(model)+"\",\"messages\":[{\"role\":\"system\",\"content\":\"Você é JARVIS, assistente pessoal em português do Brasil. Seja útil, claro e seguro.\"},{\"role\":\"user\",\"content\":\""+json(prompt)+"\"}]}";
            String answer=http(body,key);
            runOnUiThread(()->{addMessage(answer,false);save("assistant",answer,"");speak(answer);});
        }catch(Exception e){runOnUiThread(()->addMessage("Erro na IA: "+e.getMessage(),false));}}).start();
    }
