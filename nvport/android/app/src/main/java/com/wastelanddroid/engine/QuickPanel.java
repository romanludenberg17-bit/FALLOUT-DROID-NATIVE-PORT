package com.wastelanddroid.engine;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.wastelanddroid.input.ScreenKeyStore;
import com.wastelanddroid.util.Prefs;

/**
 * The in-game quick panel (the system Back gesture opens and closes it), as Fallout Droid
 * 1.x's DroidDeck panel: the keys' accent colour and opacity, the frame-rate limit, the
 * look sensitivity, smart hiding and the keys on or off. Every change applies at once
 * (the overlay reloads, the engine's frame cap is set live) and is kept for later starts.
 */
public final class QuickPanel extends FrameLayout {
    public interface Host {
        /** The keys' look or settings changed: reload the overlay. */
        void keysChanged();

        /** The frame-rate limit changed (0: none): the engine and the display follow. */
        void frameCapChanged(int fps);

        /** Leave the game for the launcher. */
        void exitGame();
    }

    /** Accent colours of the keys: amber (New Vegas), green (Fallout 3), blue, white, red. */
    private static final int[] ACCENTS = {0xFFFFB840, 0xFF1AFF80, 0xFF2ECFFF, 0xFFE6F0FF, 0xFFFF5A3C};
    private static final String[] FPS_CHOICES = {"auto", "30", "60", "90", "120", "off"};
    private static final String[] FPS_LABELS = {"АВТО", "30", "60", "90", "120", "БЕЗ"};

    private final Host host;
    private final LinearLayout column;
    private int accent;

