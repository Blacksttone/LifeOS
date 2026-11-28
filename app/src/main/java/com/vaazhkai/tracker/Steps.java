package com.vaazhkai.tracker;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Today's steps from the phone's step counter sensor. The sensor counts steps since boot,
 * so we keep the reading at the start of each day (a small alarm just after midnight
 * takes it) and subtract.
 */
final class Steps {
    private Steps() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("steps", Context.MODE_PRIVATE); }
    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    static boolean supported(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 29 || c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Record a raw sensor value (steps since boot). */
    static synchronized void onReading(Context c, float raw) {
        SharedPreferences p = prefs(c);
        String day = p.getString("day", ""), now = today();
        float base = p.getFloat("base", raw), last = p.getFloat("last", raw);
        SharedPreferences.Editor e = p.edit();
        if (raw < last) base = raw - (last - base); // phone restarted: the counter began again at 0
        if (!now.equals(day)) {
            if (!day.isEmpty()) { e.putString("prevDay", day).putInt("prevSteps", Math.max(0, Math.round(last - base))); }
            // Just after midnight, the current reading is the day's start. Later in the day, the last
            // reading we have (from yesterday) is the best start we know.
            boolean early = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == 0;
            base = day.isEmpty() || early ? raw : last;
            e.putString("day", now);
        }
        e.putFloat("base", base).putFloat("last", raw).apply();
    }

    /** Steps counted today so far (from the last reading). */
    static int todaySteps(Context c) {
        SharedPreferences p = prefs(c);
        if (!today().equals(p.getString("day", ""))) return 0;
        return Math.max(0, Math.round(p.getFloat("last", 0) - p.getFloat("base", 0)));
    }

    static String prevJson(Context c) {
        SharedPreferences p = prefs(c);
        return "{\"day\":\"" + p.getString("prevDay", "") + "\",\"steps\":" + p.getInt("prevSteps", 0) + "}";
    }

    /** Take one reading, then call done (on the main thread). */
    static void readOnce(Context c, Runnable done) {
        final Context app = c.getApplicationContext();
        final SensorManager sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        final Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        final Handler h = new Handler(Looper.getMainLooper());
        if (s == null || !allowed(app)) { if (done != null) h.post(done); return; }
        final boolean[] fired = {false};
        final SensorEventListener[] ref = new SensorEventListener[1];
        final Runnable finish = () -> {
            if (fired[0]) return;
            fired[0] = true;
            try { sm.unregisterListener(ref[0]); } catch (Exception ignored) {}
            if (done != null) done.run();
        };
        ref[0] = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent ev) { onReading(app, ev.values[0]); h.post(finish); }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        try { sm.registerListener(ref[0], s, SensorManager.SENSOR_DELAY_NORMAL); } catch (Exception e) { h.post(finish); return; }
        h.postDelayed(finish, 4000);
    }

    /** A daily wake-up just after midnight to take the day's starting reading. */
    static void scheduleMidnight(Context c) {
        if (!supported(c)) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 1); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7701, new Intent(c, StepsReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi); } catch (Exception ignored) {}
    }
}
