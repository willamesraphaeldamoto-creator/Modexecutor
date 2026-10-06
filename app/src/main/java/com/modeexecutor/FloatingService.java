package com.modeexecutor;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingService extends Service {
    private WindowManager wm; private View bubble, panel; private CountDownTimer timer;
    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        bubble=new Button(this); ((Button)bubble).setText("MOD");
        bubble.setOnClickListener(v -> showPanel());
        wm.addView(bubble, params(180,100,Gravity.RIGHT|Gravity.CENTER_VERTICAL));
    }
    private WindowManager.LayoutParams params(int w,int h,int g){
        return new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
    }
    private void showPanel(){
        if(panel!=null)return;
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,24,24,24);
        TextView title=new TextView(this); title.setText("Mod Executor • IA"); title.setTextSize(20); title.setTextColor(Color.BLACK); box.addView(title);
        TextView clock=new TextView(this); clock.setText("10:00"); box.addView(clock);
        EditText code=new EditText(this); code.setHint("Digite seu comando Lua..."); code.setMinLines(6); box.addView(code);
        Button run=new Button(this); run.setText("Executar no Sandbox"); box.addView(run);
        Button friend=new Button(this); friend.setText("IA Amiga"); box.addView(friend);
        Button close=new Button(this); close.setText("Fechar"); box.addView(close);
        run.setOnClickListener(v -> {
            String s=code.getText().toString().trim();
            TextView out=new TextView(this);
            out.setText(s.isEmpty() ? "Digite um comando." : (s.startsWith("print")||s.startsWith("return")||s.startsWith("--") ? "Sandbox: comando aceito." : "Sandbox: comando bloqueado."));
            box.addView(out);
        });
        friend.setOnClickListener(v -> { title.setText("Mod Executor • IA\nOi! Posso conversar e ajudar com tarefas."); });
        close.setOnClickListener(v -> removePanel());
        panel=box;
        WindowManager.LayoutParams lp=params(900,900,Gravity.CENTER); lp.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.width=(int)(getResources().getDisplayMetrics().widthPixels*0.92f); lp.height=WindowManager.LayoutParams.WRAP_CONTENT;
        lp.flags=WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        wm.addView(panel,lp);
        timer=new CountDownTimer(600000,1000){ public void onTick(long m){clock.setText(String.format("%d:%02d",m/60000,(m/1000)%60));} public void onFinish(){clock.setText("Sessão encerrada"); removePanel();}}.start();
    }
    private void removePanel(){ if(timer!=null){timer.cancel();timer=null;} if(panel!=null){wm.removeView(panel);panel=null;} }
    @Override public void onDestroy(){ removePanel(); if(bubble!=null)wm.removeView(bubble); super.onDestroy(); }
    @Override public IBinder onBind(Intent i){return null;}
}
