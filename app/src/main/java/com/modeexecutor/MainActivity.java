package com.modeexecutor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(32,32,32,32);
        TextView t = new TextView(this);
        t.setText("Mod Executor\n\nMenu flutuante + editor Lua + sessão de 10 minutos.\n\nA execução Lua é somente em sandbox/local.");
        t.setTextSize(18);
        l.addView(t);
        Button p = new Button(this);
        p.setText("Permitir botão flutuante");
        p.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this))
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));
            else startService(new Intent(this, FloatingService.class));
        });
        l.addView(p);
        setContentView(l);
    }
    @Override protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) startService(new Intent(this, FloatingService.class));
    }
}
