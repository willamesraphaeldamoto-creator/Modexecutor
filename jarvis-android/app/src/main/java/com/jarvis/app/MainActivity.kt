package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val fields = ArrayList<Pair<EditText, String>>()
    private lateinit var out: TextView
    private lateinit var prog: TextView
    private lateinit var modeBtn: Button
    private lateinit var wakeBtn: Button
    private lateinit var voice: Voice
    private lateinit var ls: Listener

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(t: String, f: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { f() }
        return b
    }

    private fun label(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(0xFF7DD3FC.toInt())
        v.textSize = 14f
        v.setPadding(0, dp(16), 0, dp(4))
        return v
    }

    private fun edit(hintText: String, key: String, def: String = "", pw: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hintText
        e.setText(Store.get(this, key, def))
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(0xFF64748B.toInt())
        e.textSize = 13f
        e.setSingleLine()
        if (pw) e.transformationMethod = PasswordTransformationMethod.getInstance()
        fields.add(e to key)
        return e
    }

    private fun go(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        voice = Voice(this)
        ls = Listener(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(28), dp(16), dp(40))
        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF0B1220.toInt())
        sv.addView(col)
        fun add(v: View) = col.addView(v)

        val title = TextView(this)
        title.text = "J.A.R.V.I.S"
        title.textSize = 30f
        title.setTextColor(0xFF38BDF8.toInt())
        add(title)

        add(label("Configuração (faça na ordem)"))
        add(btn("1. Permissões (microfone, contatos, ligações)") { perms() })
        add(btn("2. Ativar Acessibilidade (automação)") { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        add(btn("3. Permitir sobrepor outros apps") {
            go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        add(btn("4. Definir como assistente padrão (botão)") { go(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) })
        add(btn("5. Sem restrição de bateria") { go(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        wakeBtn = btn("") { toggleWake() }
        add(wakeBtn)

        add(label("OpenRouter (modelos NVIDIA free)"))
        add(edit("Chave sk-or-...", Store.KEY, pw = true))
        add(edit("Modelos separados por vírgula (tenta em ordem)", Store.MODEL, Store.DEF_MODEL))
        modeBtn = btn("") {
            val m = listOf("auto", "openrouter", "local")
            Store.set(this, Store.MODE, m[(m.indexOf(Store.get(this, Store.MODE, "auto")) + 1) % 3])
            refresh()
        }
        add(modeBtn)

        add(label("Modelo local (MediaPipe .task)"))
        add(edit("URL do modelo (.task)", Store.LOCAL_URL, Store.DEF_URL))
        add(edit("Token HuggingFace (só se o modelo exigir)", Store.HF, pw = true))
        add(btn("Baixar modelo") { download() })
        add(btn("Escolher arquivo já baixado") { pick() })
        prog = TextView(this)
        prog.setTextColor(Color.LTGRAY)
        prog.text = if (Store.get(this, Store.LOCAL_PATH).isBlank()) "Nenhum modelo local." else "Modelo local pronto."
        add(prog)

        add(label("Conversar / testar comandos"))
        val inp = EditText(this)
        inp.hint = "Ex: abrir whatsapp"
        inp.setTextColor(Color.WHITE)
        inp.setHintTextColor(0xFF64748B.toInt())
        add(inp)
        add(btn("Enviar") { send(inp.text.toString()); inp.setText("") })
        add(btn("🎤 Falar agora") { talk() })
        out = TextView(this)
        out.setTextColor(Color.WHITE)
        out.textSize = 15f
        out.setPadding(0, dp(12), 0, 0)
        add(out)

        setContentView(sv)
        refresh()
    }

    private fun refresh() {
        wakeBtn.text = if (Store.get(this, "wake") == "1") "⏹ Desligar escuta \"Jarvis\"" else "▶ Ligar escuta \"Jarvis\""
        modeBtn.text = "IA: " + when (Store.get(this, Store.MODE, "auto")) {
            "openrouter" -> "só OpenRouter (NVIDIA)"
            "local" -> "só modelo local"
            else -> "automático (online=OpenRouter, offline=local)"
        }
    }

    private fun saveFields() {
        fields.forEach { Store.set(this, it.second, it.first.text.toString().trim()) }
    }

    override fun onPause() { saveFields(); super.onPause() }
    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroy() {
        scope.cancel()
        ls.stop()
        voice.shutdown()
        super.onDestroy()
    }

    private fun perms() {
        val l = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(l.toTypedArray(), 1)
    }

    private fun toggleWake() {
        val on = Store.get(this, "wake") == "1"
        if (!on) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { perms(); return }
            startForegroundService(Intent(this, WakeService::class.java))
            Store.set(this, "wake", "1")
        } else {
            stopService(Intent(this, WakeService::class.java))
            Store.set(this, "wake", "0")
        }
        refresh()
    }

    private fun send(t: String) {
        if (t.isBlank()) return
        saveFields()
        out.text = "Você: $t\n…"
        scope.launch {
            val r = try { Jarvis.handle(this@MainActivity, t) } catch (e: Exception) { "Erro: \${e.message}" }
            out.text = "Você: $t\nJarvis: $r"
            voice.say(r)
        }
    }

    private fun talk() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { perms(); return }
        WakeService.pause()
        out.text = "Ouvindo…"
        ls.start(
            onFinal = { WakeService.resume(); send(it) },
            onFail = { WakeService.resume(); out.text = "Não ouvi (erro $it)" },
            onPartial = { out.text = it }
        )
    }

    private fun ui(s: String) = runOnUiThread { prog.text = s }

    private fun openFollow(url: String, tok: String): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val cn = u.openConnection() as HttpURLConnection
            cn.instanceFollowRedirects = false
            cn.connectTimeout = 20000
            cn.readTimeout = 30000
            if (tok.isNotBlank() && u.host.endsWith("huggingface.co")) cn.setRequestProperty("Authorization", "Bearer $tok")
            val code = cn.responseCode
            if (code in 300..399) {
                u = URL(u, cn.getHeaderField("Location"))
                cn.disconnect()
            } else if (code in 200..299) {
                return cn
            } else {
                throw Exception("HTTP $code (o modelo exige token?)")
            }
        }
        throw Exception("Redirecionamentos demais")
    }

    private fun download() {
        saveFields()
        val url = Store.get(this, Store.LOCAL_URL, Store.DEF_URL).ifBlank { Store.DEF_URL }.trim()
        val tok = Store.get(this, Store.HF)
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "model.task" }
        val dir = File(filesDir, "models").apply { mkdirs() }
        val dest = File(dir, name)
        ui("Conectando…")
        Thread {
            try {
                val cn = openFollow(url, tok)
                val total = cn.contentLengthLong
                val part = File(dir, "$name.part")
                var got = 0L
                var last = 0L
                cn.inputStream.use { i ->
                    part.outputStream().use { o ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val r = i.read(buf)
                            if (r < 0) break
                            o.write(buf, 0, r)
                            got += r
                            if (got - last > 2_000_000) {
                                last = got
                                ui("Baixando… \${got / 1_000_000} MB" + if (total > 0) " / \${total / 1_000_000} MB" else "")
                            }
                        }
                    }
                }
                dest.delete()
                part.renameTo(dest)
                Store.set(this, Store.LOCAL_PATH, dest.absolutePath)
                ui("Modelo pronto: $name")
            } catch (e: Exception) {
                ui("Falha no download: \${e.message}")
            }
        }.start()
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.type = "*/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(i, 7)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc != 7) return
        val uri = d?.data ?: return
        ui("Copiando modelo…")
        Thread {
            try {
                val name = contentResolver.query(uri, null, null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                } ?: "model.task"
                val dir = File(filesDir, "models").apply { mkdirs() }
                val dest = File(dir, name)
                contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                Store.set(this, Store.LOCAL_PATH, dest.absolutePath)
                ui("Modelo pronto: $name")
            } catch (e: Exception) {
                ui("Falha ao copiar: \${e.message}")
            }
        }.start()
    }
}
