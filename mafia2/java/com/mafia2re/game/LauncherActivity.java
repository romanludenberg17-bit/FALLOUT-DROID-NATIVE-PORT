package com.mafia2re.game;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.system.Os;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
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
 * ReMaf2 launcher: a card with the big "Играть" button and the graphics mode over Mafia II
 * art, tabs for settings, controls, game files and credits. First launch is "pick the
 * folder, the game starts".
 */
public class LauncherActivity extends Activity {
    static final String VERSION = "1.2.3";
    static final String EXTRA_AUTOSTART = "remaf2.autostart";
    private static final int REQ_TREE = 41;
    private static final int TAB_PLAY = 0, TAB_SETTINGS = 1, TAB_CONTROLS = 2, TAB_FILES = 3, TAB_ABOUT = 4;
    private static final String[] TAB_NAMES = {"ИГРАТЬ", "НАСТРОЙКИ", "УПРАВЛЕНИЕ", "ФАЙЛЫ ИГРЫ", "О ПОРТЕ"};
    private static final String[] TAB_ART = {"img/keyart.jpg", "img/street_snow.jpg", "img/keyart.jpg", "img/street_police.jpg", "img/street_snow.jpg"};

    private static final String[] TIPS = {
            "Полиция Эмпайр-Бэй штрафует за превышение — включите ограничитель скорости в машине.",
            "Игра сохраняется в начале глав: не выходите из неё во время ролика.",
            "Тормозит на слабом телефоне? Режим «Скорость» и лимит 30 FPS дают ровную картинку.",
            "Толкните стик вперёд до упора — Вито побежит. Дотяните до замка — бег зафиксируется.",
            "Любую кнопку можно передвинуть, увеличить или спрятать: «Управление» → «Редактор».",
            "С геймпадом сенсорные кнопки прячутся сами.",
            "Гироскоп «при прицеле» помогает точнее стрелять.",
    };

