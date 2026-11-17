package com.vaazhkai.tracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Backup to a file the user picks with the system file picker (Google Drive appears there,
 * and the picker can create a new folder). The chosen file is remembered, so later backups
 * overwrite it without asking again. Results go back to the page as
 * window.__backupDone(ok, name, message) and window.__restoreData(text, name, message).
 */
final class BackupFiles {
    static final int REQ_CREATE = 41;
    static final int REQ_OPEN = 42;
    private static final String PREFS = "backup";
    private static final String KEY_URI = "uri";

    private final MainActivity activity;
    private String pendingJson;

    BackupFiles(MainActivity activity) { this.activity = activity; }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Uri savedUri() {
        String s = prefs().getString(KEY_URI, null);
        return s == null ? null : Uri.parse(s);
    }

    String savedName() {
        return savedUri() == null ? "" : prefs().getString("name", "LifeOS backup");
    }

    void forget() { prefs().edit().remove(KEY_URI).remove("name").apply(); }

    /** Ask where to save (first time, or "Change location"). */
    void pickAndSave(String json, String suggestedName) {
        pendingJson = json;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, suggestedName);
        activity.runOnUiThread(() -> {
            try { activity.startActivityForResult(i, REQ_CREATE); }
            catch (Exception e) { done(false, "", "No file picker is available on this phone."); }
        });
    }

    /** Overwrite the remembered file. */
    void saveAgain(String json) {
        final Uri u = savedUri();
        if (u == null) { done(false, "", "Choose where to save the backup first."); return; }
        new Thread(() -> {
            boolean ok = write(u, json);
            if (ok) done(true, displayName(u), "");
            else done(false, displayName(u), "Couldn't write to the backup file. Choose the location again.");
        }).start();
    }

    void pickAndRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        activity.runOnUiThread(() -> {
            try { activity.startActivityForResult(i, REQ_OPEN); }
            catch (Exception e) { restored(null, "", "No file picker is available on this phone."); }
        });
    }

    void restoreSaved() {
        final Uri u = savedUri();
        if (u == null) { restored(null, "", "No backup file chosen yet."); return; }
        new Thread(() -> {
            String text = read(u);
            restored(text, displayName(u), text == null ? "Couldn't read the backup file. Pick it again with Restore from file." : "");
        }).start();
    }

    /** Called from MainActivity.onActivityResult. */
    boolean onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CREATE && requestCode != REQ_OPEN) return false;
        final Uri uri = (resultCode == Activity.RESULT_OK && data != null) ? data.getData() : null;
        if (requestCode == REQ_CREATE) {
            final String json = pendingJson;
            pendingJson = null;
            if (uri == null || json == null) { done(false, "", "cancelled"); return true; }
            try {
                activity.getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}
            prefs().edit().putString(KEY_URI, uri.toString()).apply();
            new Thread(() -> {
                boolean ok = write(uri, json);
                done(ok, displayName(uri), ok ? "" : "Couldn't write the backup file.");
            }).start();
        } else {
            if (uri == null) { restored(null, "", "cancelled"); return true; }
            new Thread(() -> {
                String text = read(uri);
                restored(text, displayName(uri), text == null ? "Couldn't read that file." : "");
            }).start();
        }
        return true;
    }

    private boolean write(Uri uri, String json) {
        for (String mode : new String[]{"wt", "w"}) {
            try (OutputStream out = activity.getContentResolver().openOutputStream(uri, mode)) {
                if (out == null) continue;
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private String read(Uri uri) {
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 20_000_000) return null;
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }

    private void done(boolean ok, String name, String msg) {
        if (ok && name != null && !name.isEmpty()) prefs().edit().putString("name", name).apply();
        activity.runJs("window.__backupDone && window.__backupDone(" + ok + "," + JSONObject.quote(name) + "," + JSONObject.quote(msg) + ")");
    }

    private void restored(String text, String name, String msg) {
        activity.runJs("window.__restoreData && window.__restoreData(" + (text == null ? "null" : JSONObject.quote(text)) + ","
                + JSONObject.quote(name) + "," + JSONObject.quote(msg) + ")");
    }
}
