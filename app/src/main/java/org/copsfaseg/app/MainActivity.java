package org.copsfaseg.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;

    private WebView web;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private boolean showingOffline = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, 8);
        root.addView(progress, pl);
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setBuiltInZoomControls(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, cd, mime, len) -> openExternal(Uri.parse(url)));

        if (b != null) web.restoreState(b); else web.loadUrl(BuildConfig.APP_URL);
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override public void onBackPressed() {
        if (showingOffline) { finish(); return; }
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (ActivityNotFoundException e) { toast("Aucune application pour ouvrir ce lien."); }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    // ───────── Navigation ─────────
    private class Client extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            if (!r.isForMainFrame()) return false;
            Uri u = r.getUrl();
            String sch = u.getScheme() == null ? "" : u.getScheme();
            String host = u.getHost() == null ? "" : u.getHost();
            if (sch.equals("http") || sch.equals("https")) {
                if (host.endsWith("google.com") || host.endsWith("googleusercontent.com")) {
                    // Pièces Drive / Sheets : navigateur externe ; portail Apps Script : reste dans l'application
                    if (host.equals("script.google.com") || host.endsWith("googleusercontent.com")) return false;
                }
                openExternal(u);
                return true;
            }
            if (sch.equals("file") || sch.equals("about") || sch.equals("data") || sch.equals("blob")) return false;
            openExternal(u); // tel:, mailto:, whatsapp:, sms:, intent:
            return true;
        }
        @Override public void onPageStarted(WebView v, String url, Bitmap f) { progress.setVisibility(View.VISIBLE); }
        @Override public void onPageFinished(WebView v, String url) { progress.setVisibility(View.GONE); }
        @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame() && !showingOffline) {
                showingOffline = true;
                v.loadUrl("file:///android_asset/offline.html");
            }
        }
    }

    // ───────── Dialogues JS, fenêtres, fichiers ─────────
    private class Chrome extends WebChromeClient {
        @Override public void onProgressChanged(WebView v, int p) { progress.setProgress(p); }

        @Override public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
            new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                .setPositiveButton("OK", (d, w) -> r.confirm())
                .setOnCancelListener(d -> r.confirm()).show();
            return true;
        }
        @Override public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
            new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                .setPositiveButton("OK", (d, w) -> r.confirm())
                .setNegativeButton("Annuler", (d, w) -> r.cancel())
                .setOnCancelListener(d -> r.cancel()).show();
            return true;
        }
        @Override public boolean onJsPrompt(WebView v, String url, String msg, String def, JsPromptResult r) {
            EditText et = new EditText(MainActivity.this);
            et.setText(def);
            new AlertDialog.Builder(MainActivity.this).setMessage(msg).setView(et)
                .setPositiveButton("OK", (d, w) -> r.confirm(et.getText().toString()))
                .setNegativeButton("Annuler", (d, w) -> r.cancel())
                .setOnCancelListener(d -> r.cancel()).show();
            return true;
        }

        // target="_blank" / window.open(url) -> ouvre à l'extérieur
        @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, android.os.Message msg) {
            WebView tmp = new WebView(MainActivity.this);
            tmp.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                    openExternal(r.getUrl()); w.destroy(); return true;
                }
                @Override public void onPageStarted(WebView w, String url, Bitmap f) {
                    if (url != null && !url.equals("about:blank")) { openExternal(Uri.parse(url)); w.stopLoading(); }
                }
            });
            ((WebView.WebViewTransport) msg.obj).setWebView(tmp);
            msg.sendToTarget();
            return true;
        }

        // <input type="file"> : galerie, fichiers ou appareil photo
        @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;

            Intent content = new Intent(Intent.ACTION_GET_CONTENT);
            content.addCategory(Intent.CATEGORY_OPENABLE);
            content.setType("*/*");
            content.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});

            Intent chooser = Intent.createChooser(content, "Choisir un fichier");
            List<Intent> extra = new ArrayList<>();
            try {
                File dir = new File(getCacheDir(), "photos"); dir.mkdirs();
                File f = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
                cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                extra.add(cam);
            } catch (Exception e) { cameraUri = null; }
            if (!extra.isEmpty()) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toArray(new Intent[0]));
            try { startActivityForResult(chooser, REQ_FILE); }
            catch (Exception e) { fileCallback = null; cb.onReceiveValue(null); return false; }
            return true;
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCallback == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                out = new Uri[n];
                for (int i = 0; i < n; i++) out[i] = data.getClipData().getItemAt(i).getUri();
            } else if (cameraUri != null) out = new Uri[]{cameraUri};
        }
        fileCallback.onReceiveValue(out);
        fileCallback = null;
    }

    // ───────── Pont JavaScript ─────────
    public class Bridge {
        private final Handler ui = new Handler(Looper.getMainLooper());

        @android.webkit.JavascriptInterface
        public void saveFile(String name, String mime, String base64) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                Uri uri;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(data); }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    File f = new File(dir, safe);
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(data); }
                    uri = Uri.fromFile(f);
                }
                final Uri fu = uri;
                ui.post(() -> {
                    toast("Enregistré dans Téléchargements : " + safe);
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(fu, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) { }
                });
            } catch (Exception e) {
                ui.post(() -> toast("Échec de l'enregistrement : " + e.getMessage()));
            }
        }

        @android.webkit.JavascriptInterface
        public void printHtml(String html) {
            ui.post(() -> {
                WebView pw = new WebView(MainActivity.this);
                pw.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView w, String u) {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        String job = "COPS_FASEG_document";
                        PrintDocumentAdapter ad = w.createPrintDocumentAdapter(job);
                        pm.print(job, ad, new PrintAttributes.Builder().build());
                    }
                });
                pw.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            });
        }

        @android.webkit.JavascriptInterface
        public void retry() {
            ui.post(() -> { showingOffline = false; web.loadUrl(BuildConfig.APP_URL); });
        }
    }
}
