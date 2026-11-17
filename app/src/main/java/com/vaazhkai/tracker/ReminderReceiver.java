package com.vaazhkai.tracker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String time = intent.getStringExtra("time");
        int idx = intent.getIntExtra("idx", 0);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean allowed = Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        if (nm != null && allowed && title != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminders you set in LifeOS");
            nm.createNotificationChannel(ch);

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification n = new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(title)
                    .setContentText(time == null || time.isEmpty() ? "LifeOS" : "Reminder · " + time)
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .build();
            nm.notify(1000 + idx, n);
        }

        // Set the next occurrence of every reminder.
        ReminderScheduler.scheduleAll(context);
    }
}
