package com.vaazhkai.tracker;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen alarm page (shown over the lock screen) with Dismiss and Snooze. */
public class AlarmActivity extends Activity {
    private int idx;
    private String title, time;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        read(getIntent());
        build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        read(intent);
        build();
    }

    private void read(Intent i) {
        idx = i.getIntExtra("idx", 0);
        title = i.getStringExtra("title");
        time = i.getStringExtra("time");
        if (title == null) title = "Reminder";
        if (time == null) time = "";
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(16));
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(48), dp(28), dp(48));
        root.setBackgroundColor(Color.parseColor("#1747A6"));

        TextView label = new TextView(this);
        label.setText("⏰  LifeOS alarm");
        label.setTextColor(Color.parseColor("#DBE6FB"));
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);

        TextView clock = new TextView(this);
        clock.setText(time);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(64);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setGravity(Gravity.CENTER);

        TextView what = new TextView(this);
        what.setText(title);
        what.setTextColor(Color.WHITE);
        what.setTextSize(26);
        what.setGravity(Gravity.CENTER);
        what.setPadding(0, dp(8), 0, dp(40));

        Button dismiss = button("Dismiss", Color.WHITE, Color.parseColor("#1747A6"));
        dismiss.setOnClickListener(v -> { Alarms.stop(this, idx); finish(); });
        Button snooze = button("Snooze " + Alarms.SNOOZE_MIN + " min", Color.parseColor("#2F6FE4"), Color.WHITE);
        snooze.setOnClickListener(v -> { Alarms.snooze(this, idx, title, time); finish(); });

        root.addView(label);
        root.addView(clock);
        root.addView(what);
        root.addView(dismiss);
        root.addView(snooze);
        setContentView(root);
    }
}
