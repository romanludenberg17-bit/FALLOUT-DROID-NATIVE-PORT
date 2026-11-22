package com.wastelanddroid;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.wastelanddroid.R;
import com.wastelanddroid.controls.PcLayout;
import com.wastelanddroid.engine.FalloutEngine;
import com.wastelanddroid.game.DeviceProfile;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.game.GameFiles;
import com.wastelanddroid.game.GraphicsSettings;
import com.wastelanddroid.game.SaveBackup;
import com.wastelanddroid.mods.ModManager;
import com.wastelanddroid.util.Anim;
import com.wastelanddroid.util.CoverView;
import com.wastelanddroid.util.CrashLog;
import com.wastelanddroid.util.Files2;
import com.wastelanddroid.util.Links;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.ScanlineView;
import com.wastelanddroid.util.Ui;
import com.wastelanddroid.util.WastelandBackdrop;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    public static final String EXTRA_AUTOSTART = "autostart";
    public static final String ONBOARDED = "onboarded_v2";
    private static final int REQ_GAME_DIR = 1;
    private String appliedAccent;
    private WastelandBackdrop backdrop;
    private TextView engineChip;
    private TextView headerTitle;
    public boolean launching;
    public SharedPreferences prefs;
    private final List<Animator> loops = new ArrayList();
    private final Map<GameDef, GameCard> cards = new EnumMap(GameDef.class);
    public GameDef pendingGame = GameDef.FO3;

    /* JADX INFO: Access modifiers changed from: private */
    final class GameCard {
        final CoverView cover;
        final GameDef def;
        final ImageView gear;
        final Button play;
        Animator pulse;
        boolean ready;
        final TextView status;

        GameCard(GameDef gameDef) {
            this.cover = new CoverView(MainActivity.this);
            this.status = new TextView(MainActivity.this);
            this.gear = new ImageView(MainActivity.this);
            this.def = gameDef;
            Button button = Ui.button(MainActivity.this, R.drawable.ic_play, "ИГРАТЬ", new View.OnClickListener() { // from class: com.wastelanddroid.MainActivity$GameCard$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    GameCard.this.lambda$new$0(view);
                }
            });
            this.play = button;
            button.setTextSize(19.0f);
            button.setLetterSpacing(0.18f);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$new$0(View view) {
            MainActivity.this.onPlay(this.def);
        }

        View build() {
            LinearLayout linearLayoutColumn = Ui.column(MainActivity.this);
            linearLayoutColumn.addView(this.cover, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            this.cover.setTagline(this.def == GameDef.FO3 ? "Столичная пустошь | 2277" : "Мохаве | 2281");
            this.cover.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.MainActivity$GameCard$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    GameCard.this.lambda$build$1(view);
                }
            });
            this.status.setTypeface(Ui.mono(false));
            this.status.setTextSize(12.0f);
            this.status.setSingleLine(true);
            this.status.setEllipsize(TextUtils.TruncateAt.END);
            this.status.setPadding(Ui.dp(MainActivity.this, 4.0f), Ui.dp(MainActivity.this, 8.0f), 0, Ui.dp(MainActivity.this, 6.0f));
            linearLayoutColumn.addView(this.status);
            LinearLayout linearLayoutRow = Ui.row(MainActivity.this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, Ui.dp(MainActivity.this, 56.0f), 1.0f);
            layoutParams.setMargins(0, 0, Ui.dp(MainActivity.this, 10.0f), 0);
            this.play.setMinHeight(0);
            linearLayoutRow.addView(this.play, layoutParams);
            this.gear.setImageDrawable(Ui.icon(MainActivity.this, R.drawable.ic_settings, Ui.FG, 26.0f));
            this.gear.setScaleType(ImageView.ScaleType.CENTER);
            this.gear.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.tint(70)), Ui.rounded(Ui.tint(22), Ui.FG_DIM, 12.0f, MainActivity.this), null));
            this.gear.setContentDescription("Настройки " + this.def.title);
            this.gear.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.MainActivity$GameCard$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    GameCard.this.lambda$build$2(view);
                }
            });
            Anim.pressable(this.gear);
            int iDp = Ui.dp(MainActivity.this, 56.0f);
            linearLayoutRow.addView(this.gear, new LinearLayout.LayoutParams(iDp, iDp));
            linearLayoutColumn.addView(linearLayoutRow);
            return linearLayoutColumn;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$build$1(View view) {
            MainActivity.this.onPlay(this.def);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$build$2(View view) {
            MainActivity.this.openSettings(this.def);
        }
    }

    /** NativeGameActivity's fresh run (a save loaded in game): start the game again. */
    public static final String EXTRA_RELAUNCH_GAME = "relaunch_game";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        relaunchIfAsked(intent);
    }

    /** The saves screen's "Загрузить": play this game (FalloutEngine adds the pending --load). */
    public static final String EXTRA_PLAY_GAME = "play_game";

    private void relaunchIfAsked(Intent intent) {
        final String play = intent == null ? null : intent.getStringExtra(EXTRA_PLAY_GAME);
        if (play != null) {
            intent.removeExtra(EXTRA_PLAY_GAME);
            com.wastelanddroid.game.GameDef g = com.wastelanddroid.game.GameDef.byId(play);
            if (g != null) onPlay(g);
            return;
        }
        final String game = intent == null ? null : intent.getStringExtra(EXTRA_RELAUNCH_GAME);
        if (game == null) return;
        intent.removeExtra(EXTRA_RELAUNCH_GAME);
        this.prefs.edit().putBoolean(Prefs.LAUNCH_PENDING, true).commit();
        // The game's process ends as its activity finishes: start the next run after it.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Intent g = new Intent(this, com.wastelanddroid.engine.NativeGameActivity.class).putExtra("game", game);
            startActivity(g);
            overridePendingTransition(0, 0);
        }, 900);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.immersive(this);
        this.prefs = Prefs.get(this);
        this.appliedAccent = Ui.accent(this);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackground(Ui.screenBackground());
        WastelandBackdrop wastelandBackdrop = new WastelandBackdrop(this);
        this.backdrop = wastelandBackdrop;
        frameLayout.addView(wastelandBackdrop, new FrameLayout.LayoutParams(-1, -1));
        View view = new View(this);
        view.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.argb(200, 8, 10, 9), Color.argb(120, 8, 10, 9), Color.argb(225, 8, 10, 9)}));
        frameLayout.addView(view, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayoutColumn = Ui.column(this);
        int iDp = Ui.dp(this, 22.0f);
        linearLayoutColumn.setPadding(iDp, Ui.dp(this, 14.0f), iDp, Ui.dp(this, 16.0f));
        View viewBuildHeader = buildHeader();
        linearLayoutColumn.addView(viewBuildHeader);
        LinearLayout linearLayoutRow = Ui.row(this);
        linearLayoutRow.setGravity(48);
        GameDef[] gameDefArrValues = GameDef.values();
        int length = gameDefArrValues.length;
        boolean z = true;
        int i = 0;
        while (i < length) {
            GameDef gameDef = gameDefArrValues[i];
            GameCard gameCard = new GameCard(gameDef);
            this.cards.put(gameDef, gameCard);
            if (!z) {
                linearLayoutRow.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 20.0f), 1));
            }
            linearLayoutRow.addView(gameCard.build(), new LinearLayout.LayoutParams(0, -1, 1.0f));
            i++;
            z = false;
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams.topMargin = Ui.dp(this, 14.0f);
        linearLayoutColumn.addView(linearLayoutRow, layoutParams);
        frameLayout.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        refresh();
        Anim.enterFromTop(viewBuildHeader, 0L);
        Anim.stagger(linearLayoutRow, 140L, 140L);
        if (!this.prefs.getBoolean(ONBOARDED, false) && FalloutEngine.ready(this) && anyGameChosen()) {
            this.prefs.edit().putBoolean(ONBOARDED, true).apply();
        }
        if (!this.prefs.getBoolean(ONBOARDED, false)) {
        }
        final String strTakeUnseen = CrashLog.takeUnseen(this);
        boolean z2 = this.prefs.getBoolean(Prefs.CRASH_NOTICE, false);
        this.prefs.edit().putBoolean(Prefs.CRASH_NOTICE, false).apply();
        if (strTakeUnseen != null) {
            runOnUiThread(new Runnable() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda3
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onCreate$1(strTakeUnseen);
                }
            });
        } else if (z2) {
            runOnUiThread(new Runnable() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda4
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.offerSessionLogs();
                }
            });
        }
        final String stringExtra = strTakeUnseen == null ? getIntent().getStringExtra("autostart") : null;
        if (stringExtra != null) {
            runOnUiThread(new Runnable() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda5
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onCreate$2(stringExtra);
                }
            });
        }
        relaunchIfAsked(getIntent());
    }

    private /* synthetic */ void lambda$onCreate$0() {
        open(new Intent(this, (Class<?>) OnboardingActivity.class));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$2(String str) {
        startGame(GameDef.byId(str));
    }

    private boolean anyGameChosen() {
        for (GameDef gameDef : GameDef.values()) {
            if (gameDef.gameDir(this) != null) {
                return true;
            }
        }
        return false;
    }

    private void open(Intent intent) {
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void openSettings(GameDef gameDef) {
        Intent intent = new Intent(this, (Class<?>) SettingsActivity.class);
        if (gameDef != null) {
            intent.putExtra("game", gameDef.id);
        }
        open(intent);
    }

    private View buildHeader() {
        LinearLayout linearLayoutRow = Ui.row(this);
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.fd_logo);
        imageView.setClipToOutline(true);
        imageView.setBackground(Ui.rounded(0, 0, 12.0f, this));
        int iDp = Ui.dp(this, 44.0f);
        linearLayoutRow.addView(imageView, new LinearLayout.LayoutParams(iDp, iDp));
        LinearLayout linearLayoutColumn = Ui.column(this);
        linearLayoutColumn.setPadding(Ui.dp(this, 12.0f), 0, 0, 0);
        TextView textView = new TextView(this);
        textView.setText("FALLOUT DROID");
        textView.setTextColor(Ui.FG);
        textView.setTextSize(22.0f);
        textView.setTypeface(Ui.mono(true));
        textView.setLetterSpacing(0.08f);
        textView.setShadowLayer(Ui.dp(this, 12.0f), 0.0f, 0.0f, Ui.tint(110));
        linearLayoutColumn.addView(textView);
        this.headerTitle = textView;
        TextView textViewHint = Ui.hint(this, "BY ROMMY  |  v" + versionName());
        textViewHint.setTextColor(Ui.AMBER);
        textViewHint.setLetterSpacing(0.2f);
        textViewHint.setPadding(0, 0, 0, 0);
        linearLayoutColumn.addView(textViewHint);
        linearLayoutRow.addView(linearLayoutColumn, Ui.weight());
        TextView textView2 = new TextView(this);
        this.engineChip = textView2;
        textView2.setTypeface(Ui.mono(true));
        this.engineChip.setTextSize(12.0f);
        this.engineChip.setTextColor(Ui.AMBER);
        this.engineChip.setGravity(16);
        this.engineChip.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 8.0f), Ui.dp(this, 14.0f), Ui.dp(this, 8.0f));
        this.engineChip.setBackground(Ui.rounded(Color.argb(40, Color.red(Ui.AMBER), Color.green(Ui.AMBER), Color.blue(Ui.AMBER)), Ui.AMBER, 20.0f, this));
        this.engineChip.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildHeader$3(view);
            }
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.setMargins(0, 0, Ui.dp(this, 10.0f), 0);
        linearLayoutRow.addView(this.engineChip, layoutParams);
        LinearLayout linearLayoutRow2 = Ui.row(this);
        linearLayoutRow2.setBackground(Ui.rounded(Color.argb(45, 42, 171, 238), Color.argb(160, 42, 171, 238), 22.0f, this));
        int iDp2 = Ui.dp(this, 6.0f);
        linearLayoutRow2.setPadding(iDp2, iDp2, Ui.dp(this, 14.0f), iDp2);
        ImageView imageView2 = new ImageView(this);
        imageView2.setImageDrawable(Ui.icon(this, R.drawable.ic_telegram, -1, 16.0f));
        imageView2.setScaleType(ImageView.ScaleType.CENTER);
        imageView2.setBackground(Ui.rounded(Ui.TG, 0, 15.0f, this));
        int iDp3 = Ui.dp(this, 30.0f);
        linearLayoutRow2.addView(imageView2, new LinearLayout.LayoutParams(iDp3, iDp3));
        TextView textViewLabel = Ui.label(this, "Telegram");
        textViewLabel.setTextColor(-1);
        textViewLabel.setTypeface(Ui.mono(true));
        textViewLabel.setPadding(Ui.dp(this, 8.0f), 0, 0, 0);
        linearLayoutRow2.addView(textViewLabel);
        linearLayoutRow2.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildHeader$4(view);
            }
        });
        Anim.pressable(linearLayoutRow2);
        linearLayoutRow.addView(linearLayoutRow2);
        ImageView imageView3 = new ImageView(this);
        imageView3.setImageDrawable(Ui.icon(this, R.drawable.ic_settings, Ui.FG, 22.0f));
        imageView3.setScaleType(ImageView.ScaleType.CENTER);
        imageView3.setBackground(Ui.rounded(Ui.tint(22), Ui.FG_DIM, 21.0f, this));
        imageView3.setContentDescription("Настройки");
        imageView3.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildHeader$5(view);
            }
        });
        Anim.pressable(imageView3);
        int iDp4 = Ui.dp(this, 42.0f);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(iDp4, iDp4);
        layoutParams2.setMargins(Ui.dp(this, 10.0f), 0, 0, 0);
        linearLayoutRow.addView(imageView3, layoutParams2);
        return linearLayoutRow;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildHeader$3(View view) {
        open(new Intent(this, (Class<?>) OnboardingActivity.class));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildHeader$4(View view) {
        Links.openTelegram(this);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildHeader$5(View view) {
        openSettings(null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onPlay(GameDef gameDef) {
        GameCard gameCard = this.cards.get(gameDef);
        if (gameDef.gameDir(this) == null) {
            this.pendingGame = gameDef;
            pickGameDir();
        } else {
            if (gameCard != null) {
            }
            this.prefs.edit().putString(Prefs.LAST_GAME, gameDef.id).apply();
            startGame(gameDef);
        }
    }

    private void startGame(final GameDef gameDef) {
        if (this.launching) {
            return;
        }
        final String strGameDir = gameDef.gameDir(this);
        if (strGameDir == null) {
            Toast.makeText(this, "Сначала выберите папку игры", 0).show();
            return;
        }
        if (!FalloutEngine.ready(this)) {
            Toast.makeText(this, "Сначала завершите подготовку — это нужно один раз", 1).show();
            open(new Intent(this, (Class<?>) OnboardingActivity.class));
            return;
        }
        this.launching = true;
        Toast.makeText(this, "Подготовка " + gameDef.title + "...", 0).show();
        this.prefs.edit().putBoolean(Prefs.LAUNCH_PENDING, true).commit();
        new Thread(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.prepareNative(strGameDir, gameDef);
            }
        }, "fd-prepare").start();
    }

    /** Native build: checks the game, saves the plugin order, writes the engine command line. */
    void prepareNative(String str, GameDef gameDef) {
        final FalloutEngine.Prepared error;
        try {
            File file2 = new File(str);
            ModManager modManager = new ModManager(this, gameDef, file2);
            modManager.saveOrder(modManager.loadPlugins());
            error = FalloutEngine.prepare(this, gameDef, file2);
            if (error instanceof FalloutEngine.Prepared.Ok) {
                prepareControls(gameDef);
            }
        } catch (Throwable th) {
            CrashLog.write(this, "prepare", th);
            final FalloutEngine.Prepared err = new FalloutEngine.Prepared.Error(th.getClass().getSimpleName() + ": " + th.getMessage());
            runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    MainActivity.this.lambda$startGame$6(err);
                }
            });
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.lambda$startGame$6(error);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$startGame$6(FalloutEngine.Prepared prepared) {
        this.launching = false;
        if (prepared instanceof FalloutEngine.Prepared.Ok) {
            try {
                FalloutEngine.launch(this, (FalloutEngine.Prepared.Ok) prepared);
                return;
            } catch (Throwable th) {
                CrashLog.write(this, "launch", th);
                this.prefs.edit().putBoolean(Prefs.LAUNCH_PENDING, false).apply();
                lambda$onCreate$1(Log.getStackTraceString(th));
                return;
            }
        }
        this.prefs.edit().putBoolean(Prefs.LAUNCH_PENDING, false).apply();
        new AlertDialog.Builder(this).setTitle("Не удалось запустить").setMessage(((FalloutEngine.Prepared.Error) prepared).getMessage()).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
    }

    private void prepareControls(GameDef gameDef) {
        String string = this.prefs.getString(Prefs.OVERLAY_MODE, "auto");
        PcLayout.installIfEmpty(this);
        FalloutEngine.applyPcControls(this, string);
        FalloutEngine.setGameDebugLogs(this, this.prefs.getBoolean(Prefs.GAME_LOGS, true));
        try {
            File home = FalloutEngine.homeDir(this, gameDef.id);
            SaveBackup.backup(gameDef, home, new File(home, "save_backups"));
        } catch (Exception e) {
            CrashLog.write(this, "save-backup", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void offerSessionLogs() {
        new AlertDialog.Builder(this).setTitle("Игра завершилась раньше времени").setMessage("Автозапуск отключён. Отправьте логи последнего запуска — по ним видно причину. Логи всегда доступны в Настройки > Логи.").setPositiveButton("Отправить логи", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda1
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$offerSessionLogs$9(dialogInterface, i);
            }
        }).setNegativeButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$offerSessionLogs$9(DialogInterface dialogInterface, int i) {
        SettingsActivity.shareLastLogs(this);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: showCrash, reason: merged with bridge method [inline-methods] */
    public void lambda$onCreate$1(final String str) {
        new AlertDialog.Builder(this).setTitle("Прошлый запуск завершился ошибкой").setMessage("Автозапуск отключён до следующего успешного запуска.\n\n" + (str.length() > 1500 ? str.substring(0, 1500) + "..." : str)).setPositiveButton("Отправить отчёт", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.MainActivity$$ExternalSyntheticLambda9
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showCrash$10(str, dialogInterface, i);
            }
        }).setNegativeButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showCrash$10(String str, DialogInterface dialogInterface, int i) {
        startActivity(Intent.createChooser(new Intent("android.intent.action.SEND").setType("text/plain").putExtra("android.intent.extra.SUBJECT", "Fallout Droid: отчёт о вылете").putExtra("android.intent.extra.TEXT", str), "Отправить отчёт"));
    }

    /** After the game's activity ends: if the engine stopped with an error, say why (nvforever.exit + log). */
    private void checkLastRun() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // The game's process may still be going down: give it a moment.
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignored) {
                }
                final String why = FalloutEngine.failureSummary(MainActivity.this);
                if (why == null) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        String text = why.length() > 3000 ? why.substring(0, 3000) + "..." : why;
                        new AlertDialog.Builder(MainActivity.this).setTitle("Игра закрылась")
                                .setMessage(text)
                                .setPositiveButton("Поделиться логом", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int i) {
                                        SettingsActivity.shareLastLogs(MainActivity.this);
                                    }
                                })
                                .setNegativeButton("Закрыть", (DialogInterface.OnClickListener) null).show();
                    }
                });
            }
        }, "fd-lastrun").start();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception unused) {
            return "?";
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        String str = this.appliedAccent;
        String strAccent = Ui.accent(this);
        if (!str.equals(strAccent)) {
            this.appliedAccent = strAccent;
        }
        if (!this.launching && this.prefs.getBoolean(Prefs.LAUNCH_PENDING, false)) {
            this.prefs.edit().putBoolean(Prefs.LAUNCH_PENDING, false).putBoolean(Prefs.AUTOSTART_BLOCKED, false).apply();
            checkLastRun();
        }
        refresh();
        TextView textView = this.headerTitle;
        if (textView != null) {
            this.loops.add(Anim.flicker(textView));
        }
        WastelandBackdrop wastelandBackdrop = this.backdrop;
        if (wastelandBackdrop != null) {
            ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(wastelandBackdrop, "zoom", 1.0f, 1.08f);
            objectAnimatorOfFloat.setDuration(20000L);
            objectAnimatorOfFloat.setRepeatMode(2);
            objectAnimatorOfFloat.setRepeatCount(-1);
            objectAnimatorOfFloat.setInterpolator(new AccelerateDecelerateInterpolator());
            objectAnimatorOfFloat.start();
            this.loops.add(objectAnimatorOfFloat);
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        Iterator<Animator> it = this.loops.iterator();
        while (it.hasNext()) {
            it.next().cancel();
        }
        this.loops.clear();
        for (GameCard gameCard : this.cards.values()) {
            gameCard.pulse = null;
            gameCard.play.setScaleX(1.0f);
            gameCard.play.setScaleY(1.0f);
        }
        this.launching = true;
        refresh();
        super.onPause();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pickGameDir() {
        Toast.makeText(this, "Выберите папку " + this.pendingGame.title + " — ту, где лежат " + this.pendingGame.exe + " и Data", 1).show();
        startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), 1);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        Uri data = intent.getData();
        if (i == 1) {
            this.prefs.edit().putString(this.pendingGame.dirKey(), GameFiles.treeUriToFile(data).getPath()).apply();
            refresh();
        }
    }

    private void refresh() {
        new MainActivityTelegram().accept(this);
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            Ui.immersive(this);
        }
    }
}
