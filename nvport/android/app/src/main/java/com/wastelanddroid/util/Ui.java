package com.wastelanddroid.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.wastelanddroid.R;

public final class Ui {
    public static final String ACCENT_KEY = "accent";
    public static final int BG = Color.rgb(11, 14, 12);
    public static final int BG2 = Color.rgb(20, 26, 22);
    public static final int CARD = Color.argb(225, 20, 26, 22);
    public static final int LINE = Color.rgb(44, 56, 48);
    public static final int RED = Color.rgb(236, 112, 100);
    public static final int TG = Color.rgb(42, 171, 238);
    public static final int TEXT = Color.rgb(222, 228, 220);
    public static final int TEXT_DIM = Color.rgb(190, 210, 190);
    public static int FG = Color.rgb(44, 230, 126);
    public static int FG_DIM = Color.rgb(112, 142, 110);
    public static int AMBER = Color.rgb(156, 235, 185);
    public static int ON_ACCENT = Color.rgb(12, 18, 12);
    public static final String[] ACCENTS = {"green", "orange", "white"};
    public static final String[] ACCENT_NAMES = {"Зелёный", "Оранжевый", "Белый"};
    public static final int[] ACCENT_COLORS = {Color.rgb(44, 230, 126), Color.rgb(244, 166, 64), Color.rgb(228, 230, 230)};
    private static Typeface font = Typeface.MONOSPACE;

    public interface IntCallback {
        void on(int i);
    }

    public static void applyAccent(Context context) {
        String string = context.getSharedPreferences("wasteland", 0).getString(ACCENT_KEY, "green");
        string.hashCode();
        if (string.equals("orange")) {
            FG = ACCENT_COLORS[1];
            FG_DIM = Color.rgb(160, 132, 94);
            AMBER = Color.rgb(238, 218, 178);
            ON_ACCENT = Color.rgb(24, 16, 6);
            return;
        }
        if (string.equals("white")) {
            FG = ACCENT_COLORS[2];
            FG_DIM = Color.rgb(140, 146, 140);
            AMBER = Color.rgb(226, 200, 146);
            ON_ACCENT = Color.rgb(14, 16, 14);
            return;
        }
        FG = ACCENT_COLORS[0];
        FG_DIM = Color.rgb(112, 142, 110);
        AMBER = Color.rgb(156, 235, 185);
        ON_ACCENT = Color.rgb(12, 18, 12);
    }

    public static String accent(Context context) {
        return context.getSharedPreferences("wasteland", 0).getString(ACCENT_KEY, "green");
    }

    public static int tint(int i) {
        return Color.argb(i, Color.red(FG), Color.green(FG), Color.blue(FG));
    }

    private Ui() {
    }

    public static int dp(Context context, float f) {
        return Math.round(TypedValue.applyDimension(1, f, context.getResources().getDisplayMetrics()));
    }

    public static void init(Context context) {
        try {
            font = context.getResources().getFont(R.font.fallout);
        } catch (RuntimeException unused) {
            font = Typeface.MONOSPACE;
        }
        applyAccent(context);
    }

    public static Typeface mono(boolean z) {
        return z ? Typeface.create(font, 1) : font;
    }

    public static Drawable icon(Context context, int i, int i2, float f) {
        Drawable drawableMutate = context.getDrawable(i).mutate();
        drawableMutate.setTint(i2);
        int iDp = dp(context, f);
        drawableMutate.setBounds(0, 0, iDp, iDp);
        return drawableMutate;
    }

    public static <T extends TextView> T withIcon(T t, int i, int i2) {
        t.setCompoundDrawablesRelative(icon(t.getContext(), i, i2, Math.max(16.0f, (t.getTextSize() / t.getResources().getDisplayMetrics().density) + 4.0f)), null, null, null);
        t.setCompoundDrawablePadding(dp(t.getContext(), 8.0f));
        return t;
    }

