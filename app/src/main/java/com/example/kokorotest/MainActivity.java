package com.example.kokorotest;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
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
                android.net.Uri u = r.getUrl();
                if ("huggingface.co".equals(u.getHost()) && u.getPath() != null && u.getPath().endsWith("/onnx/model_quantized.onnx")) {
                    java.io.File f = new java.io.File(getFilesDir(), "model_quantized.onnx");
                    if (f.exists()) {
                        try {
                            java.util.Map<String, String> h = new java.util.HashMap<>();
                            h.put("Access-Control-Allow-Origin", "*");
                            h.put("Content-Length", String.valueOf(f.length()));
                            return new WebResourceResponse("application/octet-stream", null, 200, "OK", h, new java.io.FileInputStream(f));
                        } catch (Exception e) { }
                    }
                }
                return loader.shouldInterceptRequest(u);
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

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 77 || res != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        new Thread(() -> {
            String msg;
            try {
                java.io.File out = new java.io.File(getFilesDir(), "model_quantized.onnx");
                java.io.File tmp = new java.io.File(getFilesDir(), "model.tmp");
                long n = 0;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 16];
                    int c;
                    while ((c = in.read(buf)) > 0) { fo.write(buf, 0, c); n += c; }
                }
                if (out.exists()) out.delete();
                tmp.renameTo(out);
                msg = "Copied " + (n >> 20) + " MB. The real file is about 92 MB, so check the number is close.";
            } catch (Exception e) { msg = "Copy failed: " + e; }
            final String m = msg;
            runOnUiThread(() -> { if (web != null) web.evaluateJavascript("window.modelPicked(" + JSONObject.quote(m) + ")", null); });
        }).start();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    class Bridge {
        @JavascriptInterface public void pickModel() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, 77);
            });
        }

        @JavascriptInterface public String localStatus() {
            java.io.File f = new java.io.File(getFilesDir(), "model_quantized.onnx");
            return f.exists() ? ("found, " + (f.length() >> 20) + " MB, will be used instead of downloading") : "none chosen, will download";
        }

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
                java.io.File lf = new java.io.File(getFilesDir(), "model_quantized.onnx");
                o.put("localModel", lf.exists() ? ("found, " + (lf.length() >> 20) + " MB, will be used instead of downloading") : "not found, will download");
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
    }
}
