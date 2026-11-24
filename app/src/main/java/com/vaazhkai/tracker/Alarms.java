package com.vaazhkai.tracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;

/**
 * Full-screen reminders. "Alarm" ones keep ringing (alarm volume) until handled; the others play
 * the normal notification sound once. Both open a full-screen page over the lock screen where the
 * user swipes right for Done or left for Snooze. Done is queued for the page to apply.
 */
final class Alarms {
    static final String ALARM_CHANNEL = "alarms2";
    static final String FULL_CHANNEL = "reminders_full";
    static final int SNOOZE_MIN = 10;
    private static final String PREFS = "alarm_done";

    private Alarms() {}

    static int notifId(int idx) { return 1000 + idx; }

    private static void ensureChannels(NotificationManager nm) {
        if (nm.getNotificationChannel(ALARM_CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(ALARM_CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminders set to ring as an alarm");
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ch.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 800, 600, 800, 600});
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(FULL_CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(FULL_CHANNEL, "Full-screen reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminders shown full screen");
            ch.enableVibration(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    static Intent extras(Intent i, int idx, String title, String time, boolean insistent, String rid) {
        return i.putExtra("idx", idx).putExtra("title", title).putExtra("time", time)
                .putExtra("insistent", insistent).putExtra("rid", rid == null ? "" : rid);
    }

    static void ring(Context c, int idx, String title, String time, boolean insistent, String rid) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannels(nm);

        Intent full = extras(new Intent(c, AlarmActivity.class), idx, title, time, insistent, rid)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(c, 3000 + idx, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent done = PendingIntent.getBroadcast(c, 4000 + idx,
                extras(new Intent(c, AlarmActionReceiver.class).setAction("com.vaazhkai.tracker.ALARM_DONE"), idx, title, time, insistent, rid),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snooze = PendingIntent.getBroadcast(c, 5000 + idx,
                extras(new Intent(c, AlarmActionReceiver.class).setAction("com.vaazhkai.tracker.ALARM_SNOOZE"), idx, title, time, insistent, rid),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(c, R.drawable.ic_notify);

        Notification n = new Notification.Builder(c, insistent ? ALARM_CHANNEL : FULL_CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText((insistent ? "Alarm · " : "Reminder · ") + (time == null ? "" : time))
                .setCategory(insistent ? Notification.CATEGORY_ALARM : Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(insistent)
                .setAutoCancel(!insistent)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .addAction(new Notification.Action.Builder(icon, "Done", done).build())
                .addAction(new Notification.Action.Builder(icon, "Snooze " + SNOOZE_MIN + " min", snooze).build())
                .build();
        if (insistent) n.flags |= Notification.FLAG_INSISTENT;   // keep ringing until handled
        nm.notify(notifId(idx), n);
    }

    static void stop(Context c, int idx) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notifId(idx));
    }

    /** Stop and remember that this reminder was done today; the page applies it when opened. */
    static void done(Context c, int idx, String rid) {
        stop(c, idx);
        if (rid == null || rid.isEmpty()) return;
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray q = new JSONArray(p.getString("q", "[]"));
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            q.put(new org.json.JSONObject().put("id", rid).put("date", today));
            p.edit().putString("q", q.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Hand the queued "done" taps to the page and clear them. */
    static String takeDone(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String q = p.getString("q", "[]");
        p.edit().remove("q").apply();
        return q;
    }

    static void snooze(Context c, int idx, String title, String time, boolean insistent, String rid) {
        stop(c, idx);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = extras(new Intent(c, ReminderReceiver.class).setAction("com.vaazhkai.tracker.SNOOZE." + idx), idx, title, time, insistent, rid)
                .putExtra("snooze", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, 6000 + idx, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + SNOOZE_MIN * 60_000L;
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static boolean fullScreenAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm == null || nm.canUseFullScreenIntent();
    }
}
