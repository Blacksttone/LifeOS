package com.vaazhkai.tracker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    private WebView web;
    final BackupFiles backups = new BackupFiles(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Keep the page clear of the status bar, navigation bar and keyboard (edge-to-edge on Android 15+).
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(this), "AndroidBridge");

        setContentView(root);
        web.loadUrl("https://" + HOST + "/assets/index.html");

        ReminderScheduler.scheduleAll(this);
        try { Geo.registerAll(this, null); } catch (Exception ignored) {}
    }

    /** Runs a script in the page from any thread. */
    void runJs(final String js) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    void requestLocationPermission(boolean background) {
        if (!background || !Geo.hasFine(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
        } else if (Build.VERSION.SDK_INT >= 29) {
            // On Android 11+ this opens the app's location settings, where the user picks "Allow all the time".
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 3);
        }
    }

    void openAppSettings() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {}
    }

    void currentLocation(final String id) {
        if (!Geo.hasFine(this)) { runJs("window.__locationDone && window.__locationDone(" + org.json.JSONObject.quote(id) + ",null,null,null,'no_permission')"); return; }
        try {
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                            new com.google.android.gms.tasks.CancellationTokenSource().getToken())
                    .addOnSuccessListener(loc -> {
                        if (loc == null) runJs("window.__locationDone(" + org.json.JSONObject.quote(id) + ",null,null,null,'no_fix')");
                        else runJs("window.__locationDone(" + org.json.JSONObject.quote(id) + "," + loc.getLatitude() + "," + loc.getLongitude() + "," + loc.getAccuracy() + ",null)");
                    })
                    .addOnFailureListener(e -> runJs("window.__locationDone(" + org.json.JSONObject.quote(id) + ",null,null,null,'error')"));
        } catch (SecurityException e) {
            runJs("window.__locationDone(" + org.json.JSONObject.quote(id) + ",null,null,null,'no_permission')");
        }
    }

    void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!backups.onResult(requestCode, resultCode, data)) super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (web != null) web.evaluateJavascript("window.__notifChanged && window.__notifChanged()", null);
        if (requestCode == 2 || requestCode == 3) {
            try { Geo.registerAll(this, null); } catch (Exception ignored) {}
            if (web != null) web.evaluateJavascript("window.__locChanged && window.__locChanged()", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Settings: the location permission may have changed.
        if (web != null) web.evaluateJavascript("window.__locChanged && window.__locChanged()", null);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
