package com.vaazhkai.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Just after midnight: take the step counter reading that starts the new day. */
public class StepsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        final Context app = context.getApplicationContext();
        Steps.readOnce(app, () -> { Steps.scheduleMidnight(app); pr.finish(); });
    }
}