    private GameConfig cfg;
    private DeviceProfile device;
    private ArtBackdrop backdrop;
    private FrameLayout page;
    private LinearLayout chips;
    private final TextView[] tabs = new TextView[5];
    private int tab = TAB_PLAY;
    private boolean pickAfterAccess;
    private boolean launchAfterPick = true;
    private TextView tipView;
    private int tipIndex;

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
        device = DeviceProfile.detectCached(this);
        cfg.ensureDefaults(device);
        autodetectGame();
        build();
        // The port author's own start-up module (news / notices): kept exactly as the original did.
        try {
            io.avm.r7.s.g(this);
        } catch (Throwable ignored) {
        }
        // Relaunch requested from the in-game port menu (graphics variant changed).
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_AUTOSTART, false)) {
            getIntent().removeExtra(EXTRA_AUTOSTART);
            page.post(this::launchGame);
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
        autodetectGame();   // access may have been granted meanwhile
        refreshChips();
        showTab(tab);
        if (cfg.takeFailedStart()) {
            if (device.isAdreno() && cfg.get(GameConfig.DRIVER) == 1) {
                // DXVK aborts on Qualcomm's own driver here: back to Turnip for the next start.
                cfg.applyAdrenoVariant(0);
                dialog("Системный драйвер не подошёл",
                        "Игра закрылась на драйвере Qualcomm, поэтому снова включён Turnip. Отправьте лог — в нём будет причина от DXVK.",
                        "ОТПРАВИТЬ ЛОГ", this::shareLog);
                return;
            }
            dialog("Игра закрылась при запуске",
                    "Похоже, игра не смогла стартовать. Отправьте лог (Telegram, почта) — по нему видно причину.\n\n"
                            + "Можно попробовать другой драйвер Vulkan: сейчас — «" + GameConfig.DRIVER.name(GameConfig.DRIVER.indexOf(cfg.get(GameConfig.DRIVER))) + "».",
                    "ОТПРАВИТЬ ЛОГ", this::shareLog);
        }
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
        backdrop = new ArtBackdrop(this, TAB_ART[0]);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // Header: ReMaf2 logo + subtitle on the left, chips on the right.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(36), dp(12), dp(28), dp(4));
        Emblem logo = new Emblem(this, Emblem.FULL);
        int lh = dp(46);
        header.addView(logo, new LinearLayout.LayoutParams(logo.widthFor(lh), lh));
        View bar = new View(this);
        bar.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(1), dp(34));
        blp.setMargins(dp(16), 0, dp(14), 0);
        header.addView(bar, blp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = Ui.text(this, "MAFIA II · ANDROID", 15, Ui.IVORY, Ui.head(this));
        t1.setLetterSpacing(0.14f);
        titles.addView(t1);
        TextView t2 = Ui.text(this, "нативный порт · сенсорное управление", 12, Ui.MUTED, Ui.body(this));
        t2.setPadding(0, dp(3), 0, 0);
        titles.addView(t2);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(chips);
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        page = new FrameLayout(this);
        page.setPadding(dp(36), dp(8), dp(28), dp(4));
        col.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout tip = new LinearLayout(this);
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setPadding(dp(36), dp(2), dp(28), dp(7));
        tip.addView(Ui.caption(this, "Совет"));
        tipView = Ui.text(this, "", 13, Ui.MUTED, Ui.body(this));
        tipView.setPadding(dp(12), 0, 0, 0);
        tipView.setSingleLine(true);
        tipView.setEllipsize(TextUtils.TruncateAt.END);
        tip.addView(tipView, new LinearLayout.LayoutParams(0, -2, 1));
        tip.setOnClickListener(v -> nextTip());
        // Short landscape phones (< 440dp tall) need every dp for the Play card.
        DisplayMetrics dm = getResources().getDisplayMetrics();
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
        tipView.setText(TIPS[tipIndex]);
    }

    private View tabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xF5080707);
        bar.setPadding(dp(26), 0, dp(28), 0);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView tv = Ui.text(this, TAB_NAMES[i], 18, Ui.IVORY, Ui.head(this));
            tv.setLetterSpacing(0.07f);
            tv.setPadding(dp(14), dp(10), dp(14), dp(8));
            tv.setGravity(Gravity.CENTER);
            tv.setOnClickListener(v -> showTab(idx));
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
        TextView v1 = Ui.text(this, "REMAF2 " + VERSION, 14, Ui.DIM, Ui.head(this));
        v1.setLetterSpacing(0.12f);
        v1.setGravity(Gravity.END);
        ver.addView(v1);
        TextView v2 = Ui.text(this, "Фанатский проект · нужна своя копия Mafia II", 11, Ui.DIM, Ui.body(this));
        v2.setPadding(0, dp(3), 0, 0);
        ver.addView(v2);
        bar.addView(ver);
        return bar;
    }

    private void showTab(int i) {
        tab = i;
        for (int k = 0; k < tabs.length; k++) {
            boolean on = k == i;
            tabs[k].setTextColor(on ? Ui.IVORY : 0xFF8F8A84);
            GradientDrawable under = new GradientDrawable();
            under.setColor(on ? Ui.ACCENT : 0);
            under.setCornerRadius(dp(2));
            LayerDrawable ld = new LayerDrawable(new Drawable[]{under});
            ld.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
            ld.setLayerHeight(0, dp(3));
            ld.setLayerInset(0, dp(14), 0, dp(14), dp(4));
            tabs[k].setBackground(Ui.ripple(ld, 0x40D9322F));
        }
        if (backdrop != null) backdrop.setImage(TAB_ART[i]);
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
        int dot = !device.cpuSupported() ? Ui.RED : device.tier == DeviceProfile.TIER_LOW ? 0xFFE0A63E : Ui.GREEN;
        LinearLayout c1 = Ui.chip(this, prettySoc(), dot);
        chips.addView(c1);
        int fps = cfg.get(GameConfig.FPS);
        LinearLayout c2 = Ui.chip(this, fps == 0 ? "БЕЗ ЛИМИТА" : fps + " FPS", 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(10));
        chips.addView(c2, lp);
        c1.setOnClickListener(v -> showTab(TAB_ABOUT));
        c2.setOnClickListener(v -> showTab(TAB_SETTINGS));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(42), dp(42));
        tp.setMarginStart(dp(10));
        chips.addView(telegramButton(), tp);
    }

    /** Round Telegram button (top right, like in the other ports). */
    private View telegramButton() {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xCC1A1717);
        g.setStroke(dp(1), 0x55FFFFFF);
        f.setBackground(Ui.ripple(g, 0x402AABEE));
        android.widget.ImageView iv = new android.widget.ImageView(this);
        Drawable d = getDrawable(getResources().getIdentifier("m2_ic_telegram", "drawable", getPackageName()));
        if (d != null) {
            d = d.mutate();
            d.setTint(0xFF2AABEE);
            iv.setImageDrawable(d);
        }
        f.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        f.setContentDescription("Telegram");
        f.setOnClickListener(v -> Links.openTelegram(this));
        return f;
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
        c.setBackground(Ui.round(Ui.CARD, 0x30FFFFFF, 1, 12, this));
        c.setPadding(dp(20), dp(14), dp(20), dp(14));
        c.setElevation(dp(10));
        return c;
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

    private View separator(int top, int bottom) {
        View sep = new View(this);
        sep.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 1);
        slp.setMargins(0, dp(top), 0, dp(bottom));
        sep.setLayoutParams(slp);
        return sep;
    }

    // ------------------------------------------------------------------ ИГРАТЬ

    private View playPage() {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout c = card();
        File game = currentGame();
        List<String> missing = game == null ? null : GameFiles.missing(game);
        boolean ready = game != null && missing.isEmpty();
        long[] saves = GameFiles.saves(this);

        c.addView(Ui.caption(this, !ready ? "Подготовка" : saves[0] > 0 ? "Продолжить историю" : "Новая история"));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(Ui.round(0xFF050505, 0x30FFFFFF, 1, 8, this));
        tile.addView(new Emblem(this, Emblem.II), new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(tile, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(dp(14), 0, 0, 0);
        String title, sub;
        if (!ready) {
            title = "ВЫБЕРИТЕ ПАПКУ С ИГРОЙ";
            sub = game == null ? "Скопируйте Mafia II с ПК вместе с папкой physx и укажите её."
                    : "Не хватает: " + String.join(", ", missing);
        } else if (saves[0] > 0) {
            title = "СОХРАНЕНИЙ: " + saves[0];
            sub = "последнее — " + DateUtils.getRelativeDateTimeString(this, saves[1], DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0);
        } else {
            title = "ВИТО ВЕРНУЛСЯ ДОМОЙ";
            sub = "Добро пожаловать в Эмпайр-Бэй.";
        }
        TextView tt = Ui.text(this, title, 24, Ui.IVORY, Ui.head(this));
        tt.setLetterSpacing(0.02f);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        tcol.addView(tt);
        TextView st = Ui.text(this, sub, 13, ready ? 0xFFD4D0CA : Ui.RED, Ui.body(this));
        st.setPadding(0, dp(4), 0, 0);
        st.setMaxLines(2);
        st.setEllipsize(TextUtils.TruncateAt.END);
        tcol.addView(st);
        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.CENTER_VERTICAL);
        time.setPadding(0, dp(5), 0, 0);
        time.addView(Ui.text(this, "◷  Время в игре  ", 13, Ui.MUTED, Ui.body(this)));
        time.addView(Ui.text(this, formatPlayTime(cfg.playSeconds()), 15, Ui.IVORY, Ui.head(this)));
        tcol.addView(time);
        row.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(row);

        TextView play = Ui.primaryButton(this, ready ? "▶  ИГРАТЬ" : "ВЫБРАТЬ ПАПКУ", v -> {
            if (ready) launchGame();
            else startPick(true);
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(52));
        plp.topMargin = dp(10);
        c.addView(play, plp);

        LinearLayout gm = new LinearLayout(this);
        gm.setPadding(0, dp(10), 0, dp(5));
        gm.addView(Ui.text(this, "Режим графики", 13, Ui.MUTED, Ui.body(this)), new LinearLayout.LayoutParams(0, -2, 1));
        gm.addView(Ui.text(this, cfg.mode() == GameConfig.MODE_CUSTOM ? "Своя настройка" : "Применяется при запуске", 13, Ui.MUTED, Ui.body(this)));
        c.addView(gm);
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(Ui.round(0x12FFFFFF, 0, 0, 8, this));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] modes = {"Качество", "Баланс", "Скорость"};
        for (int m = 0; m < 3; m++) {
            final int mode = m;
            boolean on = cfg.mode() == m;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(6), dp(5), dp(6), dp(5));
            cell.setBackground(Ui.ripple(on ? Ui.round(0x33D9322F, Ui.ACCENT, 1.5f, 7, this) : Ui.round(0, 0, 0, 7, this), 0x40D9322F));
            TextView a = Ui.text(this, modes[m], 16, Ui.IVORY, Ui.bodyBold(this));
            a.setGravity(Gravity.CENTER);
            cell.addView(a);
            TextView bsub = Ui.text(this, GameConfig.presetSummary(device, m), 11, on ? 0xFFFF8A80 : Ui.DIM, Ui.body(this));
            bsub.setGravity(Gravity.CENTER);
            bsub.setPadding(0, dp(2), 0, 0);
            cell.addView(bsub);
            cell.setOnClickListener(v -> {
                cfg.applyMode(device, mode);
                refreshChips();
                showTab(TAB_PLAY);
            });
            seg.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        c.addView(seg);

        String warn = compatWarning();
        if (warn != null) {
            TextView w = Ui.text(this, warn, 12, Ui.RED, Ui.body(this));
            w.setPadding(0, dp(8), 0, 0);
            c.addView(w);
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(c);
        int width = Math.min(dp(470), (int) (getResources().getDisplayMetrics().widthPixels * 0.46f));
        wrap.addView(sv, new FrameLayout.LayoutParams(width, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        return wrap;
    }

    private String compatWarning() {
        if (!device.cpuSupported())
            return "Процессор без инструкций ARMv8.2 (LSE + RCpc): игра на нём не запустится. Нужны ядра Cortex-A55/A75 и новее (Helio G85, Snapdragon 665+, Exynos 850+).";
        if (!device.hasVulkan11()) return "Vulkan 1.1 не найден: игра может не запуститься.";
        if (device.ramMiB > 0 && device.ramMiB < 3500) return "Мало памяти (меньше 4 ГБ): закройте другие приложения перед игрой.";
        return null;
    }

    private static String formatPlayTime(long s) {
        if (s < 60) return "0 мин";
        long h = s / 3600, m = (s % 3600) / 60;
        return h == 0 ? m + " мин" : h + " ч " + m + " мин";
    }

    // ------------------------------------------------------------------ НАСТРОЙКИ

    private View settingsPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, "Графика"));
        for (GameConfig.Option o : GameConfig.GRAPHICS) c.addView(optionRow(o, true));
        c.addView(separator(14, 12));
        c.addView(Ui.caption(this, "Звук и язык игры"));
        for (GameConfig.Option o : GameConfig.AUDIO_LANG) c.addView(optionRow(o, false));
        TextView note = Ui.text(this, "Все настройки применяются при следующем запуске игры.", 12, Ui.DIM, Ui.body(this));
        note.setPadding(0, dp(12), 0, 0);
        c.addView(note);
        return scroll(c);
    }

    private View optionRow(GameConfig.Option o, boolean graphics) {
        return OptionRow.create(this, cfg, o, 16, val -> {
            if (graphics && o != GameConfig.DRIVER) cfg.markCustom();
            if (o == GameConfig.DRIVER && val == 2 && !device.isAdreno()) toast("Turnip работает только на Adreno (Snapdragon).");
            refreshChips();
        });
    }

    // ------------------------------------------------------------------ УПРАВЛЕНИЕ

    private View controlsPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, "Раскладка"));
        TextView d = Ui.text(this, "Три набора кнопок — пешком, за рулём и в меню. Каждую кнопку можно передвинуть, изменить её размер и прозрачность или спрятать.",
                14, 0xFFD4D0CA, Ui.body(this));
        d.setPadding(0, dp(8), 0, dp(12));
        d.setLineSpacing(0, 1.1f);
        c.addView(d);
        LinearLayout btns = new LinearLayout(this);
        btns.addView(Ui.primaryButton(this, "✥  РЕДАКТОР РАСКЛАДКИ", v -> startActivity(new Intent(this, LayoutEditorActivity.class))),
                new LinearLayout.LayoutParams(0, dp(54), 1));
        TextView reset = Ui.ghostButton(this, "Сбросить", v -> confirm("Сбросить раскладку?", "Все кнопки вернутся на места по умолчанию.", () -> {
            ControlLayout.reset(this);
            toast("Раскладка сброшена");
        }));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, dp(54));
        rlp.setMarginStart(dp(12));
        btns.addView(reset, rlp);
        c.addView(btns);
        c.addView(separator(16, 10));
        c.addView(Ui.caption(this, "Стик и сенсор"));
        for (GameConfig.Option o : GameConfig.TOUCH) c.addView(optionRow(o, false));
        return scroll(c);
    }

    // ------------------------------------------------------------------ ФАЙЛЫ ИГРЫ

    private View filesPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, "Папка игры"));
        File game = currentGame();
        TextView path = Ui.text(this, game == null ? "не выбрана" : game.getAbsolutePath(), 17, Ui.IVORY, Ui.semi(this));
        path.setPadding(0, dp(8), 0, dp(10));
        c.addView(path);
        for (String r : GameFiles.REQUIRED) {
            boolean ok = game != null && GameFiles.find(game, r) != null;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(3), 0, dp(3));
            row.addView(Ui.text(this, ok ? "✔" : "✖", 14, ok ? Ui.GREEN : Ui.RED, Ui.body(this)));
            TextView n = Ui.text(this, r, 14, ok ? 0xFFD4D0CA : Ui.IVORY, Ui.body(this));
            n.setPadding(dp(10), 0, 0, 0);
            row.addView(n);
            c.addView(row);
        }
        for (String[] opt : new String[][]{{"pc/sds_en", "английский текст"}, {"pc/sds_ru_en", "английская озвучка"}}) {
            boolean ok = game != null && GameFiles.find(game, opt[0]) != null;
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(3), 0, dp(3));
            row.addView(Ui.text(this, ok ? "✔" : "–", 14, ok ? Ui.GREEN : Ui.DIM, Ui.body(this)));
            TextView n = Ui.text(this, opt[0] + "  ·  " + opt[1] + " (необязательно)", 13, Ui.DIM, Ui.body(this));
            n.setPadding(dp(10), 0, 0, 0);
            row.addView(n);
            c.addView(row);
        }
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(16), 0, 0);
        btns.addView(Ui.primaryButton(this, "ВЫБРАТЬ ПАПКУ", v -> startPick(false)), new LinearLayout.LayoutParams(-2, dp(52)));
        if (!GameFiles.hasAllFilesAccess(this)) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(52));
            lp.setMarginStart(dp(12));
            btns.addView(Ui.ghostButton(this, "Доступ к файлам", v -> requestAccess()), lp);
        }
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, dp(52));
        llp.setMarginStart(dp(12));
        btns.addView(Ui.ghostButton(this, "Отправить лог", v -> shareLog()), llp);
        c.addView(btns);
        TextView help = Ui.text(this, "Скопируйте установленную Mafia II с ПК (папки pc, edit и остальные) и папку physx с PhysXCore_283.dll и NxCooking_283.dll, например в «Внутренняя память/Mafia II». Затем нажмите «Выбрать папку».\n\n"
                + "Свой драйвер Vulkan: положите .so в " + driverDirHint() + ".\nСохранения: " + savesHint(), 12, Ui.DIM, Ui.body(this));
        help.setPadding(0, dp(14), 0, 0);
        help.setLineSpacing(0, 1.15f);
        c.addView(help);
        return scroll(c);
    }

    /** Sends the game's log (mafia2-native.log, the previous run first) as text: Telegram, mail... */
    private void shareLog() {
        File dir = getExternalFilesDir(null);
        StringBuilder sb = new StringBuilder();
        sb.append("ReMaf2 ").append(VERSION).append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE).append('\n')
                .append(device.soc).append(" · ").append(device.gpu).append(" · ").append(device.ramMiB).append(" MiB\n")
                .append("ini: ").append(readTail(new File(dir, "mafia2re.ini"), 4000)).append('\n');
        File note = dir == null ? null : new File(dir, "remaf2-launch.txt");
        if (note != null && note.exists()) sb.append("===== remaf2-launch.txt =====\n").append(readTail(note, 4000));
        File[] dxvk = dir == null ? null : dir.listFiles((d, n) -> n.endsWith("_d3d9.log") || n.endsWith("_dxgi.log"));
        if (dxvk != null) for (File f : dxvk) sb.append("===== ").append(f.getName()).append(" =====\n").append(readTail(f, 20_000)).append('\n');
        for (String n : new String[]{"mafia2-native.log", "mafia2-native.prev.log"}) {
            File f = dir == null ? null : new File(dir, n);
            if (f == null || !f.exists()) continue;
            StringBuilder body = new StringBuilder();
            int skipped = 0;
            for (String line : readTail(f, 400_000).split("\n")) {
                if (line.startsWith("VirtualAlloc(") || line.startsWith("SetThreadPriority(")) {
                    skipped++;
                    continue;
                }
                body.append(line).append('\n');
            }
            String b = body.length() > 60_000 ? body.substring(body.length() - 60_000) : body.toString();
            sb.append("===== ").append(n).append(" (").append(skipped).append(" строк памяти/потоков скрыто) =====\n").append(b).append('\n');
        }
        if (sb.indexOf("=====") < 0) {
            toast("Лога пока нет — запустите игру хотя бы раз.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "ReMaf2 log")
                .putExtra(Intent.EXTRA_TEXT, sb.toString());
        try {
            startActivity(Intent.createChooser(i, "Отправить лог"));
        } catch (Exception e) {
            toast("Не удалось открыть отправку.");
        }
    }

    private static String readTail(File f, int max) {
        try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r")) {
            long len = r.length(), from = Math.max(0, len - max);
            byte[] b = new byte[(int) (len - from)];
            r.seek(from);
            r.readFully(b);
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "—";
        }
    }

    private String driverDirHint() {
        File d = getExternalFilesDir(null);
        return d == null ? "Android/data/" + getPackageName() + "/files/driver" : new File(d, "driver").getAbsolutePath();
    }

    private String savesHint() {
        File s = GameFiles.savesDir(this);
        return s == null ? "—" : s.getAbsolutePath();
    }

    // ------------------------------------------------------------------ О ПОРТЕ

    private View aboutPage() {
        LinearLayout c = card();
        c.addView(Ui.caption(this, "Авторы"));
        TextView a = Ui.text(this, "Порт Mafia II на Android", 13, Ui.MUTED, Ui.body(this));
        a.setPadding(0, dp(10), 0, dp(2));
        c.addView(a);
        c.addView(Ui.text(this, "VinTeRuS", 30, Ui.IVORY, Ui.head(this)));
        TextView a2 = Ui.text(this, "Рекомпиляция игры, PhysX/APEX, D3D9 → Vulkan (DXVK), Turnip. Поддержите автора — без него этого порта бы не было.",
                13, 0xFFD4D0CA, Ui.body(this));
        a2.setPadding(0, dp(6), 0, dp(12));
        a2.setLineSpacing(0, 1.1f);
        c.addView(a2);
        c.addView(Ui.ghostButton(this, "Patreon · VinTeRuS", v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.patreon.com/cw/VinTeRuS/membership")));
            } catch (Exception ignored) {
            }
        }), new LinearLayout.LayoutParams(-2, dp(46)));
        LinearLayout.LayoutParams tgl = new LinearLayout.LayoutParams(-2, dp(46));
        tgl.topMargin = dp(10);
        c.addView(Ui.ghostButton(this, "Telegram · @" + Links.TELEGRAM_CHANNEL, v -> Links.openTelegram(this)), tgl);
        TextView l = Ui.text(this, "ReMaf2 " + VERSION + " — лаунчер, сенсорное управление, профили графики.\n"
                        + "Иконки кнопок: game-icons.net (Lorc, Delapouite, Skoll, Guard13007), CC BY 3.0.\n"
                        + "Шрифты: Manrope, Fira Sans Extra Condensed (SIL OFL).\n"
                        + "Mafia II и её изображения © 2K Games / Hangar 13. Фанатский проект, не связан с правообладателями.",
                12, Ui.DIM, Ui.body(this));
        l.setPadding(0, dp(14), 0, dp(14));
        l.setLineSpacing(0, 1.15f);
        c.addView(l);

        c.addView(Ui.caption(this, "Ваше устройство"));
        String vk = device.vulkanApi == 0 ? "—" : ((device.vulkanApi >> 22) + "." + ((device.vulkanApi >> 12) & 0x3FF));
        String[][] rows = {
                {"Чип", device.soc},
                {"Графика", device.gpu},
                {"Память", device.ramMiB > 0 ? String.format(Locale.ROOT, "%.1f ГБ", device.ramMiB / 1024f) : "—"},
                {"Ядра", device.cores + " × " + (device.bigCoreMHz > 0 ? device.bigCoreMHz + " МГц" : "?")},
                {"ARMv8.2 (LSE + RCpc)", device.cpuSupported() ? "есть" : "нет — игра не запустится"},
                {"Vulkan", vk},
                {"Профиль", device.profileNote()},
        };
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(5), 0, dp(5));
            row.addView(Ui.text(this, r[0], 14, Ui.MUTED, Ui.body(this)), new LinearLayout.LayoutParams(dp(190), -2));
            row.addView(Ui.text(this, r[1], 14, Ui.IVORY, Ui.bodyBold(this)), new LinearLayout.LayoutParams(0, -2, 1));
            c.addView(row);
        }
        TextView cap = Ui.caption(this, "Что нового в " + VERSION);
        cap.setPadding(0, dp(16), 0, 0);
        c.addView(cap);
        TextView news = Ui.text(this,
                "• Новый облик: логотип ReMaf2, арты Mafia II, шрифты Manrope и Fira Sans.\n"
                        + "• Стик как в шутерах: меньше, бег при толчке вперёд, фиксация бега замком.\n"
                        + "• Иконки кнопок с game-icons.net.\n"
                        + "• Всё приложение на русском.\n"
                        + "• Если игра закрылась при запуске — лаунчер предложит отправить лог.\n"
                        + "• Adreno 8xx: новая сборка Turnip и режим совместимости (sysmem + noubwc) — сами.\n"
                        + "• Перезапуск игры из меню порта больше не теряет папку игры; логи DXVK в «Отправить лог».\n"
                        + "• Профили для Helio G85/G99/G100 и Mali: 480p в «Скорости» на Mali-G52, анизотропия, ткань APEX.\n"
                        + "• Профили памяти 4 и 6 ГБ: ограничение текстур, срез мипов, 16-бит распаковка DXT на Mali.\n"
                        + "• Кнопка «Отправить лог» во вкладке «Файлы игры», Telegram в шапке.",
                13, 0xFFD4D0CA, Ui.body(this));
        news.setLineSpacing(0, 1.25f);
        news.setPadding(0, dp(8), 0, 0);
        c.addView(news);
        return scroll(c);
    }

    // ------------------------------------------------------------------ выбор папки

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
            confirm("Нужен доступ к файлам",
                    "Игра читает свои файлы напрямую из памяти телефона. Разрешите «Доступ ко всем файлам», затем выберите папку Mafia II.",
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
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Mafia II"));
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            toast("Нет системного выбора папок. Положите игру в «Mafia II» в корне памяти.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE || res != RESULT_OK || data == null || data.getData() == null) return;
        File picked = GameFiles.normalize(GameFiles.fromTreeUri(data.getData()));
        if (picked == null || !picked.isDirectory()) {
            toast("Эту папку нельзя открыть. Выберите папку во внутренней памяти или на SD-карте.");
            return;
        }
        cfg.setGamePath(picked.getAbsolutePath());
        List<String> miss = GameFiles.missing(picked);
        if (miss.isEmpty()) {
            if (launchAfterPick) {
                launchGame();
                return;
            }
            toast("Игра найдена");
        } else {
            message("Не все файлы на месте", picked.getAbsolutePath() + "\n\nНе хватает: " + String.join(", ", miss));
        }
        showTab(tab);
    }

    // ------------------------------------------------------------------ запуск

    private void launchGame() {
        File game = currentGame();
        if (game == null || !GameFiles.missing(game).isEmpty()) {
            startPick(true);
            return;
        }
        if (!device.cpuSupported()) {
            message("Процессор не поддерживается", compatWarning());
            return;
        }
        GameEnv.apply(this);
        startActivity(new Intent(this, M2GameActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ------------------------------------------------------------------ диалоги

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    private void message(String title, String text) {
        dialog(title, text, null, null);
    }

    private void confirm(String title, String text, Runnable ok) {
        dialog(title, text, "ПРОДОЛЖИТЬ", ok);
    }

    private void dialog(String title, String text, String okLabel, Runnable ok) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(0xFF121112, Ui.ACCENT_DEEP, 1, 12, this));
        c.setPadding(dp(24), dp(20), dp(24), dp(18));
        c.addView(Ui.text(this, title, 22, Ui.IVORY, Ui.head(this)));
        TextView m = Ui.text(this, text, 14, 0xFFD4D0CA, Ui.body(this));
        m.setPadding(0, dp(10), 0, dp(16));
        m.setLineSpacing(0, 1.15f);
        c.addView(m);
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.END);
        b.addView(Ui.ghostButton(this, ok == null ? "Понятно" : "Отмена", v -> d.dismiss()));
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
