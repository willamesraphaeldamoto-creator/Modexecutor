package com.modeexecutor;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingService extends Service {
    private WindowManager wm;
    private View bubble, panel;
    private CountDownTimer timer;
    private TextView clock, output, title;
    private EditText code;

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private TextView label(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private TextView action(String s) {
        TextView t = label(s, 12, Color.WHITE);
        t.setGravity(Gravity.CENTER); t.setBackground(bg(Color.rgb(39,35,52), 14));
        return t;
    }

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        TextView b = label("M", 18, Color.WHITE);
        b.setGravity(Gravity.CENTER); b.setBackground(bg(Color.rgb(132,65,225), 30));
        b.setElevation(dp(10)); b.setOnClickListener(v -> showPanel());
        bubble = b;
        wm.addView(bubble, params(dp(58), dp(58), Gravity.RIGHT|Gravity.CENTER_VERTICAL, false));
    }

    private WindowManager.LayoutParams params(int w,int h,int g,boolean focus) {
        int flags = focus ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        return new WindowManager.LayoutParams(w,h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
    }

    private void showPanel() {
        if(panel != null) return;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(16));
        root.setBackground(bg(Color.rgb(15,13,21),26));
        root.setElevation(dp(18));

        LinearLayout header = new LinearLayout(this);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        title = label("MOD EXECUTOR",19,Color.WHITE);
        TextView sub = label("PERSONAL WORKSPACE",10,Color.rgb(160,150,175));
        brand.addView(title,new LinearLayout.LayoutParams(-1,dp(28)));
        brand.addView(sub,new LinearLayout.LayoutParams(-1,dp(20)));
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(52),1));
        clock = label("10:00",12,Color.rgb(220,175,255));
        clock.setGravity(Gravity.CENTER); clock.setBackground(bg(Color.rgb(45,27,60),18));
        header.addView(clock,new LinearLayout.LayoutParams(dp(64),dp(36)));
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0,dp(12),0,dp(10));
        TextView exec=action("Executor"), chars=action("Personagens"), ai=action("IA");
        tabs.addView(exec,new LinearLayout.LayoutParams(0,dp(42),1));
        tabs.addView(chars,new LinearLayout.LayoutParams(0,dp(42),1));
        tabs.addView(ai,new LinearLayout.LayoutParams(0,dp(42),1));
        root.addView(tabs);

        code = new EditText(this);
        code.setTextColor(Color.WHITE); code.setHintTextColor(Color.rgb(110,103,125));
        code.setHint("Digite seu comando Lua...\nEnter = executar");
        code.setTextSize(14); code.setGravity(Gravity.TOP|Gravity.START);
        code.setSingleLine(false); code.setMinLines(7);
        code.setPadding(dp(14),dp(14),dp(14),dp(14));
        code.setBackground(bg(Color.rgb(25,22,33),18));
        code.setImeOptions(EditorInfo.IME_ACTION_DONE);
        root.addView(code,new LinearLayout.LayoutParams(-1,dp(190)));

        TextView run=label("▶   EXECUTAR",13,Color.WHITE);
        run.setGravity(Gravity.CENTER); run.setBackground(bg(Color.rgb(132,65,225),16));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(50)); rp.topMargin=dp(10);
        root.addView(run,rp);

        output=label("Pronto • execução somente no sandbox local.",11,Color.rgb(155,148,170));
        output.setPadding(dp(4),dp(8),dp(4),dp(8)); root.addView(output);

        TextView close=action("Fechar"); root.addView(close,new LinearLayout.LayoutParams(-1,dp(42)));

        run.setOnClickListener(v->execute());
        code.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_DONE ||
               (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER &&
                event.getAction()==KeyEvent.ACTION_DOWN)){ execute(); return true; }
            return false;
        });
        exec.setOnClickListener(v->{title.setText("MOD EXECUTOR");output.setText("Executor ativo • sandbox local");});
        chars.setOnClickListener(v->showCharacters());
        ai.setOnClickListener(v->{title.setText("MOD EXECUTOR • IA");output.setText("IA Amiga: pronta para conversar.");});
        close.setOnClickListener(v->removePanel());

        panel=root;
        WindowManager.LayoutParams lp=params((int)(getResources().getDisplayMetrics().widthPixels*.92f),
            WindowManager.LayoutParams.WRAP_CONTENT,Gravity.CENTER,true);
        wm.addView(panel,lp);

        timer=new CountDownTimer(600000,1000){
            public void onTick(long m){clock.setText(String.format("%d:%02d",m/60000,(m/1000)%60));}
            public void onFinish(){removePanel();}
        }.start();
    }

    private void execute(){
        String s=code.getText().toString().trim();
        if(s.isEmpty()) output.setText("Digite um comando primeiro.");
        else if(s.startsWith("print")||s.startsWith("return")||s.startsWith("--"))
            output.setText("✓ Comando aceito no sandbox.");
        else output.setText("Comando bloqueado pelo sandbox.");
    }

    private void showCharacters(){
        title.setText("PERSONAGENS");
        output.setText("Personagem selecionado no sandbox.");
        code.setText("-- personagem: Default\nreturn 'Personagem selecionado'");
    }

    private void removePanel(){
        if(timer!=null){timer.cancel();timer=null;}
        if(panel!=null){wm.removeView(panel);panel=null;}
    }
    @Override public void onDestroy(){removePanel();if(bubble!=null)wm.removeView(bubble);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}