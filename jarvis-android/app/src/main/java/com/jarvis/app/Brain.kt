package com.jarvis.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Store {
    const val KEY="or_key"; const val MODEL="or_model"; const val MODE="mode"; const val LOCAL_PATH="local_path"; const val LOCAL_URL="local_url"; const val HF="hf_token"
    const val DEF_MODEL="nvidia/nemotron-3-super-120b-a12b:free,nvidia/nemotron-nano-9b-v2:free,nvidia/nemotron-3-ultra-550b-a55b:free"
    const val DEF_URL="https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    private fun p(c:Context)=c.getSharedPreferences("jarvis",Context.MODE_PRIVATE)
    fun get(c:Context,k:String,d:String="")=p(c).getString(k,d)?:d
    fun set(c:Context,k:String,v:String){p(c).edit().putString(k,v).apply()}
}

object Brain {
    private var llm:LlmInference?=null
    private var llmPath:String?=null
    private val history=ArrayList<Pair<String,String>>()
    suspend fun ask(c:Context,user:String):String {
        val mode=Store.get(c,Store.MODE,"auto")
        if(mode!="local" && Store.get(c,Store.KEY).isNotBlank() && online(c)){
            try{return openRouter(c,user)}catch(_:Exception){}
        }
        if(mode!="openrouter"){
            try{return local(c,user)}catch(_:Exception){}
        }
        return JSONObject().put("say","Ainda não consegui acessar a IA. Configure a chave do OpenRouter ou baixe um modelo local.").put("actions",JSONArray()).toString()
    }
    fun parse(raw:String):Pair<String,JSONArray>{
        return try{val j=JSONObject(raw.trim().substringAfter("{").let{"{"+it});Pair(j.optString("say"),j.optJSONArray("actions")?:JSONArray())}
        catch(_:Exception){Pair(raw.replace(Regex("[{}]"),""),JSONArray())}
    }
    fun remember(u:String,a:String){if(history.size>20)history.removeAt(0);history.add(u to a)}
    private fun online(c:Context):Boolean{val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val n=cm.activeNetwork?:return false;return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}
    private suspend fun openRouter(c:Context,user:String)=withContext(Dispatchers.IO){
        val body=JSONObject().put("model",Store.get(c,Store.MODEL,Store.DEF_MODEL).split(",").first()).put("messages",JSONArray().put(JSONObject().put("role","system").put("content","Você é JARVIS. Responda em português e somente JSON com say e actions.")).put(JSONObject().put("role","user").put("content",user)))
        val cn=URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        cn.requestMethod="POST";cn.doOutput=true;cn.setRequestProperty("Authorization","Bearer "+Store.get(c,Store.KEY));cn.setRequestProperty("Content-Type","application/json")
        cn.outputStream.use{it.write(body.toString().toByteArray())}
        if(cn.responseCode !in 200..299)throw Exception("HTTP ${cn.responseCode}")
        val txt=cn.inputStream.bufferedReader().readText();val msg=JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        msg
    }
    private fun local(c:Context,user:String):String{
        val path=Store.get(c,Store.LOCAL_PATH);if(path.isBlank()||!File(path).exists())throw Exception("Modelo local não baixado")
        if(llm==null||llmPath!=path){llm?.close();llm=LlmInference.createFromOptions(c.applicationContext,LlmInference.LlmInferenceOptions.builder().setModelPath(path).setMaxTokens(512).build());llmPath=path}
        return llm!!.generateResponse("Você é JARVIS, responda em português e somente JSON com say e actions. Usuário: $user")
    }
}