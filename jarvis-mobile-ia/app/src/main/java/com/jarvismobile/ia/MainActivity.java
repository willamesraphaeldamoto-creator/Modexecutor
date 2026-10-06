package com.jarvismobile.ia;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText command;
    private LinearLayout messages;
    private TextView downloadStatus;
    private ProgressBar downloadProgress;
    private Button downloadButton;

    private static final String MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf";
    private static final String MODEL_NAME = "qwen2.5-0.5b-instruct-q4_0.gguf";
    private static final int NOTIFICATION_ID = 44;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildChatUi();
        createNotificationChannel();
        requestNotifications();
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void buildChatUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 20, 20));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(12), dp(18), dp(12));
        TextView title = text("JARVIS", 22, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView sub = text("●  local", 13, Color.rgb(130, 200, 145));
        top.addView(sub);
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(14), dp(10), dp(14), dp(12));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addMessage("Olá! Eu sou o JARVIS. Como posso ajudar?", false);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12), dp(8), dp(12), dp(10));
        bottom.setBackgroundColor(Color.rgb(20,20,20));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        command = new EditText(this);
        command.setSingleLine(false);
        command.setHint("Mensagem para JARVIS");
        command.setHintTextColor(Color.rgb(150,150,150));
        command.setTextColor(Color.WHITE);
        command.setPadding(dp(16), dp(8), dp(16), dp(8));
        command.setBackground(bg(Color.rgb(45,45,45), 28));
        inputRow.addView(command, new LinearLayout.LayoutParams(0, dp(54), 1));

        Button send = new Button(this);
        send.setText("↑");
        send.setTextSize(20);
        send.setTextColor(Color.WHITE);
        send.setBackground(bg(Color.rgb(55,55,55), 28));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(54), dp(54));
        sp.setMargins(dp(8),0,0,0);
        inputRow.addView(send, sp);
        send.setOnClickListener(v -> runCommand());

        bottom.addView(inputRow);

        downloadStatus = text("Modelo local pronto para baixar quando você quiser.", 12, Color.rgb(160,160,160));
        downloadStatus.setPadding(dp(4), dp(6), dp(4), 0);
        bottom.addView(downloadStatus);

        downloadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setMax(100);
        downloadProgress.setProgress(0);
        bottom.addView(downloadProgress, new LinearLayout.LayoutParams(-1, dp(5)));

        downloadButton = new Button(this);
        downloadButton.setText("BAIXAR MODELO LOCAL • 429 MB");
        downloadButton.setTextColor(Color.WHITE);
        downloadButton.setBackground(bg(Color.rgb(45,45,45), 22));
        downloadButton.setOnClickListener(v -> downloadModel());
        bottom.addView(downloadButton, new LinearLayout.LayoutParams(-1, dp(46)));

        Button access = new Button(this);
        access.setText("Ativar controle autorizado do celular");
        access.setTextColor(Color.LTGRAY);
        access.setBackgroundColor(Color.TRANSPARENT);
        access.setOnClickListener(v -> startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS")));
        bottom.addView(access, new LinearLayout.LayoutParams(-1, dp(42)));

        root.addView(bottom);
        setContentView(root);
    }

    private void addMessage(String value, boolean user) {
        TextView bubble = text(value, 16, Color.WHITE);
        bubble.setPadding(dp(16), dp(11), dp(16), dp(11));
        bubble.setBackground(bg(user ? Color.rgb(50,50,50) : Color.rgb(35,35,35), 20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                user ? dp(290) : dp(330), -2);
        p.gravity = user ? Gravity.END : Gravity.START;
        p.setMargins(dp(2), dp(5), dp(2), dp(5));
        messages.addView(bubble, p);
    }

    private void runCommand() {
        String c = command.getText().toString().trim();
        if (c.isEmpty()) return;
        addMessage(c, true);
        command.setText("");

        String low = c.toLowerCase(Locale.ROOT);
        if (low.contains("config")) {
            addMessage("Vou abrir as Configurações. Esta ação é visível para você.", false);
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        } else if (low.contains("wifi")) {
            addMessage("Abrindo as configurações de Wi‑Fi.", false);
            startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
        } else if (low.contains("baix") && low.contains("modelo")) {
            addMessage("Certo. Vou baixar o modelo local com retomada automática.", false);
            downloadModel();
        } else {
            addMessage("Recebi sua mensagem. O modelo local será usado aqui quando a inferência estiver integrada.", false);
        }
    }

    private File modelFile() {
        return new File(getFilesDir(), MODEL_NAME);
    }

    private File partialFile() {
        return new File(getFilesDir(), MODEL_NAME + ".part");
    }

    private void downloadModel() {
        if (modelFile().exists() && modelFile().length() > 100000000) {
            downloadStatus.setText("Modelo local já está baixado.");
            downloadProgress.setProgress(100);
            return;
        }

        downloadButton.setEnabled(false);
        downloadStatus.setText("Conectando ao servidor do modelo…");
        new Thread(() -> {
            File part = partialFile();
            File finalFile = modelFile();
            long existing = part.exists() ? part.length() : 0;

            try {
                HttpURLConnection c = (HttpURLConnection) new URL(MODEL_URL).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("Accept-Encoding", "identity");
                if (existing > 0) c.setRequestProperty("Range", "bytes=" + existing + "-");
                c.connect();

                int code = c.getResponseCode();
                boolean resumed = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
                if (existing > 0 && !resumed) {
                    existing = 0;
                    part.delete();
                }
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("HTTP " + code);
                }

                long length = c.getContentLengthLong();
                long total = resumed && length > 0 ? existing + length : length;
                long done = resumed ? existing : 0;

                try (InputStream in = new BufferedInputStream(c.getInputStream(), 256 * 1024);
                     FileOutputStream fos = new FileOutputStream(part, resumed)) {
                    byte[] buf = new byte[256 * 1024];
                    int n;
                    long lastUi = 0;
                    while ((n = in.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastUi > 300) {
                            lastUi = now;
                            final long d = done, t = total;
                            runOnUiThread(() -> updateDownload(d, t));
                        }
                    }
                    fos.getFD().sync();
                }
                c.disconnect();

                if (!part.renameTo(finalFile)) {
                    try (InputStream in = new FileInputStream(part);
                         OutputStream out = new FileOutputStream(finalFile)) {
                        byte[] buf = new byte[256 * 1024];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf,0,n);
                    }
                    part.delete();
                }

                runOnUiThread(() -> {
                    downloadProgress.setProgress(100);
                    downloadStatus.setText("Modelo baixado e salvo no armazenamento privado.");
                    downloadButton.setText("MODELO INSTALADO ✓");
                    downloadButton.setEnabled(true);
                    showNotification("JARVIS", "Modelo local baixado com sucesso.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    downloadButton.setEnabled(true);
                    downloadStatus.setText("Download pausado: " + e.getMessage() + " • toque novamente para continuar.");
                });
                showNotification("JARVIS", "Download do modelo interrompido. Você pode continuar depois.");
            }
        }).start();
    }

    private void updateDownload(long done, long total) {
        int percent = total > 0 ? (int)Math.min(100, (done * 100L) / total) : 0;
        downloadProgress.setProgress(percent);
        String mb = String.format(Locale.US, "%.0f", done / 1048576.0);
        String totalMb = total > 0 ? String.format(Locale.US, "%.0f", total / 1048576.0) : "?";
        downloadStatus.setText("Baixando modelo… " + percent + "% (" + mb + " / " + totalMb + " MB)");
        showNotification("JARVIS • baixando modelo", percent + "% concluído");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    "jarvis", "JARVIS", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Status do JARVIS e downloads");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 90);
        }
    }

    private void showNotification(String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "jarvis")
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle(title)
         .setContentText(body)
         .setOngoing(body.contains("%"));
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, b.build());
    }
}
