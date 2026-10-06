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
    private static final String MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf";

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

        Button model = new Button(this);
        model.setText("BAIXAR MODELO LOCAL (≈429 MB)");
        model.setOnClickListener(v -> downloadModel());
        root.addView(model);

        setContentView(root);
    }

    private void downloadModel() {
        status.setText("Baixando o modelo local...");
        new Thread(() -> {
            java.io.File out = new java.io.File(getFilesDir(), "qwen2.5-0.5b-instruct-q4_0.gguf");
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(MODEL_URL).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.connect();
                if (c.getResponseCode() / 100 != 2) {
                    throw new java.io.IOException("HTTP " + c.getResponseCode());
                }
                try (java.io.InputStream in = c.getInputStream();
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    byte[] buf = new byte[1024 * 64];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                    }
                }
                runOnUiThread(() -> status.setText("Modelo local baixado. Arquivo salvo no armazenamento privado do JARVIS."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Falha no download: " + e.getMessage()));
            }
        }).start();
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