    public static void immersive(Activity activity) {
        if (font == Typeface.MONOSPACE) {
            init(activity.getApplicationContext());
        }
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = 1;
            window.setAttributes(attributes);
            window.setStatusBarColor(0);
            window.setNavigationBarColor(0);
            decorView.setSystemUiVisibility(5894);
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController windowInsetsController = decorView.getWindowInsetsController();
            if (windowInsetsController != null) {
                windowInsetsController.hide(WindowInsets.Type.systemBars());
                windowInsetsController.setSystemBarsBehavior(2);
            }
        } catch (RuntimeException e) {
            Log.w("Ui", "immersive mode failed", e);
        }
    }

    public static GradientDrawable rounded(int i, int i2, float f, Context context) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(dp(context, f));
        if (i2 != 0) {
            gradientDrawable.setStroke(dp(context, 1.0f), i2);
        }
        return gradientDrawable;
    }

    public static Drawable screenBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{BG2, BG, Color.rgb(8, 10, 9)});
    }

    public static LinearLayout row(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        return linearLayout;
    }

    public static LinearLayout column(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(1);
        return linearLayout;
    }

    public static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1.0f);
    }

    public static LinearLayout.LayoutParams matchWrap(Context context, int i) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(context, i);
        return layoutParams;
    }

    public static LinearLayout card(Context context, String str) {
        LinearLayout linearLayoutColumn = column(context);
        linearLayoutColumn.setBackground(rounded(CARD, LINE, 14.0f, context));
        int iDp = dp(context, 14.0f);
        linearLayoutColumn.setPadding(iDp, dp(context, 10.0f), iDp, iDp);
        if (str != null) {
            TextView textView = new TextView(context);
            textView.setText(str.toUpperCase());
            textView.setTextColor(AMBER);
            textView.setTextSize(12.0f);
            textView.setLetterSpacing(0.18f);
            textView.setTypeface(mono(true));
            textView.setPadding(0, 0, 0, dp(context, 6.0f));
            linearLayoutColumn.addView(textView);
        }
        return linearLayoutColumn;
    }

    public static TextView label(Context context, String str) {
        TextView textView = new TextView(context);
        textView.setText(str);
        textView.setTextColor(TEXT);
        textView.setTextSize(14.0f);
        textView.setTypeface(mono(false));
        textView.setLineSpacing(0.0f, 1.15f);
        textView.setPadding(dp(context, 2.0f), dp(context, 3.0f), dp(context, 2.0f), dp(context, 3.0f));
        return textView;
    }

    public static TextView hint(Context context, String str) {
        TextView textViewLabel = label(context, str);
        textViewLabel.setTextColor(TEXT_DIM);
        textViewLabel.setTextSize(12.0f);
        return textViewLabel;
    }

    private static Drawable buttonBg(Context context, int i, int i2) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{-16842910}, rounded(Color.argb(180, Color.red(i), Color.green(i), Color.blue(i)), 0, 12.0f, context));
        stateListDrawable.addState(new int[0], rounded(i, i2, 12.0f, context));
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(90, 255, 255, 255)), stateListDrawable, null);
    }

    public static Button button(Context context, String str, View.OnClickListener onClickListener) {
        return styledButton(context, str, onClickListener, FG, ON_ACCENT, 0);
    }

    public static Button button(Context context, int i, String str, View.OnClickListener onClickListener) {
        return (Button) withIcon(button(context, str, onClickListener), i, ON_ACCENT);
    }

    public static Button ghostButton(Context context, int i, String str, View.OnClickListener onClickListener) {
        return (Button) withIcon(ghostButton(context, str, onClickListener), i, FG);
    }

    public static Button ghostButton(Context context, String str, View.OnClickListener onClickListener) {
        return styledButton(context, str, onClickListener, tint(60), FG, FG_DIM);
    }

    public static Button styledButton(Context context, String str, View.OnClickListener onClickListener, int i, int i2, int i3) {
        Button button = new Button(context);
        button.setText(str);
        button.setAllCaps(false);
        button.setTextColor(i2);
        button.setTypeface(mono(true));
        button.setTextSize(14.0f);
        button.setStateListAnimator(null);
        button.setBackground(buttonBg(context, i, i3));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.setMargins(dp(context, 4.0f), dp(context, 4.0f), dp(context, 4.0f), dp(context, 4.0f));
        button.setLayoutParams(layoutParams);
        button.setMinHeight(dp(context, 44.0f));
        button.setPadding(dp(context, 16.0f), dp(context, 8.0f), dp(context, 16.0f), dp(context, 8.0f));
        if (onClickListener != null) {
            button.setOnClickListener(onClickListener);
        }
        Anim.pressable(button);
        return button;
    }

    public static CheckBox check(Context context, String str, boolean z) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(str);
        checkBox.setTextColor(TEXT);
        checkBox.setTypeface(mono(false));
        checkBox.setTextSize(13.0f);
        checkBox.setChecked(z);
        checkBox.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[0]}, new int[]{FG, FG_DIM}));
        return checkBox;
    }

    public static View slider(Context context, final String str, final int i, int i2, int i3, final IntCallback intCallback) {
        LinearLayout linearLayoutColumn = column(context);
        final TextView textViewLabel = label(context, str + ": " + i3);
        textViewLabel.setTextSize(12.0f);
        SeekBar seekBar = new SeekBar(context);
        seekBar.setTag("seek");
        int i4 = i2 - i;
        seekBar.setMax(i4);
        seekBar.setProgress(Math.max(0, Math.min(i4, i3 - i)));
        seekBar.setProgressTintList(ColorStateList.valueOf(FG));
        seekBar.setThumbTintList(ColorStateList.valueOf(AMBER));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.wastelanddroid.util.Ui.1
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar2) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar2) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar2, int i5, boolean z) {
                textViewLabel.setText(str + ": " + (i + i5));
                intCallback.on(i5 + i);
            }
        });
        linearLayoutColumn.addView(textViewLabel);
        linearLayoutColumn.addView(seekBar);
        return linearLayoutColumn;
    }
}
