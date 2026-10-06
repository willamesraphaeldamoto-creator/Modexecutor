package com.jarvismobile.ia;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText command;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);
        root.setBackgroundColor(Color.rgb(5,11,18));

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.jarvismobile.ia.R.drawable.jarvis_logo);
        root.addView(logo, new LinearLayout.LayoutParams(-1, 180));

        TextView title = new TextView(this);
        title.setText("JARVIS Mobile IA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setText("Pronto. Diga o que você precisa.");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(16);
        status.setPadding(0,20,0,20);
        root.addView(status);

        command = new EditText(this);
        command.setHint("Ex.: abrir configurações");
        command.setTextColor(Color.WHITE);
        command.setHintTextColor(Color.GRAY);
        root.addView(command, new LinearLayout.LayoutParams(-1, 60));

        Button send = new Button(this);
        send.setText("EXECUTAR");
        send.setOnClickListener(v -> runCommand());
        root.addView(send);

        Button accessibility = new Button(this);
        accessibility.setText("ATIVAR CONTROLE DO CELULAR");
        accessibility.setOnClickListener(v ->
            startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS")));
        root.addView(accessibility);

        setContentView(root);
    }

    private void runCommand() {
        String c = command.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) return;
        status.setText("Entendi: " + c + "\nVou pedir confirmação antes de ações sensíveis.");
        Intent i = new Intent(Intent.ACTION_VIEW);
        if (c.contains("config")) {
            i = new Intent(android.provider.Settings.ACTION_SETTINGS);
            startActivity(i);
        } else if (c.contains("wifi")) {
            i = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
            startActivity(i);
        } else {
            Toast.makeText(this, "Comando recebido. Motor local será conectado nesta camada.", Toast.LENGTH_LONG).show();
        }
    }
}
