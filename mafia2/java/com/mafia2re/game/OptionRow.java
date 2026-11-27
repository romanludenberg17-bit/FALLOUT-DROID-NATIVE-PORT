package com.mafia2re.game;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** "Label ......... [pill][pill][pill]" row that updates itself in place. */
final class OptionRow {
    interface OnChange {
        void changed(int value);
    }

    private OptionRow() {
    }

    static View create(Context c, GameConfig cfg, GameConfig.Option o, float labelSp, OnChange cb) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 9), 0, Ui.dp(c, 3));
        LinearLayout lab = new LinearLayout(c);
        lab.setOrientation(LinearLayout.VERTICAL);
        lab.addView(Ui.text(c, o.label(), labelSp, Ui.IVORY, Ui.body(c)));
        if (!o.hint().isEmpty()) {
            TextView h = Ui.text(c, o.hint(), 12, Ui.DIM, Ui.body(c));
            h.setPadding(0, Ui.dp(c, 3), Ui.dp(c, 10), 0);
            lab.addView(h);
        }
        row.addView(lab, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout pills = new LinearLayout(c);
        pills.setBackground(Ui.round(0x14FFFFFF, 0, 0, 6, c));
        pills.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
        TextView[] views = new TextView[o.values.length];
        Runnable paint = () -> {
            int cur = cfg.get(o);
            for (int i = 0; i < views.length; i++) {
                boolean on = o.values[i] == cur;
                views[i].setTextColor(on ? Ui.INK : 0xFFCFC4B2);
                views[i].setBackground(Ui.ripple(on ? Ui.round(Ui.AMBER, 0, 0, 5, c) : Ui.round(0, 0, 0, 5, c), 0x33E0A63E));
            }
        };
        for (int i = 0; i < o.values.length; i++) {
            final int val = o.values[i];
            TextView p = Ui.text(c, o.name(i), 15, 0xFFCFC4B2, Ui.body(c));
            p.setGravity(Gravity.CENTER);
            p.setMinWidth(Ui.dp(c, 50));
            p.setPadding(Ui.dp(c, 9), Ui.dp(c, 7), Ui.dp(c, 9), Ui.dp(c, 7));
            p.setClickable(true);
            p.setOnClickListener(v -> {
                cfg.set(o, val);
                paint.run();
                if (cb != null) cb.changed(val);
            });
            views[i] = p;
            pills.addView(p);
        }
        paint.run();
        row.addView(pills);
        return row;
    }
}
