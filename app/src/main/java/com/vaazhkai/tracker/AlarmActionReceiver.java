package com.vaazhkai.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dismiss and Snooze buttons on the alarm notification. */
public class AlarmActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int idx = intent.getIntExtra("idx", 0);
        if ("com.vaazhkai.tracker.ALARM_SNOOZE".equals(intent.getAction())) {
            Alarms.snooze(context, idx, intent.getStringExtra("title"), intent.getStringExtra("time"));
        } else {
            Alarms.stop(context, idx);
        }
    }
}
