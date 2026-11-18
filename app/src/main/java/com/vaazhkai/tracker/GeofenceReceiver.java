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

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Shows the location reminder when the phone arrives at or leaves a saved place. */
public class GeofenceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent ev = GeofencingEvent.fromIntent(intent);
        if (ev == null || ev.hasError()) return;
        List<Geofence> fired = ev.getTriggeringGeofences();
        if (fired == null) return;
        boolean leaving = ev.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean allowed = Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (nm == null || !allowed) return;
        NotificationChannel ch = new NotificationChannel(ReminderReceiver.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Reminders you set in LifeOS");
        nm.createNotificationChannel(ch);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        JSONArray all = Geo.load(context);
        for (Geofence g : fired) {
            JSONObject rule = null;
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                if (o != null && g.getRequestId().equals(o.optString("id"))) { rule = o; break; }
            }
            if (rule == null || !rule.optBoolean("enabled", true)) continue;
            // Only on the chosen days (0 = Sunday … 6 = Saturday, like JavaScript's getDay()).
            JSONArray days = rule.optJSONArray("days");
            if (days != null && days.length() > 0) {
                int today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1;
                boolean on = false;
                for (int d = 0; d < days.length(); d++) if (days.optInt(d, -1) == today) { on = true; break; }
                if (!on) continue;
            }
            String place = rule.optString("place", "your place");
            Notification n = new Notification.Builder(context, ReminderReceiver.CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(rule.optString("title", "Reminder"))
                    .setContentText((leaving ? "Leaving " : "Arrived at ") + place)
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .build();
            nm.notify(5000 + (g.getRequestId().hashCode() & 0xFFFF), n);
        }
    }
}
