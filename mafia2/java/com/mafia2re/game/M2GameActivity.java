package com.mafia2re.game;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The port's game activity with our controls: everything the original GameActivity does
 * (libraries, environment, refresh rate, the author credit) stays; only its touch overlay is
 * swapped for {@link ControlsOverlay}, and a port menu (gear button) is added.
 */
public class M2GameActivity extends GameActivity implements ControlsOverlay.Listener {
    private ControlsOverlay overlay;
    private FrameLayout host;
    private GameConfig cfg;
    private long resumedAt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new GameConfig(this);
        cfg.markGameStart();
        // Still alive after 25 s: the game started fine (rendering, data, driver).
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(cfg::markGameAlive, 25_000);
        ViewGroup content = findViewById(android.R.id.content);
        int at = -1;
        if (content != null) {
            for (int i = content.getChildCount() - 1; i >= 0; i--) {
                View v = content.getChildAt(i);
                if (v instanceof TouchControls) {
                    content.removeViewAt(i);
                    at = i;
                }
            }
        }
        overlay = new ControlsOverlay(this, cfg, true);
        overlay.setListener(this);
        if (content != null && at >= 0) content.addView(overlay, at, new FrameLayout.LayoutParams(-1, -1));
        else addContentView(overlay, new ViewGroup.LayoutParams(-1, -1));
        host = new FrameLayout(this);
        addContentView(host, new ViewGroup.LayoutParams(-1, -1));
        host.setVisibility(View.GONE);
        applyFpsLimit();
    }

    private void applyFpsLimit() {
        try {
            TouchControls.nativeSetFpsLimit(cfg.get(GameConfig.FPS));
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumedAt = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onPause() {
        if (resumedAt > 0) cfg.addPlaySeconds((SystemClock.elapsedRealtime() - resumedAt) / 1000);
        resumedAt = 0;
        if (overlay != null) overlay.releaseAll();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (overlay != null) overlay.detach();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (host != null && host.getVisibility() == View.VISIBLE) {
            closePanel();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ port menu

    @Override
    public void onGear() {
        openPanel();
    }

    @Override
    public void onSelection(ControlLayout.Element e) {
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void openPanel() {
        overlay.releaseAll();
        host.removeAllViews();
        host.setBackgroundColor(0xB3000000);
        host.setClickable(true);
        host.setOnClickListener(v -> closePanel());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(0xF215110E, 0x55D9322F, 1, 12, this));
        card.setPadding(dp(22), dp(16), dp(22), dp(16));
        card.setClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new Emblem(this), new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView title = Ui.text(this, Ui.t("МЕНЮ ПОРТА", "PORT MENU"), 22, Ui.IVORY, Ui.head(this));
        title.setLetterSpacing(0.1f);
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(Ui.ghostButton(this, "✕", v -> closePanel()));
        card.addView(head);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(6), 0, dp(6));
        if (DeviceProfile.detectCached(this).isAdreno()) rows.addView(adrenoBlock());
        rows.addView(OptionRow.create(this, cfg, GameConfig.FPS, 16, v -> {
            applyFpsLimit();
            cfg.writeIni();
        }));
        GameConfig.Option[] live = {GameConfig.SHOW_FPS, GameConfig.OPACITY, GameConfig.SCALE, GameConfig.CAM, GameConfig.INVERT,
                GameConfig.GYRO, GameConfig.AIM, GameConfig.STICK, GameConfig.FIRE_LOOK, GameConfig.LABELS, GameConfig.VIBRATE};
        for (GameConfig.Option o : live) rows.addView(OptionRow.create(this, cfg, o, 16, v -> overlay.reloadSettings()));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(rows);
        card.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(10), 0, 0);
        btns.addView(Ui.primaryButton(this, Ui.t("ПРОДОЛЖИТЬ", "RESUME"), v -> closePanel()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(52));
        lp.setMarginStart(dp(10));
        btns.addView(Ui.ghostButton(this, Ui.t("Раскладка", "Layout"), v -> openEditor()), lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, dp(52));
        lp2.setMarginStart(dp(10));
        btns.addView(Ui.ghostButton(this, Ui.t("В лаунчер", "Launcher"), v -> confirmQuit()), lp2);
        card.addView(btns);

        int w = Math.min(dp(640), (int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.9f);
        host.addView(card, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
        host.setVisibility(View.VISIBLE);
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        card.setAlpha(0f);
        card.animate().scaleX(1).scaleY(1).alpha(1).setDuration(160).start();
    }

    /** "Графика Adreno": pick a driver variant, the game restarts with it right away. */
    private android.view.View adrenoBlock() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(12));
        box.addView(Ui.caption(this, "Графика Adreno"));
        TextView hint = Ui.text(this, "Если на экране полосы или мусор — выберите другой вариант. Игра перезапустится, прогресс с последнего сохранения.",
                12, Ui.DIM, Ui.body(this));
        hint.setPadding(0, dp(4), 0, dp(6));
        box.addView(hint);
        int cur = cfg.adrenoVariant();
        for (int i = 0; i < GameConfig.ADRENO_VARIANTS.length; i++) {
            final int idx = i;
            boolean on = i == cur;
            TextView b = Ui.text(this, (on ? "●  " : "○  ") + GameConfig.ADRENO_VARIANTS[i], 15, on ? Ui.IVORY : 0xFFD4D0CA, Ui.body(this));
            b.setPadding(dp(12), dp(10), dp(12), dp(10));
            b.setBackground(Ui.ripple(on ? Ui.round(0x33D9322F, Ui.ACCENT, 1, 8, this) : Ui.round(0x12FFFFFF, 0, 0, 8, this), 0x40D9322F));
            b.setOnClickListener(v -> {
                cfg.applyAdrenoVariant(idx);
                relaunchGame();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            box.addView(b, lp);
        }
        return box;
    }

    /** Ends the process and lets the launcher start the game again with the new settings. */
    private void relaunchGame() {
        cfg.markGameAlive();
        Intent i = new Intent(this, LauncherActivity.class).putExtra(LauncherActivity.EXTRA_AUTOSTART, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 8, i, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, pi);
        if (resumedAt > 0) cfg.addPlaySeconds((SystemClock.elapsedRealtime() - resumedAt) / 1000);
        resumedAt = 0;
        finishAffinity();
        Process.killProcess(Process.myPid());
    }

    private void closePanel() {
        host.removeAllViews();
        host.setVisibility(View.GONE);
        overlay.reloadSettings();
    }

    private void openEditor() {
        host.removeAllViews();
        host.setBackgroundColor(0x66000000);
        host.setOnClickListener(null);
        host.setClickable(false);
        // The editor works on the live overlay: move it into the host for the duration.
        ViewGroup parent = (ViewGroup) overlay.getParent();
        int idx = parent.indexOfChild(overlay);
        parent.removeView(overlay);
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        EditorChrome chrome = new EditorChrome(this, overlay, ControlLayout.FOOT, () -> {
            overlay.setEditing(false, ControlLayout.FOOT);
            overlay.setListener(this);
            host.removeAllViews();
            parent.addView(overlay, idx, new FrameLayout.LayoutParams(-1, -1));
            host.setVisibility(View.GONE);
            overlay.reloadSettings();
        });
        host.addView(chrome, new FrameLayout.LayoutParams(-1, -1));
        host.setVisibility(View.VISIBLE);
    }

    private void confirmQuit() {
        host.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(0xF215110E, 0x55D9322F, 1, 12, this));
        card.setPadding(dp(24), dp(20), dp(24), dp(18));
        card.setClickable(true);
        card.addView(Ui.text(this, Ui.t("Выйти в лаунчер?", "Quit to the launcher?"), 22, Ui.IVORY, Ui.head(this)));
        TextView m = Ui.text(this, Ui.t("Несохранённый прогресс будет потерян. Игра сохраняется в начале глав.",
                "Unsaved progress is lost. The game saves at the start of chapters."), 15, 0xFFD4D0CA, Ui.body(this));
        m.setPadding(0, dp(10), 0, dp(16));
        card.addView(m);
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.END);
        b.addView(Ui.ghostButton(this, Ui.t("Отмена", "Cancel"), v -> openPanel()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(10));
        TextView q = Ui.primaryButton(this, Ui.t("ВЫЙТИ", "QUIT"), v -> quitToLauncher());
        q.setTextSize(16);
        b.addView(q, lp);
        card.addView(b);
        host.addView(card, new FrameLayout.LayoutParams(Math.min(dp(480), (int) (getResources().getDisplayMetrics().widthPixels * 0.8f)), -2, Gravity.CENTER));
    }

    /** The native game cannot be restarted in-process: schedule the launcher, then end the process. */
    private void quitToLauncher() {
        cfg.markGameAlive();   // the player left on purpose: not a failed start
        Intent i = new Intent(this, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 400, pi);
        if (resumedAt > 0) cfg.addPlaySeconds((SystemClock.elapsedRealtime() - resumedAt) / 1000);
        resumedAt = 0;
        finishAffinity();
        Process.killProcess(Process.myPid());
    }
}