    public QuickPanel(Context c, Host host) {
        super(c);
        this.host = host;
        setVisibility(GONE);
        setBackgroundColor(0x99000000);
        setClickable(true);
        // A touch outside the card closes the panel.
        setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) hide();
            return true;
        });
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setOnTouchListener(null);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        addView(scroll, lp);
        scroll.setClickable(true);
    }

    public boolean shown() {
        return getVisibility() == VISIBLE;
    }

    public void toggle() {
        if (shown()) hide(); else show();
    }

    public void show() {
        build();
        setVisibility(VISIBLE);
        bringToFront();
    }

    public void hide() {
        setVisibility(GONE);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Typeface font() {
        try {
            return Typeface.createFromAsset(getContext().getAssets(), "fonts/FalloutMonofonto.ttf");
        } catch (RuntimeException e) {
            return Typeface.MONOSPACE;
        }
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(font());
        return t;
    }

    private GradientDrawable box(int stroke, int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1.5f), stroke);
        g.setCornerRadius(dp(4));
        return g;
    }

    private void heading(String s) {
        TextView t = text(s, 13, accent);
        t.setPadding(0, dp(14), 0, dp(6));
        column.addView(t);
    }

    private void build() {
        Context c = getContext();
        accent = ScreenKeyStore.tint(c, ACCENTS[0]);
        column.removeAllViews();
        ((View) column.getParent()).setBackground(box(accent, 0xF20B0F0B));

        TextView title = text("ПАНЕЛЬ FALLOUT DROID", 18, accent);
        title.setTypeface(font(), Typeface.BOLD);
        column.addView(title);
        column.addView(text("Свайп «назад» — открыть / закрыть", 11, 0xFFB0B0A0));

        heading("ЦВЕТ КНОПОК");
        LinearLayout colours = new LinearLayout(c);
        for (int col : ACCENTS) {
            View sw = new View(c);
            sw.setBackground(box(col == accent ? Color.WHITE : 0x66FFFFFF, col));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(42), dp(42));
            p.setMargins(0, 0, dp(10), 0);
            sw.setOnClickListener(v -> {
                ScreenKeyStore.setTint(c, col);
                host.keysChanged();
                build();
            });
            colours.addView(sw, p);
        }
        column.addView(colours);

        heading("ПРОЗРАЧНОСТЬ КНОПОК");
        column.addView(slider(10, 100, Prefs.get(c).getInt("keys_opacity", 100), "%", v -> {
            Prefs.get(c).edit().putInt("keys_opacity", v).apply();
            host.keysChanged();
        }));

        heading("ЛИМИТ FPS");
        String cur;
        try {
            cur = Prefs.get(c).getString(Prefs.FPS_LOCK, "auto");
        } catch (ClassCastException e) {
            cur = "auto";
        }
        LinearLayout fps = new LinearLayout(c);
        for (int i = 0; i < FPS_CHOICES.length; i++) {
            final String choice = FPS_CHOICES[i];
            boolean on = choice.equals(cur);
            TextView b = text(FPS_LABELS[i], 13, on ? Color.BLACK : accent);
            b.setGravity(Gravity.CENTER);
            b.setBackground(box(accent, on ? accent : 0x00000000));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(38), 1f);
            p.setMargins(0, 0, dp(5), 0);
            b.setOnClickListener(v -> {
                Prefs.get(c).edit().putString(Prefs.FPS_LOCK, choice).apply();
                host.frameCapChanged(FalloutEngine.frameCap(c));
                build();
            });
            fps.addView(b, p);
        }
        column.addView(fps);

        heading("ЧУВСТВИТЕЛЬНОСТЬ ОБЗОРА");
        column.addView(slider(30, 300, Prefs.get(c).getInt(FalloutInput.LOOK_SENS, 115), "%", v -> {
            Prefs.get(c).edit().putInt(FalloutInput.LOOK_SENS, v).apply();
            host.keysChanged();
        }));

        heading("КНОПКИ");
        column.addView(toggle("Умное скрытие в меню", Prefs.get(c).getBoolean("smart_hide", true), on -> {
            Prefs.get(c).edit().putBoolean("smart_hide", on).apply();
            host.keysChanged();
        }));
        column.addView(toggle("Показывать экранные кнопки", ScreenKeyStore.shown(c), on -> {
            ScreenKeyStore.setShown(c, on);
            host.keysChanged();
        }));

        LinearLayout actions = new LinearLayout(c);
        actions.setPadding(0, dp(18), 0, 0);
        TextView resume = text("ПРОДОЛЖИТЬ", 14, Color.BLACK);
        resume.setGravity(Gravity.CENTER);
        resume.setBackground(box(accent, accent));
        resume.setOnClickListener(v -> hide());
        TextView exit = text("В ЛАУНЧЕР", 14, accent);
        exit.setGravity(Gravity.CENTER);
        exit.setBackground(box(accent, 0x00000000));
        exit.setOnClickListener(v -> host.exitGame());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        p1.setMargins(0, 0, dp(8), 0);
        actions.addView(resume, p1);
        actions.addView(exit, new LinearLayout.LayoutParams(0, dp(44), 1f));
        column.addView(actions);
    }

    private interface IntChange {
        void on(int v);
    }

    private interface BoolChange {
        void on(boolean v);
    }

    private View slider(int min, int max, int value, String unit, IntChange change) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.getProgressDrawable().setTint(accent);
        bar.getThumb().setTint(accent);
        TextView val = text(value + unit, 13, accent);
        val.setMinWidth(dp(56));
        val.setGravity(Gravity.END);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                val.setText((p + min) + unit);
                if (user) change.on(p + min);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(val);
        return row;
    }

    private View toggle(String label, boolean value, BoolChange change) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView name = text(label, 13, 0xFFE0E0D0);
        TextView state = text(value ? "ВКЛ" : "ВЫКЛ", 13, value ? Color.BLACK : accent);
        state.setGravity(Gravity.CENTER);
        state.setBackground(box(accent, value ? accent : 0x00000000));
        final boolean[] on = {value};
        state.setOnClickListener(v -> {
            on[0] = !on[0];
            state.setText(on[0] ? "ВКЛ" : "ВЫКЛ");
            state.setTextColor(on[0] ? Color.BLACK : accent);
            state.setBackground(box(accent, on[0] ? accent : 0x00000000));
            change.on(on[0]);
        });
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(state, new LinearLayout.LayoutParams(dp(70), dp(34)));
        return row;
    }
}
