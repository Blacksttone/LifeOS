package com.vaazhkai.tracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Keeps one alarm per enabled reminder, always set to its next occurrence. */
final class ReminderScheduler {
    private static final String PREFS = "reminders";
    private static final String KEY_JSON = "json";
    private static final String KEY_COUNT = "count";

    private ReminderScheduler() {}

    static void save(Context c, String json) {
        prefs(c).edit().putString(KEY_JSON, json == null ? "[]" : json).commit();
    }

    static JSONArray load(Context c) {
        try { return new JSONArray(prefs(c).getString(KEY_JSON, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent intentFor(Context c, int idx, String title, String time, int flags) {
        Intent it = new Intent(c, ReminderReceiver.class);
        it.setAction("com.vaazhkai.tracker.REMIND." + idx);
        if (title != null) {
            it.putExtra("idx", idx);
            it.putExtra("title", title);
            it.putExtra("time", time);
        }
        return PendingIntent.getBroadcast(c, idx, it, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static synchronized void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        SharedPreferences p = prefs(c);
        int prev = p.getInt(KEY_COUNT, 0);
        for (int i = 0; i < prev; i++) {
            PendingIntent old = intentFor(c, i, null, null, PendingIntent.FLAG_NO_CREATE);
            if (old != null) { am.cancel(old); old.cancel(); }
        }

        JSONArray arr = load(c);
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !o.optBoolean("enabled", true)) continue;
            long t = next(o, now);
            if (t < 0) continue;
            PendingIntent pi = intentFor(c, i, o.optString("title", "Reminder"), o.optString("time", ""),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                }
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
        }
        p.edit().putInt(KEY_COUNT, arr.length()).apply();
    }

    /** Next trigger time in ms, or -1 when the reminder will not fire again. */
    static long next(JSONObject o, long now) {
        int h = 8, m = 0;
        try {
            String[] hm = o.optString("time", "08:00").split(":");
            h = Integer.parseInt(hm[0]);
            m = Integer.parseInt(hm[1]);
        } catch (Exception ignored) {}
        String repeat = o.optString("repeat", "daily");

        if ("once".equals(repeat)) {
            try {
                String[] d = o.optString("date", "").split("-");
                Calendar cal = Calendar.getInstance();
                cal.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]), h, m, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long t = cal.getTimeInMillis();
                return t > now ? t : -1;
            } catch (Exception e) { return -1; }
        }

        if ("monthly".equals(repeat)) {
            int dom = Math.max(1, Math.min(31, o.optInt("dom", 1)));
            for (int i = 0; i < 14; i++) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(now);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MONTH, i);
                cal.set(Calendar.DAY_OF_MONTH, Math.min(dom, cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, m);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (cal.getTimeInMillis() > now) return cal.getTimeInMillis();
            }
            return -1;
        }

        for (int i = 0; i < 8; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.add(Calendar.DAY_OF_MONTH, i);
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, m);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long t = cal.getTimeInMillis();
            if (t <= now) continue;
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            boolean weekday = dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY;
            if ("weekdays".equals(repeat) && !weekday) continue;
            if ("weekends".equals(repeat) && weekday) continue;
            return t;
        }
        return -1;
    }
}
