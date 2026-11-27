package com.mafia2re.game;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.system.Os;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;

/**
 * The launcher: one card with the big "Play" button and the graphics mode, a tab bar for
 * settings, controls, game files and credits. First launch is "pick the folder, the game starts".
 */
public class LauncherActivity extends Activity {
    static final String VERSION = "1.0.0";
    private static final int REQ_TREE = 41;
    private static final int TAB_PLAY = 0, TAB_SETTINGS = 1, TAB_CONTROLS = 2, TAB_FILES = 3, TAB_ABOUT = 4;

    private GameConfig cfg;
    private DeviceProfile device;
    private FrameLayout page;
    private LinearLayout chips;
    private final TextView[] tabs = new TextView[5];
    private int tab = TAB_PLAY;
    private boolean pickAfterAccess;
    private boolean launchAfterPick = true;
    private TextView tipView;
    private int tipIndex;

    private static final String[][] TIPS = {
            {"Полиция Эмпайр-Бэй штрафует за превышение: включите ЛИМИТ скорости в машине.", "Empire Bay cops fine you for speeding: switch the LIMITER on in a car."},
            {"Сохранение идёт в начале глав: не выходите из игры во время ролика.", "The game saves at chapter starts: do not quit during a cutscene."},
            {"Тормозит на слабом телефоне? Режим «Скорость» и лимит 30 FPS дают ровную картинку.", "Choppy on an entry phone? The Speed mode with a 30 FPS cap keeps it smooth."},
            {"Раскладку можно двигать и масштабировать: вкладка «Управление» → «Редактор».", "Every button can be moved and resized: Controls → Layout editor."},
            {"С геймпадом сенсорные кнопки прячутся сами.", "With a gamepad connected the touch buttons hide themselves."},
            {"Гироскоп «при прицеле» помогает точнее стрелять.", "Gyro \"while aiming\" makes shooting more precise."},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new GameConfig(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        immersive();
        device = DeviceProfile.detect(this);
        cfg.ensureDefaults(device);
        autodetectGame();
        build();
        // The port author's own start-up module (news / notices): kept exactly as the original did.
        try {
            io.avm.r7.s.g(this);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
        if (pickAfterAccess && GameFiles.hasAllFilesAccess(this)) {
            pickAfterAccess = false;
            openPicker();
        }
        refreshChips();
        showTab(tab);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) immersive();
    }

    private void immersive() {
        Window w = getWindow();
        w.getDecorView(); // installs the decor: getInsetsController() is null-unsafe before that
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new CityBackdrop(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // Header: emblem + title on the left, chips on the right.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(36), dp(10), dp(28), dp(4));
        Emblem em = new Emblem(this);
        header.addView(em, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(16), 0, 0, 0);
        TextView t1 = Ui.text(this, "MAFIA II", 25, Ui.IVORY, Ui.serif(this));
        t1.setLetterSpacing(0.06f);
        titles.addView(t1);
        TextView t2 = Ui.text(this, Ui.t("EMPIRE BAY · ANDROID · НАТИВНЫЙ ПОРТ", "EMPIRE BAY · ANDROID · NATIVE PORT"), 11, Ui.MUTED, Ui.head(this));
        t2.setLetterSpacing(0.2f);
        t2.setPadding(0, dp(3), 0, 0);
        titles.addView(t2);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(chips);
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        page = new FrameLayout(this);
        page.setPadding(dp(36), dp(6), dp(28), dp(4));
        col.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));

        // Tip line.
        LinearLayout tip = new LinearLayout(this);
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setPadding(dp(36), dp(2), dp(28), dp(7));
        tip.addView(Ui.caption(this, Ui.t("Совет", "Tip")));
        tipView = Ui.text(this, "", 13, Ui.MUTED, Ui.body(this));
        tipView.setPadding(dp(12), 0, 0, 0);
        tipView.setSingleLine(true);
        tipView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tip.addView(tipView, new LinearLayout.LayoutParams(0, -2, 1));
        tip.setOnClickListener(v -> nextTip());
        // Short landscape phones (< 440dp tall) need every dp for the Play card.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        if (Math.min(dm.widthPixels, dm.heightPixels) / dm.density >= 440) col.addView(tip);
        tipIndex = (int) ((System.currentTimeMillis() / 60000) % TIPS.length);
        nextTip();

        col.addView(tabBar(), new LinearLayout.LayoutParams(-1, dp(52)));
        setContentView(root);
        refreshChips();
        showTab(TAB_PLAY);
    }

