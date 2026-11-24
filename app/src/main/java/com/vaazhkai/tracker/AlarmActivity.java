package com.vaazhkai.tracker;

import android.annotation.SuppressLint;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen reminder over the lock screen. Swipe the round handle right for Done,
 * left for Snooze.
 */
public class AlarmActivity extends Activity {
    private int idx;
    private String title, time, rid;
    private boolean insistent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
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

    @Override
    public void onBackPressed() {
        // Leave the reminder showing in notifications; the page just closes.
        super.onBackPressed();
    }

    private void read(Intent i) {
        idx = i.getIntExtra("idx", 0);
        title = i.getStringExtra("title");
        time = i.getStringExtra("time");
        rid = i.getStringExtra("rid");
        insistent = i.getBooleanExtra("insistent", false);
        if (title == null) title = "Reminder";
        if (time == null) time = "";
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void finishDone() {
        Alarms.done(this, idx, rid);
        finish();
    }

    private void finishSnooze() {
        Alarms.snooze(this, idx, title, time, insistent, rid);
        finish();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(72), dp(24), dp(56));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#2F6FE4"), Color.parseColor("#0F2E70")});
        root.setBackground(bg);

        root.addView(text(insistent ? "⏰  LifeOS alarm" : "🔔  LifeOS reminder", 16, Color.parseColor("#DBE6FB"), false));
        TextView clock = text(time, 72, Color.WHITE, true);
        clock.setPadding(0, dp(12), 0, 0);
        root.addView(clock);
        TextView what = text(title, 28, Color.WHITE, true);
        what.setPadding(0, dp(12), 0, 0);
        root.addView(what);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        // Swipe track: knob in the middle, drag right for Done, left for Snooze.
        final FrameLayout track = new FrameLayout(this);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(Color.parseColor("#33FFFFFF"));
        tbg.setCornerRadius(dp(40));
        track.setBackground(tbg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(80));
        root.addView(track, tlp);

        TextView left = text("‹‹  Snooze " + Alarms.SNOOZE_MIN + " min", 15, Color.parseColor("#DBE6FB"), true);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START);
        llp.leftMargin = dp(22);
        track.addView(left, llp);
        TextView right = text("Done  ››", 15, Color.parseColor("#DBE6FB"), true);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
        rlp.rightMargin = dp(22);
        track.addView(right, rlp);

        final TextView knob = text(insistent ? "⏰" : "🔔", 28, Color.parseColor("#1747A6"), false);
        GradientDrawable kbg = new GradientDrawable();
        kbg.setShape(GradientDrawable.OVAL);
        kbg.setColor(Color.WHITE);
        knob.setBackground(kbg);
        knob.setElevation(dp(6));
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER);
        track.addView(knob, klp);

        final float[] startX = new float[1];
        knob.setOnTouchListener((v, e) -> {
            float max = (track.getWidth() - knob.getWidth()) / 2f - dp(6);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = e.getRawX() - knob.getTranslationX();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float x = Math.max(-max, Math.min(max, e.getRawX() - startX[0]));
                    knob.setTranslationX(x);
                    right.setAlpha(x > 0 ? 1f : 0.6f);
                    left.setAlpha(x < 0 ? 1f : 0.6f);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float t = knob.getTranslationX();
                    if (t > max * 0.7f) { knob.animate().translationX(max).setDuration(120).withEndAction(this::finishDone).start(); }
                    else if (t < -max * 0.7f) { knob.animate().translationX(-max).setDuration(120).withEndAction(this::finishSnooze).start(); }
                    else knob.animate().translationX(0).setDuration(180).start();
                    return true;
            }
            return false;
        });

        TextView hint = text("Swipe right when done · swipe left to snooze", 13, Color.parseColor("#B8CCF5"), false);
        hint.setPadding(0, dp(14), 0, 0);
        root.addView(hint);

        setContentView(root);
    }
}
