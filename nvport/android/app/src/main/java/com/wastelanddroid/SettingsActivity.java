package com.wastelanddroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.wastelanddroid.R;
import com.wastelanddroid.controls.ControlsEditorActivity;
import com.wastelanddroid.engine.FalloutEngine;
import com.wastelanddroid.engine.FalloutInput;
import com.wastelanddroid.game.DeviceProfile;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.game.GameFiles;
import com.wastelanddroid.game.GraphicsActivity;
import com.wastelanddroid.mods.ModManagerActivity;
import com.wastelanddroid.saves.SavesActivity;
import com.wastelanddroid.util.Anim;
import com.wastelanddroid.util.CrashLog;
import com.wastelanddroid.util.Files2;
import com.wastelanddroid.util.Links;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.ScanlineView;
import com.wastelanddroid.util.Ui;
import com.wastelanddroid.util.WastelandBackdrop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

public final class SettingsActivity extends Activity {
    public static final String EXTRA_GAME = "game";
    public static final String EXTRA_SECTION = "section";
    private static final int REQ_COVER = 12;
    private static final int REQ_GAME_DIR = 11;
    private static final int REQ_SPLASH = 13;
    private LinearLayout content;
    private ScrollView contentScroll;
    private GameDef game = GameDef.FO3;
    private SharedPreferences prefs;
    private LinearLayout rail;
    private int section;
    private static final String[] SECTIONS = {"Игры", "Графика", "Управление", "Запуск", "Движок", "Оформление", "Логи", "О приложении"};
    private static final int[] ICONS = {R.drawable.ic_play, R.drawable.ic_display, R.drawable.ic_touch, R.drawable.ic_lock, R.drawable.ic_settings, R.drawable.ic_image, R.drawable.ic_upload, R.drawable.ic_info};

