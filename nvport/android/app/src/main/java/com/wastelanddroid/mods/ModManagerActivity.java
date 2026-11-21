package com.wastelanddroid.mods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wastelanddroid.R;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.mods.ModManager;
import com.wastelanddroid.util.ScanlineView;
import com.wastelanddroid.util.Ui;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.json.JSONException;

public final class ModManagerActivity extends Activity {
    public static final String EXTRA_GAME = "game";
    private static final int REQ_ZIP = 1;
    private TextView counter;
    private GameDef game;
    private ModManager mm;
    private LinearLayout modList;
    private LinearLayout pluginList;
    private List<ModManager.Plugin> plugins;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
        super.onCreate(bundle);
        Ui.immersive(this);
        GameDef gameDefById = GameDef.byId(getIntent().getStringExtra("game"));
        this.game = gameDefById;
        String strGameDir = gameDefById.gameDir(this);
        if (strGameDir == null) {
            Toast.makeText(this, "Сначала выберите папку игры", 1).show();
            finish();
            return;
        }
        ModManager modManager = new ModManager(this, this.game, new File(strGameDir));
        this.mm = modManager;
        this.plugins = modManager.loadPlugins();
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackground(Ui.screenBackground());
        LinearLayout linearLayoutColumn = Ui.column(this);
        int iDp = Ui.dp(this, 20.0f);
        linearLayoutColumn.setPadding(iDp, Ui.dp(this, 12.0f), iDp, Ui.dp(this, 12.0f));
        LinearLayout linearLayoutRow = Ui.row(this);
        TextView textViewLabel = Ui.label(this, "МОДЫ | " + this.game.title.toUpperCase());
        textViewLabel.setTextSize(20.0f);
        textViewLabel.setTypeface(Ui.mono(true));
        linearLayoutRow.addView(textViewLabel, Ui.weight());
        linearLayoutRow.addView(Ui.ghostButton(this, R.drawable.ic_download, "Установить .zip", new View.OnClickListener() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ModManagerActivity.this.lambda$onCreate$0(view);
            }
        }));
        linearLayoutRow.addView(Ui.button(this, "Сохранить", new View.OnClickListener() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ModManagerActivity.this.lambda$onCreate$1(view);
            }
        }));
        linearLayoutColumn.addView(linearLayoutRow);
        LinearLayout linearLayoutRow2 = Ui.row(this);
        linearLayoutRow2.setGravity(48);
        LinearLayout linearLayoutCard = Ui.card(this, "Порядок загрузки (сверху вниз)");
        TextView textViewHint = Ui.hint(this, "");
        this.counter = textViewHint;
        linearLayoutCard.addView(textViewHint);
        LinearLayout linearLayoutColumn2 = Ui.column(this);
        this.pluginList = linearLayoutColumn2;
        linearLayoutCard.addView(linearLayoutColumn2);
        linearLayoutRow2.addView(scroll(linearLayoutCard), new LinearLayout.LayoutParams(0, -1, 1.5f));
        linearLayoutRow2.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 14.0f), 1));
        LinearLayout linearLayoutCard2 = Ui.card(this, "Установленные архивы");
        LinearLayout linearLayoutColumn3 = Ui.column(this);
        this.modList = linearLayoutColumn3;
        linearLayoutCard2.addView(linearLayoutColumn3);
        linearLayoutCard2.addView(Ui.hint(this, "Поддерживаются .zip. Архивы .7z/.rar сначала перепакуйте в .zip. Установщики FOMOD с выбором опций ставятся целиком — лишнее удалите вручную."));
        linearLayoutCard2.addView(Ui.hint(this, "Мастер-файлы (.esm) всегда загружаются раньше плагинов (.esp); основная игра и официальные DLC закреплены вверху."));
        linearLayoutRow2.addView(scroll(linearLayoutCard2), new LinearLayout.LayoutParams(0, -1, 1.0f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams.topMargin = Ui.dp(this, 10.0f);
        linearLayoutColumn.addView(linearLayoutRow2, layoutParams);
        frameLayout.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frameLayout);
        renderPlugins();
        renderMods();
            } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$0(View view) {
        pickZip();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$1(View view) {
        save(true);
    }

    private ScrollView scroll(View view) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(view);
        return scrollView;
    }

    private void renderPlugins() {
        this.pluginList.removeAllViews();
        int iEnabledCount = this.mm.enabledCount(this.plugins);
        this.counter.setText("Включено: " + iEnabledCount + " / 255" + (iEnabledCount > 255 ? "  — превышен лимит движка!" : ""));
        this.counter.setTextColor(iEnabledCount > 255 ? Ui.RED : Ui.FG_DIM);
        for (int i0 = 0; i0 < this.plugins.size(); i0++) {
            final int i = i0;
            final ModManager.Plugin plugin = this.plugins.get(i);
            LinearLayout linearLayoutRow = Ui.row(this);
            linearLayoutRow.setBackground(Ui.rounded(i % 2 == 0 ? Ui.tint(18) : 0, 0, 8.0f, this));
            TextView textViewHint = Ui.hint(this, String.format("%02X", Integer.valueOf(i)));
            textViewHint.setMinWidth(Ui.dp(this, 30.0f));
            linearLayoutRow.addView(textViewHint);
            CheckBox checkBoxCheck = Ui.check(this, plugin.name, plugin.enabled);
            checkBoxCheck.setEnabled((plugin.official && i == 0) ? false : true);
            checkBoxCheck.setTextColor(plugin.master ? Ui.AMBER : Ui.FG);
            checkBoxCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda6
                @Override // android.widget.CompoundButton.OnCheckedChangeListener
                public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                    ModManagerActivity.this.lambda$renderPlugins$2(plugin, compoundButton, z);
                }
            });
            linearLayoutRow.addView(checkBoxCheck, Ui.weight());
            if (!plugin.official) {
                linearLayoutRow.addView(small(R.drawable.ic_arrow_up, new View.OnClickListener() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda7
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        ModManagerActivity.this.lambda$renderPlugins$3(i, view);
                    }
                }));
                linearLayoutRow.addView(small(R.drawable.ic_arrow_down, new View.OnClickListener() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda8
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        ModManagerActivity.this.lambda$renderPlugins$4(i, view);
                    }
                }));
            }
            this.pluginList.addView(linearLayoutRow);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$renderPlugins$2(ModManager.Plugin plugin, CompoundButton compoundButton, boolean z) {
        plugin.enabled = z;
        renderCounterOnly();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$renderPlugins$3(int i, View view) {
        move(i, -1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$renderPlugins$4(int i, View view) {
        move(i, 1);
    }

    private void renderCounterOnly() {
        int iEnabledCount = this.mm.enabledCount(this.plugins);
        this.counter.setText("Включено: " + iEnabledCount + " / 255" + (iEnabledCount > 255 ? "  — превышен лимит движка!" : ""));
    }

    private Button small(int i, View.OnClickListener onClickListener) {
        Button buttonGhostButton = Ui.ghostButton(this, "", onClickListener);
        buttonGhostButton.setCompoundDrawablesRelative(Ui.icon(this, i, Ui.FG, 16.0f), null, null, null);
        buttonGhostButton.setMinWidth(Ui.dp(this, 40.0f));
        buttonGhostButton.setMinimumWidth(Ui.dp(this, 40.0f));
        buttonGhostButton.setMinHeight(Ui.dp(this, 36.0f));
        buttonGhostButton.setPadding(Ui.dp(this, 8.0f), 0, Ui.dp(this, 8.0f), 0);
        return buttonGhostButton;
    }

    private void move(int i, int i2) {
        int i3 = i2 + i;
        if (i3 < 0 || i3 >= this.plugins.size()) {
            return;
        }
        ModManager.Plugin plugin = this.plugins.get(i);
        ModManager.Plugin plugin2 = this.plugins.get(i3);
        if (plugin2.official || plugin.master != plugin2.master) {
            return;
        }
        this.plugins.set(i, plugin2);
        this.plugins.set(i3, plugin);
        renderPlugins();
    }

    private void renderMods() throws JSONException {
        this.modList.removeAllViews();
        List<ModManager.InstalledMod> listInstalledMods = this.mm.installedMods();
        if (listInstalledMods.isEmpty()) {
            this.modList.addView(Ui.hint(this, "Пока ничего не установлено."));
        }
        for (final ModManager.InstalledMod installedMod : listInstalledMods) {
            LinearLayout linearLayoutRow = Ui.row(this);
            linearLayoutRow.addView(Ui.label(this, installedMod.name + "\n" + installedMod.files.size() + " файлов"), Ui.weight());
            linearLayoutRow.addView(Ui.styledButton(this, "Удалить", new View.OnClickListener() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    ModManagerActivity.this.lambda$renderMods$6(installedMod, view);
                }
            }, Ui.RED, -1, 0));
            this.modList.addView(linearLayoutRow);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$renderMods$6(final ModManager.InstalledMod installedMod, View view) {
        new AlertDialog.Builder(this).setTitle("Удалить '" + installedMod.name + "'?").setMessage("Файлы мода будут удалены, перезаписанные им файлы — восстановлены.").setPositiveButton("Удалить", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda2
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
        try {
                ModManagerActivity.this.lambda$renderMods$5(installedMod, dialogInterface, i);
                    } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$renderMods$5(ModManager.InstalledMod installedMod, DialogInterface dialogInterface, int i) throws JSONException {
        try {
            this.mm.uninstall(installedMod);
        } catch (Exception e) {
            toast("Ошибка: " + e.getMessage());
        }
        this.plugins = this.mm.loadPlugins();
        renderPlugins();
        renderMods();
    }

    private void pickZip() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        intent.putExtra("android.intent.extra.MIME_TYPES", new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, 1);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i != 1 || i2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        final Uri data = intent.getData();
        final String strDisplayName = displayName(data);
        toast("Установка '" + strDisplayName + "'...");
        new Thread(new Runnable() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
        try {
                ModManagerActivity.this.lambda$onActivityResult$8(data, strDisplayName);
                    } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onActivityResult$8(Uri uri, String str) throws IOException {
        String result;
        try (InputStream inputStreamOpenInputStream = getContentResolver().openInputStream(uri)) {
            ModManager.InstalledMod installedModInstall = this.mm.install(str, inputStreamOpenInputStream, getCacheDir());
            result = "Установлено: " + installedModInstall.name + " (" + installedModInstall.files.size() + " файлов)";
        } catch (Exception e) {
            result = "Ошибка установки: " + e.getMessage();
        }
        final String str2 = result;
        runOnUiThread(new Runnable() { // from class: com.wastelanddroid.mods.ModManagerActivity$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
        try {
                ModManagerActivity.this.lambda$onActivityResult$7(str2);
                    } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onActivityResult$7(String str) throws JSONException {
        toast(str);
        save(false);
        this.plugins = this.mm.loadPlugins();
        renderPlugins();
        renderMods();
    }

    private String displayName(Uri uri) {
        try {
            Cursor cursorQuery = getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null);
            if (cursorQuery != null) {
                try {
                    if (cursorQuery.moveToFirst()) {
                        String strReplaceAll = cursorQuery.getString(0).replaceAll("(?i)\\.zip$", "");
                        if (cursorQuery != null) {
                            cursorQuery.close();
                        }
                        return strReplaceAll;
                    }
                } finally {
                }
            }
            if (cursorQuery != null) {
                cursorQuery.close();
            }
        } catch (Exception unused) {
        }
        return "mod-" + System.currentTimeMillis();
    }

    private void save(boolean z) {
        try {
            this.mm.saveOrder(this.plugins);
            if (z) {
                toast("Порядок загрузки сохранён");
            }
        } catch (Exception e) {
            toast("Ошибка сохранения: " + e.getMessage());
        }
        renderPlugins();
    }

    private void toast(String str) {
        Toast.makeText(this, str, 1).show();
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        ModManager modManager = this.mm;
        if (modManager != null) {
            try {
                modManager.saveOrder(this.plugins);
            } catch (Exception unused) {
            }
        }
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            Ui.immersive(this);
        }
    }
}
