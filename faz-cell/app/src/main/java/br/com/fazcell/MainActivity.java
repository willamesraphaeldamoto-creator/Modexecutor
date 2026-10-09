package br.com.fazcell;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    int ink=Color.rgb(25,29,48), purple=Color.rgb(105,76,255);
    LinearLayout page;
    TextView batteryValue,batteryDetail,storageValue;
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    android.graphics.drawable.GradientDrawable bg(int c,int r){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(c);d.setCornerRadius(dp(r));return d;
    }
    TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    void gap(int h){page.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    void card(TextView t){t.setPadding(dp(15),dp(14),dp(15),dp(14));t.setBackground(bg(Color.WHITE,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);page.addView(t,p);}
    void action(String title,String sub,Runnable run){
        LinearLayout b=new LinearLayout(this);b.setOrientation(1);b.setPadding(dp(16),dp(14),dp(16),dp(14));b.setBackground(bg(Color.WHITE,18));
        b.addView(text(title,16,ink,true));TextView s=text(sub,13,Color.rgb(108,109,130),false);s.setPadding(0,dp(5),0,0);b.addView(s);
        b.setClickable(true);b.setFocusable(true);b.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);page.addView(b,p);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246,245,255));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(1);
        page.setPadding(dp(20),dp(22),dp(20),dp(28));page.setBackgroundColor(Color.rgb(246,245,255));scroll.addView(page);setContentView(scroll);
        page.addView(text("FAZ CELL",29,ink,true));page.addView(text("Seu celular, mais organizado.",15,Color.GRAY,false));gap(20);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(20),dp(20),dp(20),dp(20));hero.setBackground(bg(Color.rgb(43,35,88),24));
        hero.addView(text("Cuide do seu celular",21,Color.WHITE,true));
        TextView desc=text("Ferramentas reais do Android para acompanhar a bateria, gerenciar espaço e ajustar configurações.",14,Color.rgb(224,220,255),false);
        desc.setPadding(0,dp(8),0,dp(8));hero.addView(desc);
        Button refresh=new Button(this);refresh.setText("Atualizar diagnóstico");refresh.setAllCaps(false);refresh.setTextColor(Color.WHITE);refresh.setBackground(bg(purple,14));refresh.setOnClickListener(v->updateStats());hero.addView(refresh);page.addView(hero);gap(18);
        page.addView(text("RESUMO DO DISPOSITIVO",12,Color.rgb(105,95,153),true));gap(8);
        batteryValue=text("Bateria: --",18,ink,true);card(batteryValue);batteryDetail=text("Verificando estado…",13,Color.GRAY,false);card(batteryDetail);
        storageValue=text("Espaço disponível: --",16,ink,true);card(storageValue);gap(18);
        page.addView(text("FERRAMENTAS",12,Color.rgb(105,95,153),true));gap(8);
        action("⚡  Bateria e carregamento","Abrir configurações de bateria",()->openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        action("🧹  Espaço e aplicativos","Gerencie apps e armazenamento",()->openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        action("📱  Desempenho e sistema","Abra as configurações do aparelho",()->openSettings(Settings.ACTION_SETTINGS));
        action("📷  Abrir câmera","Use a câmera instalada no celular",()->{Intent i=new Intent("android.media.action.IMAGE_CAPTURE");if(i.resolveActivity(getPackageManager())!=null)startActivity(i);else Toast.makeText(this,"Câmera não encontrada.",Toast.LENGTH_SHORT).show();});
        action("🖼  Qualidade da tela","Ajuste brilho e tela nas configurações",()->openSettings(Settings.ACTION_DISPLAY_SETTINGS));
        gap(12);page.addView(text("O app não aumenta fisicamente a potência de carregamento nem ultrapassa os limites da câmera. A velocidade depende do carregador, cabo, temperatura e aparelho. Revise arquivos antes de apagar qualquer coisa.",12,Color.rgb(100,101,120),false));
        updateStats();
    }
    void updateStats(){
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(b!=null){int l=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),s=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            int pct=l>=0&&s>0?Math.round(l*100f/s):-1;int st=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            boolean charging=st==BatteryManager.BATTERY_STATUS_CHARGING||st==BatteryManager.BATTERY_STATUS_FULL;
            batteryValue.setText("Bateria: "+(pct>=0?pct+"%":"indisponível"));
            batteryDetail.setText(charging?"Carregando ou totalmente carregada":"Não está carregando no momento");}
        File f=getFilesDir();storageValue.setText("Espaço da partição do app: "+(f.getUsableSpace()/(1024*1024))+" MB livres");
    }
    void openSettings(String action){try{startActivity(new Intent(action));}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception ignored){Toast.makeText(this,"Não foi possível abrir as configurações.",Toast.LENGTH_SHORT).show();}}}
}