    private void nextTip() {
        tipIndex = (tipIndex + 1) % TIPS.length;
        tipView.setText(Ui.t(TIPS[tipIndex][0], TIPS[tipIndex][1]));
    }

    private View tabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xF20B0907);
        bar.setPadding(dp(28), 0, dp(28), 0);
        View topLine = new View(this);
        topLine.setBackgroundColor(Ui.LINE);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        String[][] names = {{"ИГРАТЬ", "PLAY"}, {"НАСТРОЙКИ", "SETTINGS"}, {"УПРАВЛЕНИЕ", "CONTROLS"},
                {"ФАЙЛЫ ИГРЫ", "GAME FILES"}, {"О ПОРТЕ", "ABOUT"}};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView tv = Ui.text(this, Ui.t(names[i][0], names[i][1]), 17, Ui.IVORY, Ui.head(this));
            tv.setLetterSpacing(0.08f);
            tv.setPadding(dp(14), dp(10), dp(14), dp(8));
            tv.setGravity(Gravity.CENTER);
            tv.setOnClickListener(v -> showTab(idx));
            tv.setBackground(Ui.ripple(null, 0x33E0A63E));
            tabs[i] = tv;
            tabRow.addView(tv);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tabRow);
        bar.addView(hs, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout ver = new LinearLayout(this);
        ver.setOrientation(LinearLayout.VERTICAL);
        ver.setGravity(Gravity.END);
        TextView v1 = Ui.text(this, "MAFIA II DROID " + VERSION, 13, Ui.DIM, Ui.head(this));
        v1.setLetterSpacing(0.1f);
        v1.setGravity(Gravity.END);
        ver.addView(v1);
        TextView v2 = Ui.text(this, Ui.t("Фанатский проект · нужна своя копия Mafia II", "Fan project · you need your own copy of Mafia II"), 11, Ui.DIM, Ui.body(this));
        v2.setPadding(0, dp(4), 0, 0);
        ver.addView(v2);
        bar.addView(ver);
        return bar;
    }

    private void showTab(int i) {
        tab = i;
        for (int k = 0; k < tabs.length; k++) {
            boolean on = k == i;
            tabs[k].setTextColor(on ? Ui.AMBER : 0xFFCFC4B2);
            GradientDrawable under = new GradientDrawable();
            under.setColor(on ? Ui.AMBER : 0);
            // Amber underline for the active tab (drawn as bottom inset layer).
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{under});
            ld.setLayerInset(0, dp(14), dp(42), dp(14), dp(6));
            tabs[k].setBackground(Ui.ripple(ld, 0x33E0A63E));
        }
        page.removeAllViews();
        View v;
        switch (i) {
            case TAB_SETTINGS: v = settingsPage(); break;
            case TAB_CONTROLS: v = controlsPage(); break;
            case TAB_FILES: v = filesPage(); break;
            case TAB_ABOUT: v = aboutPage(); break;
            default: v = playPage();
        }
        page.addView(v);
        v.setAlpha(0f);
        v.setTranslationY(dp(8));
        v.animate().alpha(1f).translationY(0).setDuration(180).start();
    }

    private void refreshChips() {
        if (chips == null) return;
        chips.removeAllViews();
        String soc = prettySoc();
        int dot = !device.cpuSupported() ? Ui.RED : device.tier == DeviceProfile.TIER_LOW ? Ui.AMBER : Ui.GREEN;
        LinearLayout c1 = Ui.chip(this, soc, dot);
        chips.addView(c1);
        int fps = cfg.get(GameConfig.FPS);
        LinearLayout c2 = Ui.chip(this, fps == 0 ? Ui.t("БЕЗ ЛИМИТА", "UNCAPPED") : fps + " FPS", 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(10));
        chips.addView(c2, lp);
        c1.setOnClickListener(v -> showTab(TAB_ABOUT));
        c2.setOnClickListener(v -> showTab(TAB_SETTINGS));
    }

    private String prettySoc() {
        String s = device.soc == null ? "" : device.soc.trim();
        if (s.matches("(?i)mt6769[a-z]*")) s = "Helio G85 / G80";
        else if (s.matches("(?i)mt6785[a-z]*")) s = "Helio G90 / G95";
        else if (s.matches("(?i)mt6781[a-z]*")) s = "Helio G96 / G99";
        else if (s.matches("(?i)mt6789[a-z]*")) s = "Helio G99";
        if (s.length() > 26) s = s.substring(0, 26) + "…";
        return s.isEmpty() ? device.gpu : s;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(Ui.CARD, 0x40FFFFFF, 1, 10, this));
        c.setPadding(dp(20), dp(14), dp(20), dp(14));
        c.setElevation(dp(10));
        return c;
    }

    // ------------------------------------------------------------------ PLAY

    private View playPage() {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout c = card();
        File game = currentGame();
        List<String> missing = game == null ? null : GameFiles.missing(game);
        boolean ready = game != null && missing.isEmpty();
        long[] saves = GameFiles.saves(this);

        c.addView(Ui.caption(this, !ready ? Ui.t("Подготовка", "Setup")
                : saves[0] > 0 ? Ui.t("Продолжить историю", "Continue the story") : Ui.t("Новая история", "A new story")));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        Emblem em = new Emblem(this);
        row.addView(em, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(dp(14), 0, 0, 0);
        String title, sub;
        if (!ready) {
            title = Ui.t("ВЫБЕРИТЕ ПАПКУ С ИГРОЙ", "PICK THE GAME FOLDER");
            sub = game == null
                    ? Ui.t("Скопируйте Mafia II с ПК вместе с папкой physx и укажите её.", "Copy Mafia II from your PC with the physx folder and pick it.")
                    : Ui.t("Не хватает: ", "Missing: ") + String.join(", ", missing);
        } else if (saves[0] > 0) {
            title = Ui.t("СОХРАНЕНИЙ: ", "SAVES: ") + saves[0];
            sub = Ui.t("последнее — ", "last — ") + DateUtils.getRelativeDateTimeString(this, saves[1], DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0);
        } else {
            title = Ui.t("ВИТО ВЕРНУЛСЯ ДОМОЙ", "VITO IS BACK HOME");
            sub = Ui.t("Добро пожаловать в Эмпайр-Бэй.", "Welcome to Empire Bay.");
        }
        TextView tt = Ui.text(this, title, 22, Ui.IVORY, Ui.head(this));
        tt.setLetterSpacing(0.03f);
        tt.setSingleLine(true);
        tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tcol.addView(tt);
        TextView st = Ui.text(this, sub, 14, ready ? 0xFFCFC4B2 : Ui.mix(Ui.RED, Ui.IVORY, 0.35f), Ui.body(this));
        st.setPadding(0, dp(4), 0, 0);
        st.setMaxLines(2);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tcol.addView(st);
        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.CENTER_VERTICAL);
        time.setPadding(0, dp(5), 0, 0);
        time.addView(Ui.text(this, "◷  " + Ui.t("Время в игре", "Time played") + "  ", 14, Ui.MUTED, Ui.body(this)));
        time.addView(Ui.text(this, formatPlayTime(cfg.playSeconds()), 15, Ui.AMBER, Ui.head(this)));
        tcol.addView(time);
        row.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(row);

        // The button.
        TextView play = Ui.primaryButton(this, ready ? "▶  " + Ui.t("ИГРАТЬ", "PLAY") : "▣  " + Ui.t("ВЫБРАТЬ ПАПКУ", "PICK FOLDER"),
                v -> {
                    if (ready) launchGame();
                    else startPick(true);
                });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(52));
        plp.topMargin = dp(10);
        c.addView(play, plp);

        // Graphics mode.
        LinearLayout gm = new LinearLayout(this);
        gm.setPadding(0, dp(10), 0, dp(5));
        TextView g1 = Ui.text(this, Ui.t("Режим графики", "Graphics mode"), 13, Ui.MUTED, Ui.body(this));
        gm.addView(g1, new LinearLayout.LayoutParams(0, -2, 1));
        gm.addView(Ui.text(this, cfg.mode() == GameConfig.MODE_CUSTOM ? Ui.t("Своя настройка", "Custom") : Ui.t("Применяется при запуске", "Applied at launch"), 13, Ui.MUTED, Ui.body(this)));
        c.addView(gm);
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(Ui.round(0x14FFFFFF, 0, 0, 6, this));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[][] modes = {{"Качество", "Quality"}, {"Баланс", "Balance"}, {"Скорость", "Speed"}};
        for (int m = 0; m < 3; m++) {
            final int mode = m;
            boolean on = cfg.mode() == m;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(6), dp(5), dp(6), dp(5));
            cell.setBackground(Ui.ripple(on ? Ui.round(0x22E0A63E, Ui.AMBER, 1.5f, 6, this) : Ui.round(0, 0, 0, 6, this), 0x33E0A63E));
            TextView a = Ui.text(this, Ui.t(modes[m][0], modes[m][1]), 16, on ? Ui.AMBER : Ui.IVORY, Ui.body(this));
            a.setGravity(Gravity.CENTER);
            cell.addView(a);
            TextView bsub = Ui.text(this, GameConfig.presetSummary(device.tier, m), 12, on ? Ui.AMBER : Ui.DIM, Ui.body(this));
            bsub.setGravity(Gravity.CENTER);
            bsub.setPadding(0, dp(2), 0, 0);
            cell.addView(bsub);
            cell.setOnClickListener(v -> {
                cfg.applyMode(device.tier, mode);
                refreshChips();
                showTab(TAB_PLAY);
            });
            seg.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        c.addView(seg);

        String warn = compatWarning();
        if (warn != null) {
            TextView w = Ui.text(this, warn, 13, Ui.mix(Ui.RED, Ui.IVORY, 0.45f), Ui.body(this));
            w.setPadding(0, dp(10), 0, 0);
            c.addView(w);
        }

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(c);
        int width = Math.min(dp(470), (int) (getResources().getDisplayMetrics().widthPixels * 0.48f));
        wrap.addView(sv, new FrameLayout.LayoutParams(width, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        return wrap;
    }

    private String compatWarning() {
        if (!device.cpuSupported())
            return Ui.t("Процессор без инструкций ARMv8.2 (LSE + RCpc): игра на нём не запустится. Нужны ядра Cortex-A55/A75 и новее (Helio G85, Snapdragon 665+, Exynos 850+).",
                    "This CPU lacks ARMv8.2 (LSE + RCpc): the game cannot run. Cortex-A55/A75 or newer is needed (Helio G85, Snapdragon 665+, Exynos 850+).");
        if (!device.hasVulkan11())
            return Ui.t("Vulkan 1.1 не найден: игра может не запуститься.", "Vulkan 1.1 not found: the game may fail to start.");
        if (device.ramMiB > 0 && device.ramMiB < 3500)
            return Ui.t("Мало памяти (меньше 4 ГБ): закройте другие приложения перед игрой.", "Less than 4 GB of RAM: close other apps before playing.");
        return null;
    }

    private static String formatPlayTime(long s) {
        if (s < 60) return Ui.t("0 мин", "0 min");
        long h = s / 3600, m = (s % 3600) / 60;
        if (h == 0) return m + Ui.t(" мин", " min");
        return h + Ui.t(" ч ", " h ") + m + Ui.t(" мин", " min");
    }

    // ------------------------------------------------------------------ SETTINGS

    private View settingsPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, Ui.t("Графика", "Graphics")));
        for (GameConfig.Option o : GameConfig.GRAPHICS) c.addView(optionRow(o, true));
        View sep = new View(this);
        sep.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 1);
        slp.setMargins(0, dp(14), 0, dp(14));
        c.addView(sep, slp);
        c.addView(Ui.caption(this, Ui.t("Звук и язык", "Audio and language")));
        for (GameConfig.Option o : GameConfig.AUDIO_LANG) c.addView(optionRow(o, false));
        TextView note = Ui.text(this, Ui.t("Все настройки применяются при следующем запуске игры.", "Everything applies the next time the game starts."), 13, Ui.DIM, Ui.body(this));
        note.setPadding(0, dp(12), 0, 0);
        c.addView(note);
        return scroll(c);
    }

    private ScrollView scroll(View content) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, 0, 0, dp(8));
        FrameLayout f = new FrameLayout(this);
        int width = Math.min(dp(760), (int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
        f.addView(content, new FrameLayout.LayoutParams(width, -2));
        sv.addView(f);
        return sv;
    }

    /** Label on the left, a row of pills on the right. */
    private View optionRow(GameConfig.Option o, boolean graphics) {
        return OptionRow.create(this, cfg, o, 17, val -> {
            if (graphics && o != GameConfig.DRIVER) cfg.markCustom();
            if (o == GameConfig.DRIVER && val == 2 && !device.isAdreno())
                toast(Ui.t("Turnip работает только на Adreno (Snapdragon).", "Turnip only works on Adreno (Snapdragon)."));
            refreshChips();
        });
    }

    // ------------------------------------------------------------------ CONTROLS

    private View controlsPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, Ui.t("Раскладка", "Layout")));
        TextView d = Ui.text(this, Ui.t("Три набора кнопок — пешком, за рулём и в меню. Каждую кнопку можно передвинуть, изменить её размер и прозрачность или спрятать.",
                "Three button sets: on foot, driving and menus. Every button can be moved, resized, faded or hidden."), 15, 0xFFCFC4B2, Ui.body(this));
        d.setPadding(0, dp(8), 0, dp(12));
        c.addView(d);
        LinearLayout btns = new LinearLayout(this);
        btns.addView(Ui.primaryButton(this, "✥  " + Ui.t("РЕДАКТОР РАСКЛАДКИ", "LAYOUT EDITOR"),
                v -> startActivity(new Intent(this, LayoutEditorActivity.class))), new LinearLayout.LayoutParams(0, dp(58), 1));
        TextView reset = Ui.ghostButton(this, Ui.t("Сбросить", "Reset"), v -> confirm(
                Ui.t("Сбросить раскладку?", "Reset the layout?"),
                Ui.t("Все кнопки вернутся на места по умолчанию.", "All buttons go back to their default places."),
                () -> {
                    ControlLayout.reset(this);
                    toast(Ui.t("Раскладка сброшена", "Layout reset"));
                }));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, dp(58));
        rlp.setMarginStart(dp(12));
        btns.addView(reset, rlp);
        c.addView(btns);
        View sep = new View(this);
        sep.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 1);
        slp.setMargins(0, dp(16), 0, dp(10));
        c.addView(sep, slp);
        c.addView(Ui.caption(this, Ui.t("Сенсор", "Touch")));
        for (GameConfig.Option o : GameConfig.TOUCH) c.addView(optionRow(o, false));
        return scroll(c);
    }

    // ------------------------------------------------------------------ FILES

    private View filesPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, Ui.t("Папка игры", "Game folder")));
        File game = currentGame();
        TextView path = Ui.text(this, game == null ? Ui.t("не выбрана", "not chosen") : game.getAbsolutePath(), 18, Ui.IVORY, Ui.head(this));
        path.setPadding(0, dp(8), 0, dp(10));
        c.addView(path);
        for (String r : GameFiles.REQUIRED) {
            boolean ok = game != null && GameFiles.find(game, r) != null;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(3), 0, dp(3));
            row.addView(Ui.text(this, ok ? "✔" : "✖", 15, ok ? Ui.GREEN : Ui.RED, Ui.body(this)));
            TextView n = Ui.text(this, r, 15, ok ? 0xFFCFC4B2 : Ui.IVORY, Ui.body(this));
            n.setPadding(dp(10), 0, 0, 0);
            row.addView(n);
            c.addView(row);
        }
        for (String[] opt : new String[][]{{"pc/sds_en", "Английский текст", "English text"}, {"pc/sds_ru_en", "Английская озвучка", "English speech"}}) {
            boolean ok = game != null && GameFiles.find(game, opt[0]) != null;
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(3), 0, dp(3));
            row.addView(Ui.text(this, ok ? "✔" : "–", 15, ok ? Ui.GREEN : Ui.DIM, Ui.body(this)));
            TextView n = Ui.text(this, opt[0] + "  ·  " + Ui.t(opt[1], opt[2]) + Ui.t(" (необязательно)", " (optional)"), 14, Ui.DIM, Ui.body(this));
            n.setPadding(dp(10), 0, 0, 0);
            row.addView(n);
            c.addView(row);
        }
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(16), 0, 0);
        btns.addView(Ui.primaryButton(this, Ui.t("ВЫБРАТЬ ПАПКУ", "PICK FOLDER"), v -> startPick(false)), new LinearLayout.LayoutParams(-2, dp(54)));
        if (!GameFiles.hasAllFilesAccess(this)) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(54));
            lp.setMarginStart(dp(12));
            btns.addView(Ui.ghostButton(this, Ui.t("Доступ к файлам", "File access"), v -> requestAccess()), lp);
        }
        c.addView(btns);
        TextView help = Ui.text(this, Ui.t(
                "Скопируйте установленную Mafia II с ПК (папки pc, edit и остальные) и папку physx с PhysXCore_283.dll и NxCooking_283.dll, например в «Внутренняя память/Mafia II». Затем нажмите «Выбрать папку».\n\nСвой драйвер Vulkan: положите .so в " + driverDirHint() + ".\nСохранения: " + savesHint(),
                "Copy the installed Mafia II from your PC (pc, edit and the rest) plus the physx folder with PhysXCore_283.dll and NxCooking_283.dll, e.g. to \"Internal storage/Mafia II\". Then tap Pick folder.\n\nCustom Vulkan driver: put a .so into " + driverDirHint() + ".\nSaves: " + savesHint()),
                13, Ui.DIM, Ui.body(this));
        help.setPadding(0, dp(14), 0, 0);
        help.setLineSpacing(0, 1.15f);
        c.addView(help);
        return scroll(c);
    }

    private String driverDirHint() {
        File d = getExternalFilesDir(null);
        return d == null ? "Android/data/" + getPackageName() + "/files/driver" : new File(d, "driver").getAbsolutePath();
    }

    private String savesHint() {
        File s = GameFiles.savesDir(this);
        return s == null ? "-" : s.getAbsolutePath();
    }

    // ------------------------------------------------------------------ ABOUT

    private View aboutPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, Ui.t("Авторы", "Credits")));
        TextView a = Ui.text(this, Ui.t("Порт Mafia II на Android", "Mafia II Android port"), 15, Ui.MUTED, Ui.body(this));
        a.setPadding(0, dp(10), 0, dp(2));
        c.addView(a);
        c.addView(Ui.text(this, "VinTeRuS", 30, Ui.IVORY, Ui.serif(this)));
        TextView a2 = Ui.text(this, Ui.t("Рекомпиляция игры, PhysX/APEX, D3D9 → Vulkan (DXVK), Turnip. Поддержите автора — без него этого порта бы не было.",
                "Game recompilation, PhysX/APEX, D3D9 → Vulkan (DXVK), Turnip. Support the author: there would be no port without him."), 14, 0xFFCFC4B2, Ui.body(this));
        a2.setPadding(0, dp(6), 0, dp(12));
        c.addView(a2);
        c.addView(Ui.ghostButton(this, "Patreon · VinTeRuS", v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.patreon.com/cw/VinTeRuS/membership")));
            } catch (Exception ignored) {
            }
        }), new LinearLayout.LayoutParams(-2, dp(48)));
        TextView l = Ui.text(this, Ui.t("Лаунчер, сенсорное управление, профили графики, иконки — Mafia II Droid " + VERSION + ". Шрифты Oswald и Playfair Display (SIL OFL).",
                "Launcher, touch controls, graphics profiles, icons: Mafia II Droid " + VERSION + ". Fonts: Oswald and Playfair Display (SIL OFL)."), 13, Ui.DIM, Ui.body(this));
        l.setPadding(0, dp(14), 0, dp(14));
        c.addView(l);

        c.addView(Ui.caption(this, Ui.t("Ваше устройство", "Your device")));
        String vk = device.vulkanApi == 0 ? "—" : ((device.vulkanApi >> 22) + "." + ((device.vulkanApi >> 12) & 0x3FF));
        String[][] rows = {
                {Ui.t("Чип", "Chip"), device.soc},
                {Ui.t("Графика", "GPU"), device.gpu},
                {Ui.t("Память", "Memory"), device.ramMiB > 0 ? String.format(Locale.ROOT, "%.1f GB", device.ramMiB / 1024f) : "—"},
                {Ui.t("Ядра", "Cores"), device.cores + " × " + (device.bigCoreMHz > 0 ? device.bigCoreMHz + " MHz" : "?")},
                {"ARMv8.2 (LSE + RCpc)", device.cpuSupported() ? Ui.t("есть", "yes") : Ui.t("нет — игра не запустится", "no — the game cannot run")},
                {"Vulkan", vk},
                {Ui.t("Класс", "Class"), device.tierName(Ui.english())},
        };
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(5), 0, dp(5));
            row.addView(Ui.text(this, r[0], 15, Ui.MUTED, Ui.body(this)), new LinearLayout.LayoutParams(dp(190), -2));
            row.addView(Ui.text(this, r[1], 15, Ui.IVORY, Ui.body(this)), new LinearLayout.LayoutParams(0, -2, 1));
            c.addView(row);
        }
        c.addView(Ui.caption(this, Ui.t("Что нового", "What's new")), captionLp());
        TextView news = Ui.text(this, Ui.t(
                "• Новый лаунчер: папка → игра, режимы Качество / Баланс / Скорость.\n• Профили под чип: Helio G85…G99, Dimensity, Exynos, Snapdragon.\n• Новое сенсорное управление: плавающий стик, обзор с кнопки огня, педали в машине, гироскоп.\n• Редактор раскладки: перемещение, размер, прозрачность, скрытие, сетка, отмена.\n• Проверка процессора и Vulkan до запуска.",
                "• New launcher: folder → game, Quality / Balance / Speed modes.\n• Per-chip profiles: Helio G85…G99, Dimensity, Exynos, Snapdragon.\n• New touch controls: floating stick, look while firing, car pedals, gyro.\n• Layout editor: move, resize, opacity, hide, grid snap, undo.\n• CPU and Vulkan checks before launch."),
                14, 0xFFCFC4B2, Ui.body(this));
        news.setLineSpacing(0, 1.2f);
        news.setPadding(0, dp(8), 0, 0);
        c.addView(news);
        return scroll(c);
    }

    private LinearLayout.LayoutParams captionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(16);
        return lp;
    }

    // ------------------------------------------------------------------ folder picking

    private File currentGame() {
        String p = cfg.gamePath();
        return p == null ? null : new File(p);
    }

    /** First run: if the game is already in one of the usual places, take it. */
    private void autodetectGame() {
        File cur = currentGame();
        if (cur != null && GameFiles.find(cur, GameFiles.EXE) != null) return;
        for (File f : GameFiles.candidates(this)) {
            if (f != null && GameFiles.find(f, GameFiles.EXE) != null) {
                cfg.setGamePath(f.getAbsolutePath());
                return;
            }
        }
    }

    private void startPick(boolean launchAfter) {
        launchAfterPick = launchAfter;
        if (!GameFiles.hasAllFilesAccess(this)) {
            confirm(Ui.t("Нужен доступ к файлам", "File access needed"),
                    Ui.t("Игра читает свои файлы напрямую с памяти телефона. Разрешите «Доступ ко всем файлам», затем выберите папку Mafia II.",
                            "The game reads its files straight from storage. Allow \"All files access\", then pick the Mafia II folder."),
                    () -> {
                        pickAfterAccess = true;
                        requestAccess();
                    });
            return;
        }
        openPicker();
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (pickAfterAccess && GameFiles.hasAllFilesAccess(this)) {
            pickAfterAccess = false;
            openPicker();
        }
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= 26) {
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Mafia II"));
        }
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            toast(Ui.t("Нет системного выбора папок. Положите игру в «Mafia II» в корне памяти.", "No folder picker. Put the game into \"Mafia II\" at the storage root."));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE || res != RESULT_OK || data == null || data.getData() == null) return;
        File picked = GameFiles.normalize(GameFiles.fromTreeUri(data.getData()));
        if (picked == null || !picked.isDirectory()) {
            toast(Ui.t("Эту папку нельзя открыть. Выберите папку во внутренней памяти или на SD-карте.", "This folder cannot be opened. Pick one in internal storage or on the SD card."));
            return;
        }
        cfg.setGamePath(picked.getAbsolutePath());
        List<String> miss = GameFiles.missing(picked);
        if (miss.isEmpty()) {
            if (launchAfterPick) {
                launchGame();
                return;
            }
            toast(Ui.t("Игра найдена", "Game found"));
        } else {
            message(Ui.t("Не все файлы на месте", "Some files are missing"),
                    picked.getAbsolutePath() + "\n\n" + Ui.t("Не хватает: ", "Missing: ") + String.join(", ", miss));
        }
        showTab(tab);
    }

    // ------------------------------------------------------------------ launching

    private void launchGame() {
        File game = currentGame();
        if (game == null || !GameFiles.missing(game).isEmpty()) {
            startPick(true);
            return;
        }
        if (!device.cpuSupported()) {
            message(Ui.t("Процессор не поддерживается", "CPU not supported"), compatWarning());
            return;
        }
        cfg.writeIni();
        try {
            Os.setenv("MAFIA2RE_GAME", game.getAbsolutePath(), true);
            File drv = installCustomDriver();
            if (drv == null) Os.unsetenv("MAFIA2RE_CUSTOM_DRIVER");
            else Os.setenv("MAFIA2RE_CUSTOM_DRIVER", drv.getAbsolutePath(), true);
        } catch (Exception ignored) {
        }
        startActivity(new Intent(this, M2GameActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    /** A .so dropped into files/driver is copied to private storage (same rule as the port's setup). */
    private File installCustomDriver() {
        File ext = getExternalFilesDir(null);
        File[] list = ext == null ? null : new File(ext, "driver").listFiles((d, n) -> n.endsWith(".so"));
        File dst = new File(new File(getFilesDir(), "driver"), "libvulkan_custom.so");
        if (list == null || list.length == 0) {
            dst.delete();
            return null;
        }
        File src = list[0];
        if (dst.exists() && dst.length() == src.length() && dst.lastModified() >= src.lastModified()) return dst;
        dst.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return dst;
        } catch (Exception e) {
            dst.delete();
            return null;
        }
    }

    // ------------------------------------------------------------------ dialogs

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    private void message(String title, String text) {
        dialog(title, text, null, null);
    }

    private void confirm(String title, String text, Runnable ok) {
        dialog(title, text, Ui.t("ПРОДОЛЖИТЬ", "CONTINUE"), ok);
    }

    private void dialog(String title, String text, String okLabel, Runnable ok) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(0xFF17130F, Ui.AMBER_DEEP, 1, 10, this));
        c.setPadding(dp(24), dp(20), dp(24), dp(18));
        TextView t = Ui.text(this, title, 22, Ui.IVORY, Ui.head(this));
        c.addView(t);
        TextView m = Ui.text(this, text, 15, 0xFFCFC4B2, Ui.body(this));
        m.setPadding(0, dp(10), 0, dp(16));
        m.setLineSpacing(0, 1.15f);
        c.addView(m);
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.END);
        b.addView(Ui.ghostButton(this, ok == null ? "OK" : Ui.t("Отмена", "Cancel"), v -> d.dismiss()));
        if (ok != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(dp(10));
            TextView go = Ui.primaryButton(this, okLabel, v -> {
                d.dismiss();
                ok.run();
            });
            go.setTextSize(16);
            go.setPadding(dp(18), dp(10), dp(18), dp(10));
            b.addView(go, lp);
        }
        c.addView(b);
        d.setContentView(c);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.min(dp(520), (int) (getResources().getDisplayMetrics().widthPixels * 0.8f)), -2);
        }
        d.show();
    }
}
