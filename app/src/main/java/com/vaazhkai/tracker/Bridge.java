package com.vaazhkai.tracker;

import android.content.Intent;
import android.webkit.JavascriptInterface;

/** Methods the tracker page calls as window.AndroidBridge.*. */
public class Bridge {
    private final MainActivity activity;

    Bridge(MainActivity activity) { this.activity = activity; }

    @JavascriptInterface
    public boolean isApp() { return true; }

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
