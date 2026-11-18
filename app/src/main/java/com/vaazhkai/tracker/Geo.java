package com.vaazhkai.tracker;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Location reminders: the page sends a list of {id, title, place, lat, lng, radius, when, enabled};
 * each enabled entry becomes one geofence that fires on arriving ("enter") or leaving ("exit").
 * Geofences are cleared on reboot and app update, so they are registered again then.
 */
final class Geo {
    private static final String PREFS = "geo";
    private static final String KEY = "json";
    static final int MAX = 95;

    interface Done { void run(boolean ok, String msg); }

    private Geo() {}

    static void save(Context c, String json) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, json == null ? "[]" : json).commit();
    }

    static JSONArray load(Context c) {
        try {
            return new JSONArray(c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static boolean hasFine(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasBackground(Context c) {
        if (!hasFine(c)) return false;
        if (Build.VERSION.SDK_INT < 29) return true;
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static String status(Context c) {
        if (!hasFine(c)) return "none";
        return hasBackground(c) ? "background" : "foreground";
    }

    private static PendingIntent pendingIntent(Context c) {
        Intent i = new Intent(c.getApplicationContext(), GeofenceReceiver.class);
        i.setAction("com.vaazhkai.tracker.GEOFENCE");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(c.getApplicationContext(), 7, i, flags);
    }

    static void registerAll(Context ctx, Done done) {
        final Context c = ctx.getApplicationContext();
        final GeofencingClient client = LocationServices.getGeofencingClient(c);
        final PendingIntent pi = pendingIntent(c);
        client.removeGeofences(pi).addOnCompleteListener(t -> {
            JSONArray arr = load(c);
            List<Geofence> list = new ArrayList<>();
            for (int i = 0; i < arr.length() && list.size() < MAX; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || !o.optBoolean("enabled", true)) continue;
                double lat = o.optDouble("lat", Double.NaN), lng = o.optDouble("lng", Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lng)) continue;
                float radius = (float) Math.max(100, Math.min(5000, o.optDouble("radius", 200)));
                int transition = "exit".equals(o.optString("when")) ? Geofence.GEOFENCE_TRANSITION_EXIT : Geofence.GEOFENCE_TRANSITION_ENTER;
                list.add(new Geofence.Builder()
                        .setRequestId(o.optString("id"))
                        .setCircularRegion(lat, lng, radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(transition)
                        .setNotificationResponsiveness(60_000)
                        .build());
            }
            if (list.isEmpty()) { if (done != null) done.run(true, "none"); return; }
            if (!hasFine(c)) { if (done != null) done.run(false, "no_permission"); return; }
            GeofencingRequest req = new GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(list).build();
            try {
                client.addGeofences(req, pi)
                        .addOnSuccessListener(v -> { if (done != null) done.run(true, String.valueOf(list.size())); })
                        .addOnFailureListener(e -> { if (done != null) done.run(false, String.valueOf(e.getMessage())); });
            } catch (SecurityException e) {
                if (done != null) done.run(false, "no_permission");
            }
        });
    }
}
