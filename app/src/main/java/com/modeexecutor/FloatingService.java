package com.modeexecutor;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {
    private WindowManager wm;
    private View bubble,panel;
    private CountDownTimer timer;
    private TextView clock,title,output;
    private EditText input;
    private ExecutorService net=Executors.newSingleThreadExecutor();
    private Handler mainHandler=new Handler(Looper.getMainLooper());

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private TextView text(String s,float z,int c){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private TextView btn(String s){
        TextView t=text(s,12,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(bg(Color.rgb(39,35,52),14));return t;
    }

    @Override public void onCreate(){
        super.onCreate();
        if(!Settings.canDrawOverlays(this)){stopSelf();return;}
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        TextView b=text("M",18,Color.WHITE);b.setGravity(Gravity.CENTER);
        b.setBackground(bg(Color.rgb(132,65,225),30));b.setElevation(dp(10));b.setOnClickListener(v->showPanel());
        bubble=b;wm.addView(bubble,params(dp(58),dp(58),Gravity.RIGHT|Gravity.CENTER_VERTICAL,false));
    }

    private WindowManager.LayoutParams params(int w,int h,int g,boolean focus){
        int flags=focus?0:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        return new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);
    }

    private void showPanel(){
        if(panel!=null)return;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackground(bg(Color.rgb(15,13,21),24));root.setElevation(dp(18));

        LinearLayout head=new LinearLayout(this);
        title=text("MOD EXECUTOR",18,Color.WHITE);head.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        clock=text("10:00",12,Color.rgb(220,175,255));clock.setGravity(Gravity.CENTER);clock.setBackground(bg(Color.rgb(45,27,60),18));
        head.addView(clock,new LinearLayout.LayoutParams(dp(64),dp(36)));root.addView(head);

        LinearLayout tabs=new LinearLayout(this);
        TextView chat=btn("IA CHAT"),play=btn("IA JOGAR"),cmd=btn("COMANDOS");
        tabs.addView(chat,new LinearLayout.LayoutParams(0,dp(42),1));
        tabs.addView(play,new LinearLayout.LayoutParams(0,dp(42),1));
        tabs.addView(cmd,new LinearLayout.LayoutParams(0,dp(42),1));root.addView(tabs);

        input=new EditText(this);input.setTextColor(Color.WHITE);input.setHintTextColor(Color.rgb(110,103,125));
        input.setHint("Fale com a IA ou peça uma ação...");
        input.setTextSize(14);input.setGravity(Gravity.TOP|Gravity.START);input.setSingleLine(false);
        input.setPadding(dp(12),dp(12),dp(12),dp(12));input.setBackground(bg(Color.rgb(25,22,33),18));
        root.addView(input,new LinearLayout.LayoutParams(-1,dp(150)));

        TextView send=text("ENVIAR PARA IA",13,Color.WHITE);send.setGravity(Gravity.CENTER);
        send.setBackground(bg(Color.rgb(132,65,225),16));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.topMargin=dp(8);
        root.addView(send,sp);

        output=text("Pronto • IA conectada quando uma chave OpenRouter estiver configurada.",11,Color.rgb(170,160,185));
        output.setPadding(dp(4),dp(8),dp(4),dp(8));root.addView(output);

        TextView close=btn("FECHAR");root.addView(close,new LinearLayout.LayoutParams(-1,dp(40)));

        chat.setOnClickListener(v->{title.setText("MOD EXECUTOR • IA CHAT");output.setText("Converse com a IA. Use o modo IA JOGAR para gerar ações do seu jogo.");});
        play.setOnClickListener(v->{title.setText("MOD EXECUTOR • IA JOGAR");input.setText("Jogue no meu jogo: analise a situação e retorne uma ação segura entre MOVE, JUMP, FOLLOW, KICK_BALL, STOP.");output.setText("A IA pode planejar ações para a integração autorizada do seu jogo.");});
        cmd.setOnClickListener(v->{title.setText("COMANDOS");input.setText("MOVE\nJUMP\nFOLLOW\nKICK_BALL\nSTOP");output.setText("Comandos disponíveis para o bridge do seu próprio jogo.");});
        send.setOnClickListener(v->askAI());
        close.setOnClickListener(v->removePanel());

        panel=root;
        int width=(int)(getResources().getDisplayMetrics().widthPixels*.92f);
        wm.addView(panel,params(width,WindowManager.LayoutParams.WRAP_CONTENT,Gravity.CENTER,true));

        timer=new CountDownTimer(600000,1000){
            public void onTick(long m){clock.setText(String.format("%d:%02d",m/60000,(m/1000)%60));}
            public void onFinish(){removePanel();}
        }.start();
    }

    private void askAI(){
        String key=getSharedPreferences("ModExecutor",MODE_PRIVATE).getString("openrouter_key","");
        if(key.isEmpty()){
            // MainActivity stores in activity preferences; copy is attempted below for compatibility.
            output.setText("Configure a chave OpenRouter na tela principal e abra o menu novamente.");
            return;
        }
        String model=getSharedPreferences("ModExecutor",MODE_PRIVATE).getString("openrouter_model","openrouter/free");
        String prompt=input.getText().toString().trim();
        if(prompt.isEmpty()){output.setText("Digite uma mensagem.");return;}
        output.setText("IA pensando...");
        net.execute(()->{
            try{
                JSONObject body=new JSONObject();
                body.put("model",model);
                JSONArray messages=new JSONArray();
                JSONObject sys=new JSONObject();
                sys.put("role","system");
                sys.put("content","Você é a IA do Mod Executor. Ajude o usuário e, quando solicitado a jogar, retorne uma única ação segura para o jogo próprio: MOVE, JUMP, FOLLOW, KICK_BALL ou STOP. Nunca peça injeção no cliente Roblox.");
                messages.put(sys);
                JSONObject user=new JSONObject();user.put("role","user");user.put("content",prompt);messages.put(user);
                body.put("messages",messages);

                HttpURLConnection c=(HttpURLConnection)new URL("https://openrouter.ai/api/v1/chat/completions").openConnection();
                c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setDoOutput(true);
                c.setRequestProperty("Authorization","Bearer "+key);
                c.setRequestProperty("Content-Type","application/json");
                byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream os=c.getOutputStream();os.write(data);os.close();

                InputStream is=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();
                BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));
                StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);
                br.close();
                JSONObject res=new JSONObject(sb.toString());
                String answer=res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","Sem resposta.");
                mainHandler.post(()->output.setText(answer));
            }catch(Exception e){mainHandler.post(()->output.setText("Erro OpenRouter: "+e.getMessage()));}
        });
    }

    private void removePanel(){if(timer!=null){timer.cancel();timer=null;}if(panel!=null){wm.removeView(panel);panel=null;}}
    @Override public void onDestroy(){removePanel();net.shutdownNow();if(bubble!=null)wm.removeView(bubble);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
