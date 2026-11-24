package com.vaazhkai.tracker;

import android.content.Intent;
import android.webkit.JavascriptInterface;

/** Methods the tracker page calls as window.AndroidBridge.*. */
public class Bridge {
    private final MainActivity activity;

    Bridge(MainActivity activity) { this.activity = activity; }

    @JavascriptInterface
    public boolean isApp() { return true; }

    /* Experts (Claude with web search). The API key stays in a no-backup file on this phone. */
    private java.io.File keyFile() { return new java.io.File(activity.getNoBackupFilesDir(), "expert_key"); }

    private String readKey() {
        try { return new String(java.nio.file.Files.readAllBytes(keyFile().toPath()), java.nio.charset.StandardCharsets.UTF_8).trim(); }
        catch (Exception e) { return ""; }
    }

    @JavascriptInterface
    public boolean hasExpertKey() { return !readKey().isEmpty(); }

    @JavascriptInterface
    public boolean setExpertKey(String key) {
        try {
            String k = key == null ? "" : key.trim();
            if (k.isEmpty()) { keyFile().delete(); return true; }
            java.nio.file.Files.write(keyFile().toPath(), k.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void askExpert(final String id, final String system, final String history) {
        final String key = readKey();
        new Thread(() -> {
            String result = key.isEmpty()
                    ? "{\"ok\":false,\"error\":\"Add your Anthropic API key first.\"}"
                    : ExpertsApi.ask(key, system, history);
            activity.runJs("window.__expertDone && window.__expertDone(" + org.json.JSONObject.quote(id) + "," + org.json.JSONObject.quote(result) + ")");
        }).start();
    }

    /* Full-screen reminders */
    @JavascriptInterface
    public String takeDoneReminders() { return Alarms.takeDone(activity); }

    @JavascriptInterface
    public boolean fullScreenAllowed() { return Alarms.fullScreenAllowed(activity); }

    @JavascriptInterface
    public void openFullScreenSettings() {
        activity.runOnUiThread(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    activity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            android.net.Uri.parse("package:" + activity.getPackageName())));
                } else activity.openAppSettings();
            } catch (Exception e) { activity.openAppSettings(); }
        });
    }

    /* Location reminders */
    @JavascriptInterface
    public String locationStatus() { return Geo.status(activity); }

    @JavascriptInterface
    public void requestLocation(boolean background) { activity.runOnUiThread(() -> activity.requestLocationPermission(background)); }

    @JavascriptInterface
    public void openAppSettings() { activity.runOnUiThread(activity::openAppSettings); }

    @JavascriptInterface
    public void getLocation(String id) { activity.runOnUiThread(() -> activity.currentLocation(id)); }

    @JavascriptInterface
    public void setGeofences(String json) {
        Geo.save(activity, json);
        activity.runOnUiThread(() -> Geo.registerAll(activity, (ok, msg) ->
                activity.runJs("window.__geoStatus && window.__geoStatus(" + ok + "," + org.json.JSONObject.quote(msg) + ")")));
    }

    /* Backup to a file (Google Drive or anywhere the system picker offers). */
    @JavascriptInterface
    public void backupPick(String json, String name) { activity.backups.pickAndSave(json, name); }

    @JavascriptInterface
    public void backupSave(String json) { activity.backups.saveAgain(json); }

    @JavascriptInterface
    public String backupFileName() { return activity.backups.savedName(); }

    @JavascriptInterface
    public boolean hasBackupFile() { return activity.backups.savedUri() != null; }

    @JavascriptInterface
    public void backupForget() { activity.backups.forget(); }

    @JavascriptInterface
    public void restorePick() { activity.backups.pickAndRestore(); }

    @JavascriptInterface
    public void restoreSaved() { activity.backups.restoreSaved(); }

    /**
     * Fetches JSON for live prices outside the WebView, so sites without CORS headers work.
     * Only the price sources the app uses are allowed. The answer arrives as
     * window.__nativeFetchDone(id, httpStatus, bodyOrNull).
     */
    @JavascriptInterface
    public void fetch(final String id, final String url) {
        new Thread(() -> {
            int code = 0;
            String body = null;
            try {
                java.net.URL u = new java.net.URL(url);
                String host = u.getHost();
                boolean allowed = "https".equals(u.getProtocol())
                        && ("api.mfapi.in".equals(host) || host.endsWith(".finance.yahoo.com"));
                if (allowed) {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LifeOS");
                    c.setRequestProperty("Accept", "application/json");
                    code = c.getResponseCode();
                    java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (in != null) {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0 && out.size() < 2_000_000) out.write(buf, 0, n);
                        in.close();
                        body = out.toString("UTF-8");
                    }
                    c.disconnect();
                }
            } catch (Exception e) {
                code = 0;
                body = null;
            }
            activity.runJs("window.__nativeFetchDone && window.__nativeFetchDone(" + org.json.JSONObject.quote(id) + "," + code + ","
                    + (body == null ? "null" : org.json.JSONObject.quote(body)) + ")");
        }).start();
    }

    @JavascriptInterface
    public int versionCode() {
        try {
            android.content.pm.PackageInfo pi = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        }
        catch (Exception e) { return 0; }
    }

    @JavascriptInterface
    public String versionName() {
        try { return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    @JavascriptInterface
    public String updateRepo() { return activity.getString(R.string.update_repo); }

    @JavascriptInterface
    public void setReminders(String json) {
        ReminderScheduler.save(activity, json);
        ReminderScheduler.scheduleAll(activity);
    }

    @JavascriptInterface
    public boolean notificationsAllowed() { return activity.notificationsAllowed(); }

    @JavascriptInterface
    public void requestNotifications() {
        activity.runOnUiThread(activity::requestNotificationPermission);
    }

    @JavascriptInterface
    public void share(String text, String subject) {
        activity.runOnUiThread(() -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, subject);
            send.putExtra(Intent.EXTRA_TEXT, text);
            try { activity.startActivity(Intent.createChooser(send, subject)); } catch (Exception ignored) {}
        });
    }
}