    public static File coverFile(Context context, GameDef gameDef) {
        return new File(new File(context.getFilesDir(), "covers"), gameDef.id);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        super.onCreate(bundle);
        Ui.immersive(this);
        this.prefs = Prefs.get(this);
        String stringExtra = getIntent().getStringExtra("game");
        if (stringExtra != null) {
            this.game = GameDef.byId(stringExtra);
        }
        this.section = bundle != null ? bundle.getInt(EXTRA_SECTION, 0) : getIntent().getIntExtra(EXTRA_SECTION, 0);
        if (bundle != null && bundle.getString("game") != null) {
            this.game = GameDef.byId(bundle.getString("game"));
        }
        build();
            } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }

    @Override // android.app.Activity
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(EXTRA_SECTION, this.section);
        bundle.putString("game", this.game.id);
    }

    private void build() throws IOException {
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackground(Ui.screenBackground());
        LinearLayout linearLayoutRow = Ui.row(this);
        linearLayoutRow.setGravity(48);
        int iDp = Ui.dp(this, 20.0f);
        linearLayoutRow.setPadding(iDp, Ui.dp(this, 14.0f), iDp, Ui.dp(this, 14.0f));
        LinearLayout linearLayoutColumn = Ui.column(this);
        LinearLayout linearLayoutRow2 = Ui.row(this);
        ImageView imageView = new ImageView(this);
        imageView.setImageDrawable(Ui.icon(this, R.drawable.ic_arrow_up, Ui.FG, 20.0f));
        imageView.setRotation(-90.0f);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setBackground(Ui.rounded(Ui.tint(22), Ui.FG_DIM, 19.0f, this));
        imageView.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$build$0(view);
            }
        });
        imageView.setContentDescription("Назад");
        Anim.pressable(imageView);
        int iDp2 = Ui.dp(this, 38.0f);
        linearLayoutRow2.addView(imageView, new LinearLayout.LayoutParams(iDp2, iDp2));
        TextView textView = new TextView(this);
        textView.setText("НАСТРОЙКИ");
        textView.setTextColor(Ui.FG);
        textView.setTypeface(Ui.mono(true));
        textView.setTextSize(19.0f);
        textView.setLetterSpacing(0.12f);
        textView.setPadding(Ui.dp(this, 12.0f), 0, 0, 0);
        linearLayoutRow2.addView(textView);
        linearLayoutColumn.addView(linearLayoutRow2);
        this.rail = Ui.column(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(this.rail);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams.topMargin = Ui.dp(this, 12.0f);
        linearLayoutColumn.addView(scrollView, layoutParams);
        linearLayoutRow.addView(linearLayoutColumn, new LinearLayout.LayoutParams(Ui.dp(this, 210.0f), -1));
        View view = new View(this);
        view.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(Ui.dp(this, 1.0f), -1);
        layoutParams2.setMargins(Ui.dp(this, 14.0f), 0, Ui.dp(this, 18.0f), 0);
        linearLayoutRow.addView(view, layoutParams2);
        this.content = Ui.column(this);
        ScrollView scrollView2 = new ScrollView(this);
        this.contentScroll = scrollView2;
        scrollView2.setVerticalScrollBarEnabled(false);
        this.contentScroll.setOverScrollMode(2);
        this.contentScroll.addView(this.content);
        linearLayoutRow.addView(this.contentScroll, new LinearLayout.LayoutParams(0, -1, 1.0f));
        frameLayout.addView(linearLayoutRow, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frameLayout);
        renderRail();
        renderSection(false);
        Anim.enterFromTop(linearLayoutRow2, 0L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$0(View view) {
        finish();
    }

    private void renderRail() {
        Drawable rippleDrawable;
        this.rail.removeAllViews();
        String[] strArr = SECTIONS;
        for (int i0 = 0; i0 < strArr.length; i0++) {
            final int i = i0;
            boolean z = i == this.section;
            TextView textView = new TextView(this);
            textView.setText(strArr[i]);
            textView.setTypeface(Ui.mono(z));
            textView.setTextSize(14.0f);
            textView.setTextColor(z ? Ui.ON_ACCENT : Ui.TEXT);
            textView.setGravity(16);
            textView.setPadding(Ui.dp(this, 12.0f), 0, Ui.dp(this, 10.0f), 0);
            if (z) {
                rippleDrawable = Ui.rounded(Ui.FG, 0, 10.0f, this);
            } else {
                rippleDrawable = new RippleDrawable(ColorStateList.valueOf(Ui.tint(60)), null, Ui.rounded(-1, 0, 10.0f, this));
            }
            textView.setBackground(rippleDrawable);
            textView.setCompoundDrawablesRelative(Ui.icon(this, ICONS[i], z ? Ui.ON_ACCENT : Ui.FG_DIM, 18.0f), null, null, null);
            textView.setCompoundDrawablePadding(Ui.dp(this, 10.0f));
            textView.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
        try {
                    SettingsActivity.this.lambda$renderRail$1(i, view);
                        } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 42.0f));
            layoutParams.bottomMargin = Ui.dp(this, 4.0f);
            this.rail.addView(textView, layoutParams);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$renderRail$1(int i, View view) throws IOException {
        if (this.section != i) {
            this.section = i;
            renderRail();
            renderSection(true);
        }
    }

    private void renderSection(boolean z) throws IOException {
        this.content.removeAllViews();
        TextView textView = new TextView(this);
        textView.setText(SECTIONS[this.section].toUpperCase());
        textView.setTextColor(Ui.AMBER);
        textView.setTypeface(Ui.mono(true));
        textView.setTextSize(13.0f);
        textView.setLetterSpacing(0.22f);
        textView.setPadding(Ui.dp(this, 2.0f), Ui.dp(this, 8.0f), 0, Ui.dp(this, 10.0f));
        this.content.addView(textView);
        switch (this.section) {
            case 0:
                games();
                break;
            case 1:
                graphics();
                break;
            case 2:
                controls();
                break;
            case 3:
                launch();
                break;
            case 4:
                engine();
                break;
            case 5:
                look();
                break;
            case 6:
                logs();
                break;
            default:
                about();
                break;
        }
        this.contentScroll.scrollTo(0, 0);
        if (z) {
            this.content.setAlpha(0.0f);
            this.content.setTranslationX(Ui.dp(this, 16.0f));
            this.content.animate().alpha(1.0f).translationX(0.0f).setDuration(220L).setInterpolator(Anim.EMPHASIZED).start();
        }
    }

    private LinearLayout group(String str) {
        if (str != null) {
            TextView textViewHint = Ui.hint(this, str.toUpperCase());
            textViewHint.setLetterSpacing(0.15f);
            textViewHint.setPadding(Ui.dp(this, 4.0f), Ui.dp(this, 10.0f), 0, Ui.dp(this, 6.0f));
            this.content.addView(textViewHint);
        }
        LinearLayout linearLayoutColumn = Ui.column(this);
        linearLayoutColumn.setBackground(Ui.rounded(Ui.CARD, Ui.LINE, 14.0f, this));
        linearLayoutColumn.setPadding(Ui.dp(this, 16.0f), Ui.dp(this, 6.0f), Ui.dp(this, 16.0f), Ui.dp(this, 6.0f));
        this.content.addView(linearLayoutColumn, new LinearLayout.LayoutParams(-1, -2));
        return linearLayoutColumn;
    }

    private void separator(LinearLayout linearLayout) {
        if (linearLayout.getChildCount() == 0) {
            return;
        }
        View view = new View(this);
        view.setBackgroundColor(Ui.LINE);
        linearLayout.addView(view, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1.0f)));
    }

    private LinearLayout textBlock(String str, String str2) {
        LinearLayout linearLayoutColumn = Ui.column(this);
        TextView textViewLabel = Ui.label(this, str);
        textViewLabel.setTextSize(14.0f);
        textViewLabel.setPadding(0, 0, 0, 0);
        linearLayoutColumn.addView(textViewLabel);
        if (str2 != null) {
            TextView textViewHint = Ui.hint(this, str2);
            textViewHint.setPadding(0, Ui.dp(this, 2.0f), 0, 0);
            linearLayoutColumn.addView(textViewHint);
        }
        return linearLayoutColumn;
    }

    private Switch toggle(LinearLayout linearLayout, String str, String str2, final String str3, boolean z, final Ui.IntCallback intCallback) {
        separator(linearLayout);
        LinearLayout linearLayoutRow = Ui.row(this);
        linearLayoutRow.setPadding(0, Ui.dp(this, 10.0f), 0, Ui.dp(this, 10.0f));
        linearLayoutRow.addView(textBlock(str, str2), Ui.weight());
        final Switch r6 = new Switch(this);
        r6.setChecked(this.prefs.getBoolean(str3, z));
        int[][] iArr = {new int[]{android.R.attr.state_checked}, new int[0]};
        r6.setThumbTintList(new ColorStateList(iArr, new int[]{Ui.FG, Ui.TEXT_DIM}));
        r6.setTrackTintList(new ColorStateList(iArr, new int[]{Ui.tint(120), Ui.LINE}));
        r6.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda18
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z2) {
                SettingsActivity.this.lambda$toggle$2(str3, intCallback, compoundButton, z2);
            }
        });
        linearLayoutRow.addView(r6);
        linearLayoutRow.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda19
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                r6.toggle();
            }
        });
        linearLayout.addView(linearLayoutRow);
        return r6;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$toggle$2(String str, Ui.IntCallback intCallback, CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean(str, z).apply();
        if (intCallback != null) {
            intCallback.on(z ? 1 : 0);
        }
    }

    private View action(LinearLayout linearLayout, int i, String str, String str2, View.OnClickListener onClickListener) {
        separator(linearLayout);
        LinearLayout linearLayoutRow = Ui.row(this);
        linearLayoutRow.setPadding(0, Ui.dp(this, 10.0f), 0, Ui.dp(this, 10.0f));
        ImageView imageView = new ImageView(this);
        imageView.setImageDrawable(Ui.icon(this, i, Ui.FG, 20.0f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.dp(this, 22.0f), Ui.dp(this, 22.0f));
        layoutParams.rightMargin = Ui.dp(this, 14.0f);
        linearLayoutRow.addView(imageView, layoutParams);
        linearLayoutRow.addView(textBlock(str, str2), Ui.weight());
        ImageView imageView2 = new ImageView(this);
        imageView2.setImageDrawable(Ui.icon(this, R.drawable.ic_arrow_up, Ui.FG_DIM, 16.0f));
        imageView2.setRotation(90.0f);
        linearLayoutRow.addView(imageView2);
        linearLayoutRow.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.tint(50)), null, Ui.rounded(-1, 0, 8.0f, this)));
        linearLayoutRow.setOnClickListener(onClickListener);
        linearLayout.addView(linearLayoutRow);
        return linearLayoutRow;
    }

    private void info(LinearLayout linearLayout, int i, String str, int i2) {
        separator(linearLayout);
        TextView textViewLabel = Ui.label(this, str);
        textViewLabel.setTextColor(i2);
        textViewLabel.setTextSize(13.0f);
        textViewLabel.setPadding(0, Ui.dp(this, 10.0f), 0, Ui.dp(this, 10.0f));
        Ui.withIcon(textViewLabel, i, i2);
        linearLayout.addView(textViewLabel);
    }

    private void choice(LinearLayout linearLayout, String str, String[] strArr, final String[] strArr2, final String str2, String str3) {
        separator(linearLayout);
        LinearLayout linearLayoutRow = Ui.row(this);
        linearLayoutRow.setPadding(0, Ui.dp(this, 8.0f), 0, Ui.dp(this, 8.0f));
        linearLayoutRow.addView(textBlock(str, null), Ui.weight());
        Spinner spinner = new Spinner(this, 1);
        spinner.setAdapter((SpinnerAdapter) new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, strArr) { // from class: com.wastelanddroid.SettingsActivity.1
            @Override // android.widget.ArrayAdapter, android.widget.Adapter
            public View getView(int i, View view, ViewGroup viewGroup) {
                return style(super.getView(i, view, viewGroup));
            }

            @Override // android.widget.ArrayAdapter, android.widget.BaseAdapter, android.widget.SpinnerAdapter
            public View getDropDownView(int i, View view, ViewGroup viewGroup) {
                View viewStyle = style(super.getDropDownView(i, view, viewGroup));
                viewStyle.setBackgroundColor(Ui.BG2);
                return viewStyle;
            }

            private View style(View view) {
                TextView textView = (TextView) view;
                textView.setTextColor(Ui.FG);
                textView.setTypeface(Ui.mono(true));
                textView.setTextSize(13.0f);
                return textView;
            }
        });
        spinner.setPopupBackgroundDrawable(Ui.rounded(Ui.BG2, Ui.LINE, 10.0f, this));
        spinner.setBackground(Ui.rounded(Ui.tint(22), Ui.FG_DIM, 10.0f, this));
        spinner.setSelection(Math.max(0, Arrays.asList(strArr2).indexOf(this.prefs.getString(str2, str3))));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { // from class: com.wastelanddroid.SettingsActivity.2
            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onNothingSelected(AdapterView<?> adapterView) {
            }

            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
                SettingsActivity.this.prefs.edit().putString(str2, strArr2[i]).apply();
            }
        });
        linearLayoutRow.addView(spinner, new LinearLayout.LayoutParams(Ui.dp(this, 280.0f), Ui.dp(this, 42.0f)));
        linearLayout.addView(linearLayoutRow);
    }

    private void open(Intent intent) {
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void games() throws IOException {
        LinearLayout linearLayoutRow = Ui.row(this);
        GameDef[] gameDefArrValues = GameDef.values();
        int length = gameDefArrValues.length;
        for (int i = 0; i < length; i++) {
            final GameDef gameDef = gameDefArrValues[i];
            boolean z = gameDef == this.game;
            TextView textView = new TextView(this);
            textView.setText(gameDef.title);
            textView.setTypeface(Ui.mono(true));
            textView.setTextSize(13.0f);
            textView.setGravity(17);
            textView.setTextColor(z ? Ui.ON_ACCENT : Ui.FG);
            textView.setBackground(z ? Ui.rounded(Ui.FG, 0, 20.0f, this) : Ui.rounded(Ui.tint(18), Ui.FG_DIM, 20.0f, this));
            textView.setPadding(Ui.dp(this, 18.0f), 0, Ui.dp(this, 18.0f), 0);
            textView.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
        try {
                    SettingsActivity.this.lambda$games$4(gameDef, view);
                        } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, Ui.dp(this, 38.0f));
            layoutParams.rightMargin = Ui.dp(this, 8.0f);
            linearLayoutRow.addView(textView, layoutParams);
        }
        this.content.addView(linearLayoutRow);
        String strGameDir = this.game.gameDir(this);
        LinearLayout linearLayoutGroup = group("Файлы игры");
        if (strGameDir == null) {
            info(linearLayoutGroup, R.drawable.ic_info, "Папка не выбрана. Укажите папку, где лежат " + this.game.exe + " и Data.", Ui.TEXT_DIM);
        } else {
            GameFiles.Report reportValidate = GameFiles.validate(this.game, new File(strGameDir));
            info(linearLayoutGroup, R.drawable.ic_folder, strGameDir, Ui.TEXT);
            if (reportValidate.ok()) {
                info(linearLayoutGroup, R.drawable.ic_check, "Файлы в порядке" + (reportValidate.dlcs.isEmpty() ? "" : " | DLC: " + String.join(", ", reportValidate.dlcs)), Ui.FG);
            }
            Iterator<String> it = reportValidate.errors.iterator();
            while (it.hasNext()) {
                info(linearLayoutGroup, R.drawable.ic_cross, it.next(), Ui.RED);
            }
            Iterator<String> it2 = reportValidate.warnings.iterator();
            while (it2.hasNext()) {
                info(linearLayoutGroup, R.drawable.ic_warning, it2.next(), Ui.AMBER);
            }
        }
        action(linearLayoutGroup, R.drawable.ic_folder, strGameDir == null ? "Выбрать папку игры" : "Сменить папку игры", null, new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$games$5(view);
            }
        });
        LinearLayout linearLayoutGroup2 = group("Контент");
        action(linearLayoutGroup2, R.drawable.ic_mods, "Менеджер модов", "Порядок загрузки плагинов, включение и выключение", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$games$6(view);
            }
        });
        action(linearLayoutGroup2, R.drawable.ic_save, "Сохранения", "Импорт с ПК, экспорт и резервные копии", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$games$7(view);
            }
        });
        LinearLayout linearLayoutGroup3 = group("Обложка на главном экране");
        action(linearLayoutGroup3, R.drawable.ic_image, "Выбрать свою обложку", "Любая картинка из галереи", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$games$8(view);
            }
        });
        if (coverFile(this, this.game).exists()) {
            action(linearLayoutGroup3, R.drawable.ic_restore, "Вернуть стандартную обложку", null, new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda7
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
        try {
                    SettingsActivity.this.lambda$games$9(view);
                        } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
            });
        }
        action(group("Если игра не запускается"), R.drawable.ic_restore, "Пересоздать префикс Wine", "Сохранения сначала копируются в резервную папку", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$games$10(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$4(GameDef gameDef, View view) throws IOException {
        this.game = gameDef;
        renderSection(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$5(View view) {
        startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), 11);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$6(View view) {
        if (this.game.gameDir(this) == null) {
            Toast.makeText(this, "Сначала выберите папку игры", 0).show();
        } else {
            open(new Intent(this, (Class<?>) ModManagerActivity.class).putExtra("game", this.game.id));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$7(View view) {
        open(new Intent(this, (Class<?>) SavesActivity.class).putExtra("game", this.game.id));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$8(View view) {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("image/*");
        startActivityForResult(intent, 12);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$9(View view) throws IOException {
        coverFile(this, this.game).delete();
        renderSection(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$games$10(View view) {
        resetPrefix();
    }

    private void graphics() {
        LinearLayout linearLayoutGroup = group(null);
        choice(linearLayoutGroup, "Качество", new String[]{"Авто — " + qualityName(DeviceProfile.autoQuality(this)), "Низкое | 720p", "Среднее | 900p", "Высокое | 1080p"}, new String[]{"auto", "low", "medium", "high"}, Prefs.QUALITY, "auto");
        separator(linearLayoutGroup);
        View viewSlider = Ui.slider(this, "Масштаб разрешения, %", 50, 100, this.prefs.getInt(Prefs.RES_SCALE, 100), new Ui.IntCallback() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda9
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                SettingsActivity.this.lambda$graphics$11(i);
            }
        });
        viewSlider.setPadding(0, Ui.dp(this, 8.0f), 0, Ui.dp(this, 8.0f));
        linearLayoutGroup.addView(viewSlider);
        toggle(linearLayoutGroup, "На весь экран", "Растягивать картинку под экран телефона, без чёрных полос по бокам", "stretch_screen", true, null);
        toggle(linearLayoutGroup, "Счётчик FPS", "Показывает частоту кадров поверх игры", Prefs.SHOW_HUD, false, null);
        info(linearLayoutGroup, R.drawable.ic_lock, "Ограничение 60 FPS включено всегда: выше 60 ломается физика движка.", Ui.TEXT_DIM);
        action(group(null), R.drawable.ic_display, "Тонкая настройка графики", "Тени, дальность прорисовки, трава, вода", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$graphics$12(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$graphics$11(int i) {
        this.prefs.edit().putInt(Prefs.RES_SCALE, i).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$graphics$12(View view) {
        open(new Intent(this, (Class<?>) GraphicsActivity.class));
    }

    private void controls() {
        LinearLayout linearLayoutGroup = group(null);
        choice(linearLayoutGroup, "Экранные кнопки", new String[]{"Авто — скрывать при геймпаде", "Всегда показывать", "Выключены"}, new String[]{"auto", "on", "off"}, Prefs.OVERLAY_MODE, "auto");
        action(linearLayoutGroup, R.drawable.ic_touch, "Редактор экранных кнопок", "Размер, прозрачность, форма, расположение и действия", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda26
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$controls$13(view);
            }
        });
        toggle(linearLayoutGroup, "Удерживать мышь в игре", "Нужно играм, которые прячут курсор и читают мышь напрямую. Выключайте, только если мышь ведёт себя странно", FalloutInput.GRAB_CURSOR, true, null);
        info(linearLayoutGroup, R.drawable.ic_gamepad, "Геймпад по Bluetooth или USB игра видит как Xbox 360 — он работает сразу.", Ui.TEXT_DIM);
        info(linearLayoutGroup, R.drawable.ic_info, "Обзор мышью — свайп по правой половине экрана. Чувствительность настраивается в редакторе.", Ui.TEXT_DIM);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$controls$13(View view) {
        open(new Intent(this, (Class<?>) ControlsEditorActivity.class));
    }

    private void launch() {
        LinearLayout linearLayoutGroup = group("Запуск");
        toggle(linearLayoutGroup, "Сразу запускать последнюю игру", "После заставки, если всё готово", "autostart", false, null);
        toggle(linearLayoutGroup, "Пропуск вступительных роликов", null, Prefs.SKIP_INTRO, true, null);
        toggle(linearLayoutGroup, "Режим геймпада в игре", "Включайте только с физическим геймпадом: в этом режиме Fallout прячет курсор и не слушает мышь", Prefs.GAMEPAD, false, null);
        LinearLayout linearLayoutGroup2 = group("Стабильность");
        toggle(linearLayoutGroup2, "Автосохранения", "При переходах, ожидании и отдыхе", Prefs.AUTOSAVE, true, null);
        toggle(linearLayoutGroup2, "Только производительные ядра CPU", null, Prefs.PIN_BIG_CORES, true, null);
    }

    private void engine() {
        LinearLayout linearLayoutGroup = group("Нативный движок");
        info(linearLayoutGroup, R.drawable.ic_check, "Fallout Droid Engine (Rust, Vulkan/GLES) — встроен в приложение, ничего скачивать не нужно", Ui.FG);
        info(linearLayoutGroup, R.drawable.ic_info, "Без Proton, Wine и эмуляции x86: игра читает ESM/BSA напрямую", Ui.TEXT_DIM);
        info(linearLayoutGroup, R.drawable.ic_warning, "Сохранения движка (.nvfsave) несовместимы с .fos оригинальной игры", Ui.AMBER);
        action(linearLayoutGroup, R.drawable.ic_download, "Мастер подготовки", "Разрешения, батарея, папки игр", new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.lambda$engine$15(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$engine$15(View view) {
        open(new Intent(this, (Class<?>) OnboardingActivity.class));
    }

    private void look() {
        LinearLayout linearLayoutGroup = group("Акцентный цвет");
        LinearLayout linearLayoutRow = Ui.row(this);
        linearLayoutRow.setPadding(0, Ui.dp(this, 12.0f), 0, Ui.dp(this, 12.0f));
        String strAccent = Ui.accent(this);
        for (int i = 0; i < Ui.ACCENTS.length; i++) {
            final String str = Ui.ACCENTS[i];
            boolean zEquals = str.equals(strAccent);
            LinearLayout linearLayoutColumn = Ui.column(this);
            linearLayoutColumn.setGravity(17);
            View view = new View(this);
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setShape(1);
            gradientDrawable.setColor(Ui.ACCENT_COLORS[i]);
            gradientDrawable.setStroke(Ui.dp(this, zEquals ? 3.0f : 1.0f), zEquals ? -1 : Ui.LINE);
            view.setBackground(gradientDrawable);
            int iDp = Ui.dp(this, 44.0f);
            linearLayoutColumn.addView(view, new LinearLayout.LayoutParams(iDp, iDp));
            TextView textViewLabel = Ui.label(this, Ui.ACCENT_NAMES[i]);
            textViewLabel.setTextColor(zEquals ? Ui.ACCENT_COLORS[i] : Ui.TEXT_DIM);
            textViewLabel.setGravity(17);
            linearLayoutColumn.addView(textViewLabel);
            linearLayoutColumn.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    SettingsActivity.this.lambda$look$17(str, view2);
                }
            });
            Anim.pressable(linearLayoutColumn);
            linearLayoutRow.addView(linearLayoutColumn, Ui.weight());
        }
        linearLayoutGroup.addView(linearLayoutRow);
        info(linearLayoutGroup, R.drawable.ic_info, "Меняет цвет всего интерфейса и экранных кнопок.", Ui.TEXT_DIM);
        LinearLayout linearLayoutGroup2 = group("Заставка");
        action(linearLayoutGroup2, R.drawable.ic_image, "Свой фон заставки и меню", null, new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                SettingsActivity.this.lambda$look$18(view2);
            }
        });
        action(linearLayoutGroup2, R.drawable.ic_restore, "Стандартный фон", null, new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda20
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                SettingsActivity.this.lambda$look$19(view2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$look$17(String str, View view) {
        setAccent(str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$look$18(View view) {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("image/*");
        startActivityForResult(intent, 13);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$look$19(View view) {
        WastelandBackdrop.customFile(this).delete();
        Toast.makeText(this, "Стандартный фон заставки", 0).show();
    }

    private void setAccent(String str) {
        this.prefs.edit().putString(Ui.ACCENT_KEY, str).apply();
        Ui.applyAccent(this);
        try {
            com.wastelanddroid.input.ScreenKeyStore.setTint(this, Ui.FG);
        } catch (Throwable unused) {
        }
        recreate();
    }

    private void logs() {
        LinearLayout linearLayoutGroup = group(null);
        toggle(linearLayoutGroup, "Подробные логи игры", "Proton, Wine и DXVK пишут подробности — нужно для отчётов об ошибках", Prefs.GAME_LOGS, true, new Ui.IntCallback() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda21
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                SettingsActivity.this.lambda$logs$20(i);
            }
        });
        toggle(linearLayoutGroup, "Отладка ввода", "Строка поверх игры: доходят ли касания и что уходит игре", FalloutInput.DEBUG, false, null);
        action(linearLayoutGroup, R.drawable.ic_upload, "Отправить логи последнего запуска", "Архив с логами — в Telegram или куда удобно", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda22
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$logs$21(view);
            }
        });
        action(linearLayoutGroup, R.drawable.ic_warning, "Отчёт о вылетах приложения", null, new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda23
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$logs$22(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$logs$20(int i) {
        FalloutEngine.setGameDebugLogs(this, i == 1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$logs$21(View view) {
        shareLastLogs(this);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$logs$22(View view) {
        String strTakeUnseen = CrashLog.takeUnseen(this);
        if (strTakeUnseen == null) {
            Toast.makeText(this, "Новых вылетов нет", 0).show();
        } else {
            startActivity(Intent.createChooser(new Intent("android.intent.action.SEND").setType("text/plain").putExtra("android.intent.extra.SUBJECT", "Fallout Droid: отчёт о вылете").putExtra("android.intent.extra.TEXT", strTakeUnseen), "Отправить отчёт"));
        }
    }

    public static void shareLastLogs(final Activity activity) {
        Toast.makeText(activity, "Собираю логи...", 0).show();
        new Thread(new Runnable() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda25
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.lambda$shareLastLogs$24(activity);
            }
        }, "fd-logs").start();
    }

    static /* synthetic */ void lambda$shareLastLogs$24(final Activity activity) {
        final File fileLastSessionLogs = FalloutEngine.lastSessionLogs(activity);
        activity.runOnUiThread(new Runnable() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda27
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.lambda$shareLastLogs$23(fileLastSessionLogs, activity);
            }
        });
    }

    static /* synthetic */ void lambda$shareLastLogs$23(File file, Activity activity) {
        if (file == null) {
            Toast.makeText(activity, "Логов пока нет — сначала запустите игру", 1).show();
        } else {
            activity.startActivity(Intent.createChooser(FalloutEngine.shareLogsIntent(activity, file), "Отправить логи"));
        }
    }

    private void about() {
        String str;
        LinearLayout linearLayoutGroup = group(null);
        try {
            str = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception unused) {
            str = "?";
        }
        info(linearLayoutGroup, R.drawable.ic_radiation, "Fallout Droid " + str + " | by Rommy", Ui.FG);
        action(linearLayoutGroup, R.drawable.ic_telegram, "Telegram-канал @rommy_apps", "Новости, обновления и помощь", new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$about$25(view);
            }
        });
        action(linearLayoutGroup, R.drawable.ic_info, "Лицензии открытого ПО", null, new View.OnClickListener() { // from class: com.wastelanddroid.SettingsActivity$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$about$26(view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$about$25(View view) {
        Links.openTelegram(this);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$about$26(View view) {
        new AlertDialog.Builder(this).setTitle("Лицензии").setMessage("Fallout Droid распространяется под лицензией GPL-3.0.\n\nИгровой движок — NVForever (GPL-3.0-or-later), github.com/LootJunkieAzza/NVForever, с изменениями для Android (исходники: nvport/ в репозитории Fallout Droid). Текст лицензии — assets/licenses.\n\nИконки: Game Icon Pack (CC0). Шрифт: Fallout Regular, Ash Fargouh.\n\nFallout является товарным знаком Bethesda Softworks. Проект не связан с Bethesda.").setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
    }

    private void resetPrefix() {
        new AlertDialog.Builder(this).setTitle("Сбросить настройки движка?").setMessage("Настройки движка для " + this.game.title + " (графика, управление, кэш шейдеров) будут созданы заново. Сохранения не удаляются.").setPositiveButton("Сбросить", new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                File home = FalloutEngine.homeDir(SettingsActivity.this, SettingsActivity.this.game.id);
                File[] kids = home.listFiles();
                if (kids != null) {
                    for (File f : kids) {
                        String n = f.getName();
                        if (!n.equals("saves") && !n.equals("save_backups")) com.wastelanddroid.util.Files2.deleteTree(f);
                    }
                }
                Toast.makeText(SettingsActivity.this, "Готово — нажмите \"Играть\"", 1).show();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: com.wastelanddroid.SettingsActivity$3, reason: invalid class name */
    static /* synthetic */ class AnonymousClass3 {
        static final /* synthetic */ int[] $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality;

        static {
            int[] iArr = new int[DeviceProfile.Quality.values().length];
            $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality = iArr;
            try {
                iArr[DeviceProfile.Quality.HIGH.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality[DeviceProfile.Quality.MEDIUM.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
        }
    }

    private static String qualityName(DeviceProfile.Quality quality) {
        int i = AnonymousClass3.$SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality[quality.ordinal()];
        if (i == 1) {
            return "высокое";
        }
        if (i == 2) {
            return "среднее";
        }
        return "низкое";
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        try {
        super.onActivityResult(i, i2, intent);
        if (i2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        Uri data = intent.getData();
        if (i == 11) {
            this.prefs.edit().putString(this.game.dirKey(), GameFiles.treeUriToFile(data).getPath()).apply();
        } else if (i == 12 || i == 13) {
            File fileCoverFile = i == 12 ? coverFile(this, this.game) : WastelandBackdrop.customFile(this);
            fileCoverFile.getParentFile().mkdirs();
            try {
                InputStream inputStreamOpenInputStream = getContentResolver().openInputStream(data);
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(fileCoverFile);
                    try {
                        Files2.copy(inputStreamOpenInputStream, fileOutputStream);
                        Toast.makeText(this, i == 12 ? "Обложка обновлена" : "Фон обновлён", 0).show();
                        fileOutputStream.close();
                        if (inputStreamOpenInputStream != null) {
                            inputStreamOpenInputStream.close();
                        }
                    } finally {
                    }
                } finally {
                }
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось загрузить картинку: " + e.getMessage(), 1).show();
            }
        }
        renderSection(false);
            } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        try {
        super.onResume();
        LinearLayout linearLayout = this.content;
        if (linearLayout == null || linearLayout.getChildCount() <= 0) {
            return;
        }
        renderSection(false);
            } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
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
