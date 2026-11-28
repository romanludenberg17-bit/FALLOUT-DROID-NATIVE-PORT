package com.mafia2re.game;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Toolbars of the layout editor, laid over a {@link ControlsOverlay} in edit mode.
 * Top: context tabs, grid, undo, reset, done. Bottom: the selected button's size, opacity, hide.
 */
final class EditorChrome extends FrameLayout implements ControlsOverlay.Listener {
    private final ControlsOverlay overlay;
    private final Runnable onDone;
    private final TextView[] ctxTabs = new TextView[3];
    private final int[] ctxIds = {ControlLayout.FOOT, ControlLayout.CAR, ControlLayout.MENU};
    private TextView gridBtn, undoBtn;
    private final LinearLayout selBar;
    private final TextView hint;
    private TextView selName, sizeVal, alphaVal, hideBtn;
    private int ctx;

    EditorChrome(Context c, ControlsOverlay overlay, int startCtx, Runnable onDone) {
        super(c);
        this.overlay = overlay;
        this.onDone = onDone;
        this.ctx = startCtx;
        overlay.setListener(this);

        // ---- top bar
        LinearLayout top = bar(c);
        String[][] names = {{"ПЕШКОМ", "ON FOOT"}, {"ЗА РУЛЁМ", "DRIVING"}, {"МЕНЮ", "MENUS"}};
        for (int i = 0; i < 3; i++) {
            final int id = ctxIds[i];
            TextView t = pill(c, Ui.t(names[i][0], names[i][1]), v -> {
                ctx = id;
                overlay.setEditContext(id);
                paintTabs();
            });
            ctxTabs[i] = t;
            top.addView(t);
        }
        top.addView(sep(c));
        gridBtn = pill(c, "", v -> {
            overlay.setSnap(!overlay.snap());
            paintTools();
        });
        top.addView(gridBtn);
        undoBtn = pill(c, "↶ " + Ui.t("Отменить", "Undo"), v -> {
            overlay.undo();
            paintTools();
        });
        top.addView(undoBtn);
        top.addView(pill(c, Ui.t("Сброс", "Reset"), v -> {
            overlay.resetLayout();
            paintTools();
        }));
        top.addView(sep(c));
        TextView done = pill(c, "✓ " + Ui.t("ГОТОВО", "DONE"), v -> {
            overlay.save();
            onDone.run();
        });
        done.setTextColor(Ui.INK);
        done.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, 0, 0, 16, c), 0x55FFFFFF));
        top.addView(done);
        LayoutParams tlp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tlp.topMargin = Ui.dp(c, 10);
        addView(top, tlp);

        // ---- bottom: selection tools or a hint
        selBar = bar(c);
        selName = Ui.text(c, "", 15, Ui.ACCENT, Ui.head(c));
        selName.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 12), 0);
        selBar.addView(selName);
        selBar.addView(label(c, Ui.t("Размер", "Size")));
        selBar.addView(pill(c, "−", v -> overlay.changeSelected(-0.1f, 0, false)));
        sizeVal = value(c);
        selBar.addView(sizeVal);
        selBar.addView(pill(c, "+", v -> overlay.changeSelected(0.1f, 0, false)));
        selBar.addView(sep(c));
        selBar.addView(label(c, Ui.t("Видимость", "Opacity")));
        selBar.addView(pill(c, "−", v -> overlay.changeSelected(0, -0.1f, false)));
        alphaVal = value(c);
        selBar.addView(alphaVal);
        selBar.addView(pill(c, "+", v -> overlay.changeSelected(0, 0.1f, false)));
        selBar.addView(sep(c));
        hideBtn = pill(c, "", v -> overlay.changeSelected(0, 0, true));
        selBar.addView(hideBtn);
        LayoutParams blp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = Ui.dp(c, 12);
        addView(selBar, blp);

        hint = Ui.text(c, Ui.t("Перетаскивайте кнопки пальцем · два пальца — размер · коснитесь кнопки, чтобы настроить её",
                "Drag buttons with a finger · pinch to resize · tap a button to tune it"), 13, Ui.IVORY, Ui.body(c));
        hint.setBackground(Ui.round(0xCC15110E, 0x33FFFFFF, 1, 16, c));
        hint.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        LayoutParams hlp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hlp.topMargin = Ui.dp(c, 66);
        addView(hint, hlp);

        overlay.setEditing(true, startCtx);
        paintTabs();
        paintTools();
        onSelection(null);
    }

    private static LinearLayout bar(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(Ui.round(0xE615110E, 0x40FFFFFF, 1, 22, c));
        l.setPadding(Ui.dp(c, 6), Ui.dp(c, 5), Ui.dp(c, 6), Ui.dp(c, 5));
        l.setElevation(Ui.dp(c, 8));
        return l;
    }

    private static TextView pill(Context c, String s, OnClickListener l) {
        TextView t = Ui.text(c, s, 14, Ui.IVORY, Ui.head(c));
        t.setLetterSpacing(0.05f);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(Ui.dp(c, 36));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        t.setBackground(Ui.ripple(Ui.round(0, 0, 0, 16, c), 0x40D9322F));
        t.setOnClickListener(l);
        return t;
    }

    private static TextView label(Context c, String s) {
        TextView t = Ui.text(c, s, 13, Ui.MUTED, Ui.body(c));
        t.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 2), 0);
        return t;
    }

    private static TextView value(Context c) {
        TextView t = Ui.text(c, "", 14, Ui.IVORY, Ui.head(c));
        t.setMinWidth(Ui.dp(c, 44));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static View sep(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(1, Ui.dp(c, 22));
        lp.setMargins(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private void paintTabs() {
        Context c = getContext();
        for (int i = 0; i < 3; i++) {
            boolean on = ctxIds[i] == ctx;
            ctxTabs[i].setTextColor(on ? 0xFF111111 : Ui.IVORY);
            ctxTabs[i].setBackground(Ui.ripple(on ? Ui.round(0xFFF0E6D2, 0, 0, 16, c) : Ui.round(0, 0, 0, 16, c), 0x40D9322F));
        }
    }

    private void paintTools() {
        gridBtn.setText((overlay.snap() ? "▦ " : "□ ") + Ui.t("Сетка", "Grid"));
        gridBtn.setTextColor(overlay.snap() ? Ui.ACCENT : Ui.IVORY);
        undoBtn.setAlpha(overlay.canUndo() ? 1f : 0.4f);
    }

    @Override
    public void onGear() {
    }

    @Override
    public void onSelection(ControlLayout.Element e) {
        boolean has = e != null;
        selBar.setVisibility(has ? VISIBLE : GONE);
        hint.setVisibility(has ? GONE : VISIBLE);
        if (has) {
            selName.setText(e.label().toUpperCase(Locale.ROOT));
            sizeVal.setText(Math.round(e.scale * 100) + "%");
            alphaVal.setText(Math.round(e.alpha * 100) + "%");
            hideBtn.setText(e.hidden ? "◉ " + Ui.t("Показать", "Show") : "◌ " + Ui.t("Скрыть", "Hide"));
            hideBtn.setTextColor(e.hidden ? Ui.ACCENT : Ui.IVORY);
        }
        paintTools();
    }
}
