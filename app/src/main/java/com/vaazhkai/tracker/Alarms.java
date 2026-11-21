package com.vaazhkai.tracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * Alarm-style reminders: an "insistent" notification on an alarm channel that keeps ringing
 * (alarm volume) and vibrating until it is dismissed or snoozed, with a full-screen page on the
 * lock screen.
 */
final class Alarms {
    static final String CHANNEL = "alarms2";
    static final int SNOOZE_MIN = 10;

    private Alarms() {}

    static int notifId(int idx) { return 1000 + idx; }

    private static void ensureChannel(Context c, NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH);
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

    private static Intent extras(Intent i, int idx, String title, String time) {
        return i.putExtra("idx", idx).putExtra("title", title).putExtra("time", time);
    }

    static void ring(Context c, int idx, String title, String time) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(c, nm);

        Intent full = extras(new Intent(c, AlarmActivity.class), idx, title, time)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(c, 3000 + idx, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismiss = PendingIntent.getBroadcast(c, 4000 + idx,
                extras(new Intent(c, AlarmActionReceiver.class).setAction("com.vaazhkai.tracker.ALARM_DISMISS"), idx, title, time),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snooze = PendingIntent.getBroadcast(c, 5000 + idx,
                extras(new Intent(c, AlarmActionReceiver.class).setAction("com.vaazhkai.tracker.ALARM_SNOOZE"), idx, title, time),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(c, R.drawable.ic_notify);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText("Alarm · " + (time == null ? "" : time))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .addAction(new Notification.Action.Builder(icon, "Dismiss", dismiss).build())
                .addAction(new Notification.Action.Builder(icon, "Snooze " + SNOOZE_MIN + " min", snooze).build())
                .build();
        n.flags |= Notification.FLAG_INSISTENT;   // keep ringing until dismissed
        nm.notify(notifId(idx), n);
    }

    static void stop(Context c, int idx) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notifId(idx));
    }

    static void snooze(Context c, int idx, String title, String time) {
        stop(c, idx);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = extras(new Intent(c, ReminderReceiver.class).setAction("com.vaazhkai.tracker.SNOOZE." + idx), idx, title, time)
                .putExtra("alarm", true).putExtra("snooze", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, 6000 + idx, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + SNOOZE_MIN * 60_000L;
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
