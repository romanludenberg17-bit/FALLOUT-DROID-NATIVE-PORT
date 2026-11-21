package com.wastelanddroid;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wastelanddroid.R;
import com.wastelanddroid.engine.FalloutEngine;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.game.GameFiles;
import com.wastelanddroid.util.Anim;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.ScanlineView;
import com.wastelanddroid.util.Ui;
import com.wastelanddroid.util.WastelandBackdrop;
import java.io.File;

public final class OnboardingActivity extends Activity {
    private static final int REQ_GAME_DIR = 21;
    private static final int REQ_NOTIFY = 22;
    private static final String[] TITLES = {"Добро пожаловать", "Разрешения", "Фоновая работа", "Игры"};
    private LinearLayout body;
    private String error;
    private LinearLayout footerBox;
    private int step;
    private LinearLayout steps;
    private String workStage;
    private boolean working;
    private final Handler poll = new Handler(Looper.getMainLooper());
    private int workPercent = -1;
    private GameDef pendingGame = GameDef.FO3;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.immersive(this);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackground(Ui.screenBackground());
        frameLayout.addView(new WastelandBackdrop(this), new FrameLayout.LayoutParams(-1, -1));
        View view = new View(this);
        view.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.argb(240, 8, 10, 9), Color.argb(215, 8, 10, 9), Color.argb(170, 8, 10, 9)}));
        frameLayout.addView(view, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayoutRow = Ui.row(this);
        linearLayoutRow.setGravity(48);
        int iDp = Ui.dp(this, 16.0f);
        linearLayoutRow.setPadding(iDp, Ui.dp(this, 12.0f), iDp, Ui.dp(this, 12.0f));
        LinearLayout linearLayoutColumn = Ui.column(this);
        TextView textView = new TextView(this);
        textView.setText("FALLOUT DROID");
        textView.setTextColor(Ui.FG);
        int i = 1;
        textView.setTypeface(Ui.mono(true));
        textView.setTextSize(20.0f);
        textView.setLetterSpacing(0.08f);
        linearLayoutColumn.addView(textView);
        TextView textViewHint = Ui.hint(this, "ПОДГОТОВКА К ПЕРВОМУ ЗАПУСКУ");
        textViewHint.setTextColor(Ui.AMBER);
        textViewHint.setLetterSpacing(0.15f);
        textViewHint.setPadding(0, 0, 0, Ui.dp(this, 10.0f));
        linearLayoutColumn.addView(textViewHint);
        LinearLayout linearLayoutColumn2 = Ui.column(this);
        this.steps = linearLayoutColumn2;
        linearLayoutColumn.addView(linearLayoutColumn2);
        linearLayoutRow.addView(linearLayoutColumn, new LinearLayout.LayoutParams(Ui.dp(this, 200.0f), -1));
        LinearLayout linearLayoutColumn3 = Ui.column(this);
        linearLayoutColumn3.setBackground(Ui.rounded(Ui.CARD, Ui.LINE, 12.0f, this));
        int iDp2 = Ui.dp(this, 16.0f);
        linearLayoutColumn3.setPadding(iDp2, iDp2, iDp2, Ui.dp(this, 10.0f));
        this.body = Ui.column(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(this.body);
        linearLayoutColumn3.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        LinearLayout linearLayoutRow2 = Ui.row(this);
        this.footerBox = linearLayoutRow2;
        linearLayoutRow2.setGravity(16);
        linearLayoutColumn3.addView(this.footerBox, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
        layoutParams.leftMargin = Ui.dp(this, 14.0f);
        linearLayoutRow.addView(linearLayoutColumn3, layoutParams);
        frameLayout.addView(linearLayoutRow, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frameLayout);
        this.step = bundle != null ? bundle.getInt("step", 0) : 0;
        if (bundle == null && filesGranted()) {
            this.step = TITLES.length - 1;
            while (true) {
                if (i >= TITLES.length) {
                    break;
                }
                if (!done(i)) {
                    this.step = i;
                    break;
                }
                i++;
            }
        }
        Anim.enterFromTop(linearLayoutColumn, 0L);
    }

    @Override // android.app.Activity
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt("step", this.step);
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        render(false);
        autoAdvance();
    }

    private boolean filesGranted() {
        try {
            return Environment.isExternalStorageManager();
        } catch (RuntimeException unused) {
            return false;
        }
    }

    private boolean notifyGranted() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0;
    }

    private boolean batteryFree() {
        PowerManager powerManager = (PowerManager) getSystemService(PowerManager.class);
        return powerManager == null || powerManager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean done(int i) {
        if (i == 0) {
            return this.step > 0;
        }
        if (i == 1) {
            return filesGranted();
        }
        if (i == 2) {
            return batteryFree();
        }
        return anyGameReady();
    }

    private boolean anyGameReady() {
        for (GameDef gameDef : GameDef.values()) {
            String strGameDir = gameDef.gameDir(this);
            if (strGameDir != null && GameFiles.validate(gameDef, new File(strGameDir)).ok()) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void render(boolean z) {
        int i;
        this.steps.removeAllViews();
        int i2 = 0;
        while (true) {
            String[] strArr = TITLES;
            if (i2 >= strArr.length) {
                break;
            }
            boolean z2 = done(i2) && i2 != this.step;
            boolean z3 = i2 == this.step;
            LinearLayout linearLayoutRow = Ui.row(this);
            linearLayoutRow.setPadding(0, Ui.dp(this, 3.0f), 0, Ui.dp(this, 3.0f));
            TextView textView = new TextView(this);
            textView.setGravity(17);
            textView.setTypeface(Ui.mono(true));
            textView.setTextSize(12.0f);
            if (z2) {
                textView.setBackground(oval(Ui.FG, 0));
                textView.setCompoundDrawablesRelative(Ui.icon(this, R.drawable.ic_check, Ui.ON_ACCENT, 14.0f), null, null, null);
                textView.setPadding(Ui.dp(this, 6.0f), 0, 0, 0);
            } else {
                textView.setText(String.valueOf(i2 + 1));
                textView.setTextColor(z3 ? Ui.ON_ACCENT : Ui.TEXT_DIM);
                textView.setBackground(oval(z3 ? Ui.FG : 0, z3 ? 0 : Ui.LINE));
            }
            int iDp = Ui.dp(this, 22.0f);
            linearLayoutRow.addView(textView, new LinearLayout.LayoutParams(iDp, iDp));
            TextView textViewLabel = Ui.label(this, strArr[i2]);
            if (z3) {
                i = Ui.FG;
            } else {
                i = z2 ? Ui.TEXT : Ui.TEXT_DIM;
            }
            textViewLabel.setTextColor(i);
            textViewLabel.setTypeface(Ui.mono(z3));
            textViewLabel.setPadding(Ui.dp(this, 12.0f), 0, 0, 0);
            linearLayoutRow.addView(textViewLabel);
            this.steps.addView(linearLayoutRow);
            i2++;
        }
        this.body.removeAllViews();
        this.footerBox.removeAllViews();
        int i3 = this.step;
        if (i3 == 0) {
            welcome();
        } else if (i3 == 1) {
            permissions();
        } else if (i3 == 2) {
            background();
        } else {
            games();
        }
        if (z) {
            this.body.setAlpha(0.0f);
            this.body.setTranslationX(Ui.dp(this, 20.0f));
            this.body.animate().alpha(1.0f).translationX(0.0f).setDuration(260L).setInterpolator(Anim.EMPHASIZED).start();
        }
    }

    private GradientDrawable oval(int i, int i2) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(i);
        if (i2 != 0) {
            gradientDrawable.setStroke(Ui.dp(this, 1.0f), i2);
        }
        return gradientDrawable;
    }

    private void heading(String str, String str2) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(Ui.FG);
        textView.setTypeface(Ui.mono(true));
        textView.setTextSize(24.0f);
        this.body.addView(textView);
        TextView textViewLabel = Ui.label(this, str2);
        textViewLabel.setTextColor(Ui.TEXT_DIM);
        textViewLabel.setPadding(0, Ui.dp(this, 8.0f), 0, Ui.dp(this, 14.0f));
        this.body.addView(textViewLabel);
    }

    private void status(boolean z, String str) {
        int i = z ? Ui.FG : Ui.AMBER;
        TextView textViewLabel = Ui.label(this, str);
        textViewLabel.setTextColor(i);
        textViewLabel.setPadding(0, Ui.dp(this, 6.0f), 0, Ui.dp(this, 6.0f));
        Ui.withIcon(textViewLabel, z ? R.drawable.ic_check : R.drawable.ic_warning, i);
        this.body.addView(textViewLabel);
    }

    private void footer(String str, int i, boolean z, View.OnClickListener onClickListener) {
        this.footerBox.removeAllViews();
        this.footerBox.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        if (this.step > 0) {
            Button buttonGhostButton = Ui.ghostButton(this, "Назад", new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda8
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    OnboardingActivity.this.lambda$footer$0(view);
                }
            });
            buttonGhostButton.setMinHeight(Ui.dp(this, 48.0f));
            this.footerBox.addView(buttonGhostButton, new LinearLayout.LayoutParams(-2, Ui.dp(this, 52.0f)));
        }
        this.footerBox.addView(Ui.column(this), Ui.weight());
        Button button = Ui.button(this, i, str, onClickListener);
        button.setEnabled(z);
        button.setMinHeight(Ui.dp(this, 48.0f));
        button.setMinWidth(Ui.dp(this, 160.0f));
        this.footerBox.addView(button, new LinearLayout.LayoutParams(-2, Ui.dp(this, 52.0f)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$footer$0(View view) {
        go(this.step - 1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void go(int i) {
        this.error = null;
        this.step = Math.max(0, Math.min(TITLES.length - 1, i));
        render(true);
        autoAdvance();
    }

    private void autoAdvance() {
        // Native build: nothing to download or install (the engine is inside the APK).
    }

    private void welcome() {
        heading("Пустошь ждёт", "Fallout 3 и New Vegas с ПК — прямо на телефоне. Подготовка займёт несколько минут и нужна только один раз: выдадим разрешения, установим Linux-среду с Proton и найдём ваши игры.");
        status(true, "Нужно: около 4 ГБ свободного места и Wi-Fi");
        status(true, "Файлы игр — ваша копия с ПК (папка с exe и Data)");
        footer("Начать", R.drawable.ic_play, true, new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.lambda$welcome$1(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$welcome$1(View view) {
        go(1);
    }

    private void permissions() {
        heading("Разрешения", "Игра читает файлы прямо из папки на телефоне, а уведомление держит её в работе, пока вы в меню Android.");
        boolean zFilesGranted = filesGranted();
        status(zFilesGranted, zFilesGranted ? "Доступ ко всем файлам выдан" : "Нужен доступ ко всем файлам");
        if (!zFilesGranted) {
            this.body.addView(Ui.button(this, R.drawable.ic_folder, "Выдать доступ к файлам", new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda18
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    OnboardingActivity.this.lambda$permissions$2(view);
                }
            }));
        }
        boolean zNotifyGranted = notifyGranted();
        status(zNotifyGranted, zNotifyGranted ? "Уведомления разрешены" : "Разрешите уведомления");
        if (!zNotifyGranted) {
            this.body.addView(Ui.ghostButton(this, R.drawable.ic_info, "Разрешить уведомления", new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda19
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    OnboardingActivity.this.lambda$permissions$3(view);
                }
            }));
        }
        footer(zFilesGranted ? "Далее" : "Сначала выдайте доступ", R.drawable.ic_play, zFilesGranted, new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda20
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.lambda$permissions$4(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$permissions$2(View view) {
        try {
            startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", Uri.parse("package:" + getPackageName())));
        } catch (Exception unused) {
            startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$permissions$3(View view) {
        requestNotify();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$permissions$4(View view) {
        go(2);
    }

    private void requestNotify() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 22);
                return;
            } catch (Exception unused) {
            }
        }
        try {
            startActivity(new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", getPackageName()));
        } catch (Exception unused2) {
        }
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == 22) {
        }
        render(false);
    }

    private void requestNotifyViaSettings() {
        try {
            startActivity(new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", getPackageName()));
        } catch (Exception unused) {
        }
    }

    private void background() {
        heading("Фоновая работа", "Android может выгрузить игру из памяти, когда она свёрнута. Снимите ограничение батареи, чтобы игра не закрывалась.");
        boolean zBatteryFree = batteryFree();
        status(zBatteryFree, zBatteryFree ? "Оптимизация батареи не мешает" : "Рекомендуется: разрешить работу без ограничений батареи");
        if (!zBatteryFree) {
            this.body.addView(Ui.ghostButton(this, R.drawable.ic_lock, "Снять ограничение батареи", new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    OnboardingActivity.this.lambda$background$6(view);
                }
            }));
        }
        footer(zBatteryFree ? "Далее" : "Пропустить", R.drawable.ic_play, true, new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.lambda$background$7(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$background$6(View view) {
        try {
            try {
                startActivity(new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + getPackageName())));
            } catch (Exception unused) {
            }
        } catch (Exception unused2) {
            startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$background$7(View view) {
        go(3);
    }

    private ProgressBar progress(int i) {
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(i < 0);
        progressBar.setMax(100);
        if (i >= 0) {
            progressBar.setProgress(i);
        }
        progressBar.setProgressTintList(ColorStateList.valueOf(Ui.FG));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Ui.FG));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
        progressBar.setLayoutParams(Ui.matchWrap(this, 10));
        return progressBar;
    }

    private void errorBox() {
        String str = this.error;
        if (str == null) {
            return;
        }
        TextView textViewLabel = Ui.label(this, str);
        textViewLabel.setTextColor(Ui.RED);
        textViewLabel.setPadding(0, Ui.dp(this, 8.0f), 0, Ui.dp(this, 8.0f));
        Ui.withIcon(textViewLabel, R.drawable.ic_cross, Ui.RED);
        this.body.addView(textViewLabel);
    }

    private void games() {
        String str;
        String str2;
        int i;
        heading("Ваши игры", "Укажите папку каждой игры — ту, где лежат exe-файл и папка Data. Можно указать одну игру, вторую добавите позже.");
        GameDef[] gameDefArrValues = GameDef.values();
        int length = gameDefArrValues.length;
        int i2 = 0;
        while (true) {
            str = "Готово";
            if (i2 >= length) {
                break;
            }
            final GameDef gameDef = gameDefArrValues[i2];
            String strGameDir = gameDef.gameDir(this);
            GameFiles.Report reportValidate = strGameDir == null ? null : GameFiles.validate(gameDef, new File(strGameDir));
            LinearLayout linearLayoutRow = Ui.row(this);
            linearLayoutRow.setBackground(Ui.rounded(Ui.tint(14), Ui.LINE, 12.0f, this));
            linearLayoutRow.setPadding(Ui.dp(this, 14.0f), Ui.dp(this, 10.0f), Ui.dp(this, 10.0f), Ui.dp(this, 10.0f));
            LinearLayout linearLayoutColumn = Ui.column(this);
            TextView textViewLabel = Ui.label(this, gameDef.title);
            textViewLabel.setTypeface(Ui.mono(true));
            linearLayoutColumn.addView(textViewLabel);
            if (strGameDir == null) {
                str2 = "Папка не выбрана";
            } else if (reportValidate.ok()) {
                str2 = "Готово" + (reportValidate.dlcs.isEmpty() ? "" : " | DLC: " + reportValidate.dlcs.size());
            } else {
                str2 = reportValidate.errors.get(0);
            }
            TextView textViewHint = Ui.hint(this, str2);
            if (strGameDir == null) {
                i = Ui.TEXT_DIM;
            } else {
                i = reportValidate.ok() ? Ui.FG : Ui.RED;
            }
            textViewHint.setTextColor(i);
            linearLayoutColumn.addView(textViewHint);
            linearLayoutRow.addView(linearLayoutColumn, Ui.weight());
            linearLayoutRow.addView(Ui.ghostButton(this, R.drawable.ic_folder, strGameDir == null ? "Выбрать" : "Сменить", new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda5
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    OnboardingActivity.this.lambda$games$14(gameDef, view);
                }
            }));
            this.body.addView(linearLayoutRow, Ui.matchWrap(this, 8));
            i2++;
        }
        if (anyGameReady() && FalloutEngine.ready(this)) {
            str = "В пустошь!";
        }
        footer(str, R.drawable.ic_play, true, new View.OnClickListener() { // from class: com.wastelanddroid.OnboardingActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OnboardingActivity.this.lambda$games$15(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$14(GameDef gameDef, View view) {
        this.pendingGame = gameDef;
        startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), 21);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$15(View view) {
        finishOnboarding();
    }

    private void finishOnboarding() {
        Prefs.get(this).edit().putBoolean(MainActivity.ONBOARDED, true).apply();
        finish();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == 21 && i2 == -1 && intent != null && intent.getData() != null) {
            Prefs.get(this).edit().putString(this.pendingGame.dirKey(), GameFiles.treeUriToFile(intent.getData()).getPath()).apply();
        }
        render(false);
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        int i = this.step;
        if (i > 0 && !this.working) {
            go(i - 1);
        } else if (Prefs.get(this).getBoolean(MainActivity.ONBOARDED, false) || FalloutEngine.ready(this)) {
            finishOnboarding();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override // android.app.Activity
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            Ui.immersive(this);
        }
    }
}
