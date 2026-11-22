package com.wastelanddroid.engine;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.wastelanddroid.input.ScreenKeyStore;

/**
 * The Pip-Boy's three pages as touch buttons at the left edge while it is open: STATS,
 * ITEMS, DATA (the game's F1, F2, F3). The device's own buttons on the arm are small and,
 * at the bottom of the screen, hard to hit with a finger.
 */
public final class PipBoyPages extends LinearLayout {
    private static final String[] LABELS = {"СТАТ", "ВЕЩИ", "ДАННЫЕ"};
    private static final int[] KEYS = {59, 60, 61}; // evdev F1, F2, F3

    public PipBoyPages(Context c, boolean fo3) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setVisibility(GONE);
        int accent = ScreenKeyStore.tint(c, fo3 ? 0xFF1AFF80 : 0xFFFFB840);
        float d = getResources().getDisplayMetrics().density;
        Typeface font;
        try {
            font = Typeface.createFromAsset(c.getAssets(), "fonts/FalloutMonofonto.ttf");
        } catch (RuntimeException e) {
            font = Typeface.MONOSPACE;
        }
        for (int i = 0; i < LABELS.length; i++) {
            final int key = KEYS[i];
            TextView b = new TextView(c);
            b.setText(LABELS[i]);
            b.setTextSize(13);
            b.setTypeface(font, Typeface.BOLD);
            b.setTextColor(accent);
            b.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x99000000);
            g.setStroke(Math.round(1.5f * d), accent);
            g.setCornerRadius(6 * d);
            b.setBackground(g);
            b.setOnTouchListener((v, e) -> {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.6f);
                    NativeInput.key(key, true);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                    NativeInput.key(key, false);
                }
                return true;
            });
            LayoutParams p = new LayoutParams(Math.round(84 * d), Math.round(52 * d));
            p.setMargins(Math.round(10 * d), Math.round(6 * d), 0, Math.round(6 * d));
            addView(b, p);
        }
    }

    public void setOn(boolean on) {
        int v = on ? VISIBLE : GONE;
        if (getVisibility() != v) setVisibility(v);
    }
}
