package com.wastelanddroid.input;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wastelanddroid.engine.NativeInput;

/**
 * The console's text bar: shown while the engine's console is open (uiState 12) with
 * Android's keyboard. A line is sent on "Enter"/"Send" as characters plus Enter
 * (NativeInput.typeLine): the keyboard commits text, which the engine would never see as keys.
 */
public final class ConsoleBar extends LinearLayout {
    private final EditText field;
    private boolean shown;

    public ConsoleBar(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(0xCC000000);
        int pad = dp(6);
        setPadding(pad, pad, pad, pad);
        Typeface mono;
        try {
            mono = Typeface.createFromAsset(c.getAssets(), "fonts/FalloutMonofonto.ttf");
        } catch (RuntimeException e) {
            mono = Typeface.MONOSPACE;
        }
        field = new EditText(c);
        field.setSingleLine(true);
        field.setTextColor(0xFF1AFF80);
        field.setHintTextColor(0x881AFF80);
        field.setHint("Команда консоли");
        field.setTypeface(mono);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setBackgroundColor(0x33000000);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.setOnEditorActionListener((v, action, ev) -> {
            if (action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                send();
                return true;
            }
            return false;
        });
        addView(field, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(button(c, mono, "▲", v -> tap(103)));
        addView(button(c, mono, "ВВОД", v -> send()));
        addView(button(c, mono, "✕", v -> tap(41)));
        setVisibility(GONE);
    }

    private TextView button(Context c, Typeface mono, String text, OnClickListener l) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setTypeface(mono);
        b.setTextColor(0xFF1AFF80);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setGravity(Gravity.CENTER);
        int p = dp(10);
        b.setPadding(p, p, p, p);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(l);
        return b;
    }

    /** A key pressed and let go: ▲ recalls the last line, ✕ closes the console (`). */
    private static void tap(int code) {
        try {
            NativeInput.key(code, true);
            NativeInput.key(code, false);
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private void send() {
        String line = field.getText().toString();
        field.setText("");
        NativeInput.typeLine(line);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Shown while the console is open; the keyboard comes with it. */
    public void setShown(boolean on) {
        if (on == shown) return;
        shown = on;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (on) {
            setVisibility(VISIBLE);
            bringToFront();
            field.requestFocus();
            field.postDelayed(() -> {
                if (shown && imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        } else {
            if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
            field.clearFocus();
            setVisibility(GONE);
        }
    }
}
