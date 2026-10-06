package com.modeexecutor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(28),dp(22),dp(22));
        root.setBackgroundColor(Color.rgb(12,10,17));

        TextView title=new TextView(this);
        title.setText("MOD EXECUTOR");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(55)));

        TextView info=new TextView(this);
        info.setText("IA + painel para seu próprio jogo\n\nA IA conversa pelo OpenRouter e pode gerar ações autorizadas para a integração do seu jogo. Não controla o cliente Roblox de terceiros.");
        info.setTextColor(Color.rgb(190,182,205));
        info.setTextSize(14);
        root.addView(info,new LinearLayout.LayoutParams(-1,dp(110)));

        EditText key=new EditText(this);
        key.setHint("OpenRouter API key (sk-or-...)");
        key.setHintTextColor(Color.rgb(115,105,130));
        key.setTextColor(Color.WHITE);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(getSharedPreferences("ModExecutor",MODE_PRIVATE).getString("openrouter_key",""));
        root.addView(key,new LinearLayout.LayoutParams(-1,dp(55)));

        EditText model=new EditText(this);
        model.setHint("Modelo (ex.: openrouter/free)");
        model.setHintTextColor(Color.rgb(115,105,130));
        model.setTextColor(Color.WHITE);
        model.setSingleLine(true);
        model.setText(getSharedPreferences("ModExecutor",MODE_PRIVATE).getString("openrouter_model","openrouter/free"));
        root.addView(model,new LinearLayout.LayoutParams(-1,dp(55)));

        Button save=new Button(this);
        save.setText("SALVAR CONFIGURAÇÃO");
        save.setOnClickListener(v->{
            getSharedPreferences("ModExecutor",MODE_PRIVATE).edit()
                .putString("openrouter_key",key.getText().toString().trim())
                .putString("openrouter_model",model.getText().toString().trim())
                .apply();
        });
        root.addView(save,new LinearLayout.LayoutParams(-1,dp(52)));

        Button overlay=new Button(this);
        overlay.setText("ABRIR MENU FLUTUANTE");
        overlay.setOnClickListener(v->{
            if(!Settings.canDrawOverlays(this)){
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            }else{
                startService(new Intent(this,FloatingService.class));
            }
        });
        root.addView(overlay,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView footer=new TextView(this);
        footer.setText("\n9:16 mobile • IA • sessão de 10 minutos • comandos seguros para jogo próprio");
        footer.setTextColor(Color.rgb(130,120,145));
        footer.setTextSize(12);
        root.addView(footer);

        setContentView(root);
    }

    @Override protected void onResume(){
        super.onResume();
        if(Settings.canDrawOverlays(this)) startService(new Intent(this,FloatingService.class));
    }
}
