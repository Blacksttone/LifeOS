package com.vaazhkai.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Done and Snooze buttons on the reminder notification. */
public class AlarmActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int idx = intent.getIntExtra("idx", 0);
        String rid = intent.getStringExtra("rid");
        if ("com.vaazhkai.tracker.ALARM_SNOOZE".equals(intent.getAction())) {
            Alarms.snooze(context, idx, intent.getStringExtra("title"), intent.getStringExtra("time"),
                    intent.getBooleanExtra("insistent", false), rid);
        } else {
            Alarms.done(context, idx, rid);
        }
    }
}
