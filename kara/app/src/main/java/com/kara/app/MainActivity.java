package com.kara.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends Activity {
  WebView web; FirebaseAuth auth;
  @Override public void onCreate(Bundle b){super.onCreate(b);auth=FirebaseAuth.getInstance();web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient());web.addJavascriptInterface(new Bridge(),"KaraNative");setContentView(web);web.loadUrl("file:///android_asset/index.html");}
  void js(String s){runOnUiThread(()->web.evaluateJavascript(s,null));}
  class Bridge{
    @JavascriptInterface public void login(String e,String p){auth.signInWithEmailAndPassword(e,p).addOnCompleteListener(t->js(t.isSuccessful()?"karaAuthSuccess('login')":"karaAuthError('E-mail ou senha inválidos')"));}
    @JavascriptInterface public void signup(String e,String p){auth.createUserWithEmailAndPassword(e,p).addOnCompleteListener(t->js(t.isSuccessful()?"karaAuthSuccess('signup')":"karaAuthError('Não foi possível criar a conta')"));}
    @JavascriptInterface public void reset(String e){auth.sendPasswordResetEmail(e).addOnCompleteListener(t->js(t.isSuccessful()?"karaAuthSuccess('reset')":"karaAuthError('Falha ao enviar recuperação')"));}
    @JavascriptInterface public String user(){FirebaseUser u=auth.getCurrentUser();return u==null?"":String.valueOf(u.getEmail());}
    @JavascriptInterface public void logout(){auth.signOut();js("location.reload()");}
  }
}
