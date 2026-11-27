package com.mafia2re.game;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Shared look of the launcher and the in-game panels: warm "Empire Bay" noir with amber. */
final class Ui {
    static final int BG = 0xFF0D0B09;
    static final int CARD = 0xEB15110E;
    static final int CARD_HI = 0xFF221B15;
    static final int LINE = 0xFF3A2F24;
    static final int AMBER = 0xFFE0A63E;
    static final int AMBER_DEEP = 0xFFB97F25;
    static final int IVORY = 0xFFF0E6D2;
    static final int MUTED = 0xFFA39580;
    static final int DIM = 0xFF6E6354;
    static final int RED = 0xFFB3362B;
    static final int GREEN = 0xFF79C27B;
    static final int INK = 0xFF16110B;

    private static Typeface oswald, oswaldBold, playfair;

    private Ui() {
    }

    static boolean english() {
        String l = Locale.getDefault().getLanguage();
        return !(l.equals("ru") || l.equals("uk") || l.equals("be") || l.equals("kk"));
    }

    /** Russian first, English when the phone is not set to a Russian-reading locale. */
    static String t(String ru, String en) {
        return english() ? en : ru;
    }

    private static Typeface variable(Context c, String asset, int weight, Typeface fallback) {
        try {
            return new Typeface.Builder(c.getAssets(), asset)
                    .setFontVariationSettings("'wght' " + weight).build();
        } catch (Throwable e) {
            return fallback;
        }
    }

    static Typeface head(Context c) {
        if (oswaldBold == null) oswaldBold = variable(c, "fonts/oswald.ttf", 600, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        return oswaldBold;
    }

    static Typeface body(Context c) {
        if (oswald == null) oswald = variable(c, "fonts/oswald.ttf", 400, Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        return oswald;
    }

    static Typeface serif(Context c) {
        if (playfair == null) playfair = variable(c, "fonts/playfair.ttf", 800, Typeface.create(Typeface.SERIF, Typeface.BOLD));
        return playfair;
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(int fill, int stroke, float strokeDp, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        return d;
    }

    static Drawable ripple(Drawable content, int color) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(tf);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** Small spaced-out amber caption ("ПРОДОЛЖИТЬ ИСТОРИЮ"). */
    static TextView caption(Context c, String s) {
        TextView v = text(c, s.toUpperCase(Locale.ROOT), 12, AMBER, head(c));
        v.setLetterSpacing(0.22f);
        return v;
    }

    /** The big amber call to action. */
    static TextView primaryButton(Context c, String s, View.OnClickListener l) {
        TextView b = text(c, s, 21, INK, head(c));
        b.setLetterSpacing(0.12f);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFF0B954, AMBER_DEEP});
        g.setCornerRadius(dp(c, 6));
        b.setBackground(ripple(g, 0x55FFFFFF));
        b.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6));
        b.setOnClickListener(l);
        b.setClickable(true);
        b.setFocusable(true);
        b.setElevation(dp(c, 6));
        return b;
    }

    /** Outlined secondary button. */
    static TextView ghostButton(Context c, String s, View.OnClickListener l) {
        TextView b = text(c, s.toUpperCase(Locale.ROOT), 14, IVORY, head(c));
        b.setLetterSpacing(0.08f);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(round(0x22FFFFFF, LINE, 1, 6, c), 0x33E0A63E));
        b.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6));
        b.setOnClickListener(l);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    /** Pill chip used in the top-right corner (chip name, FPS). */
    static LinearLayout chip(Context c, String s, int dotColor) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(round(0xCC1A1612, 0x55FFFFFF, 1, 22, c));
        l.setPadding(dp(c, 14), dp(c, 8), dp(c, 16), dp(c, 8));
        if (dotColor != 0) {
            View dot = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(dotColor);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 8), dp(c, 8));
            lp.setMarginEnd(dp(c, 8));
            l.addView(dot, lp);
        }
        l.addView(text(c, s, 15, IVORY, body(c)));
        return l;
    }

    static int alpha(int color, float a) {
        return (color & 0xFFFFFF) | (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24);
    }

    static int mix(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
