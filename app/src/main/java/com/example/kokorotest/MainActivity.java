package com.example.kokorotest;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;

public class MainActivity extends Activity {
    WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                String why = d.didCrash() ? "web process crashed" : "web process killed by Android (most likely out of memory)";
                getSharedPreferences("t", 0).edit().putString("crash", why + " | last step reached: " + getSharedPreferences("t", 0).getString("mark", "none") + " | " + new java.util.Date()).commit();
                recreate();
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    class Bridge {
        @JavascriptInterface public void mark(String m) {
            getSharedPreferences("t", 0).edit().putString("mark", m).commit();
        }

        @JavascriptInterface public String mem() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                Debug.MemoryInfo d = new Debug.MemoryInfo();
                Debug.getMemoryInfo(d);
                JSONObject o = new JSONObject();
                o.put("availMB", mi.availMem >> 20);
                o.put("totalMB", mi.totalMem >> 20);
                o.put("thresholdMB", mi.threshold >> 20);
                o.put("low", mi.lowMemory);
                o.put("appPssMB", d.getTotalPss() / 1024);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public String info() {
            String wv = "unknown";
            try {
                PackageInfo p = WebView.getCurrentWebViewPackage();
                if (p != null) wv = p.packageName + " " + p.versionName;
            } catch (Throwable t) { }
            SharedPreferences sp = getSharedPreferences("t", 0);
            String crash = sp.getString("crash", "");
            sp.edit().remove("crash").apply();
            try {
                JSONObject o = new JSONObject();
                o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
                o.put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                o.put("webview", wv);
                o.put("lastCrash", crash);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
    }
}
