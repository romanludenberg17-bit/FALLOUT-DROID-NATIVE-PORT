package com.wastelanddroid.saves;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wastelanddroid.R;
import com.wastelanddroid.engine.FalloutEngine;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.saves.SaveStore;
import com.wastelanddroid.util.Anim;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.ScanlineView;
import com.wastelanddroid.util.Ui;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class SavesActivity extends Activity {
    public static final String EXTRA_GAME = "game";
    private static final int REQ_IMPORT = 1;
    private LinearLayout backups;
    private GameDef game;
    private LinearLayout list;
    private SaveStore store;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.immersive(this);
        this.game = GameDef.byId(getIntent().getStringExtra("game"));
        String string = Prefs.get(this).getString("scope_" + this.game.id, null);
        this.store = new SaveStore(this.game, FalloutEngine.homeDir(this, this.game.id));
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackground(Ui.screenBackground());
        LinearLayout linearLayoutColumn = Ui.column(this);
        int iDp = Ui.dp(this, 20.0f);
        linearLayoutColumn.setPadding(iDp, Ui.dp(this, 12.0f), iDp, Ui.dp(this, 12.0f));
        LinearLayout linearLayoutRow = Ui.row(this);
        TextView textViewLabel = Ui.label(this, "СОХРАНЕНИЯ | " + this.game.title.toUpperCase());
        textViewLabel.setTextSize(20.0f);
        textViewLabel.setTypeface(Ui.mono(true));
        linearLayoutRow.addView(textViewLabel, Ui.weight());
        linearLayoutRow.addView(Ui.button(this, R.drawable.ic_download, "Добавить сохранения", new View.OnClickListener() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SavesActivity.this.lambda$onCreate$0(view);
            }
        }));
        linearLayoutRow.addView(Ui.ghostButton(this, R.drawable.ic_upload, "Экспорт всех", new View.OnClickListener() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SavesActivity.this.lambda$onCreate$1(view);
            }
        }));
        linearLayoutColumn.addView(linearLayoutRow);
        Anim.enterFromTop(linearLayoutRow, 0L);
        LinearLayout linearLayoutRow2 = Ui.row(this);
        linearLayoutRow2.setGravity(48);
        LinearLayout linearLayoutCard = Ui.card(this, "В игре (новые сверху)");
        LinearLayout linearLayoutColumn2 = Ui.column(this);
        this.list = linearLayoutColumn2;
        linearLayoutCard.addView(linearLayoutColumn2);
        linearLayoutCard.addView(Ui.hint(this, "Добавляйте файлы .fos (вместе с .fose/.nvse, если есть) или .zip с ними. Сохранения с ПК подходят как есть."));
        linearLayoutRow2.addView(scroll(linearLayoutCard), new LinearLayout.LayoutParams(0, -1, 1.5f));
        linearLayoutRow2.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 14.0f), 1));
        LinearLayout linearLayoutCard2 = Ui.card(this, "Автокопии перед запуском");
        LinearLayout linearLayoutColumn3 = Ui.column(this);
        this.backups = linearLayoutColumn3;
        linearLayoutCard2.addView(linearLayoutColumn3);
        linearLayoutRow2.addView(scroll(linearLayoutCard2), new LinearLayout.LayoutParams(0, -1, 1.0f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams.topMargin = Ui.dp(this, 10.0f);
        linearLayoutColumn.addView(linearLayoutRow2, layoutParams);
        Anim.enter(linearLayoutCard, 120L);
        Anim.enter(linearLayoutCard2, 220L);
        frameLayout.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frameLayout);
        render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$0(View view) {
        pickImport();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$1(View view) {
        exportAll();
    }

    private ScrollView scroll(View view) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(view);
        return scrollView;
    }

    private void render() {
        this.list.removeAllViews();
        List<SaveStore.Save> list = this.store.list();
        if (list.isEmpty()) {
            this.list.addView(Ui.hint(this, "Сохранений пока нет."));
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        int i = 0;
        for (final SaveStore.Save save : list) {
            LinearLayout linearLayoutRow = Ui.row(this);
            linearLayoutRow.setBackground(Ui.rounded(i % 2 == 0 ? Ui.tint(18) : 0, 0, 8.0f, this));
            TextView textViewLabel = Ui.label(this, save.title() + "\n" + simpleDateFormat.format(new Date(save.file.lastModified())) + " | " + (save.size() / 1024) + " КБ" + (save.companions.isEmpty() ? "" : " | +" + save.companions.size()));
            textViewLabel.setTextSize(13.0f);
            linearLayoutRow.addView(textViewLabel, Ui.weight());
            linearLayoutRow.addView(Ui.styledButton(this, "Удалить", new View.OnClickListener() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SavesActivity.this.lambda$render$3(save, view);
                }
            }, Color.argb(60, 255, 100, 90), Ui.RED, Ui.RED));
            this.list.addView(linearLayoutRow);
            if (i < 12) {
                Anim.enter(linearLayoutRow, i * 60);
            }
            i++;
        }
        this.backups.removeAllViews();
        List<File> listBackups = SaveStore.backups(new File(FalloutEngine.homeDir(this, this.game.id), "save_backups"), this.game);
        if (listBackups.isEmpty()) {
            this.backups.addView(Ui.hint(this, "Копии создаются автоматически перед каждым запуском игры (последние 5)."));
        }
        for (final File file : listBackups) {
            LinearLayout linearLayoutRow2 = Ui.row(this);
            String[] list2 = file.list();
            linearLayoutRow2.addView(Ui.label(this, prettyStamp(file.getName()) + "\n" + (list2 == null ? 0 : list2.length) + " файлов"), Ui.weight());
            linearLayoutRow2.addView(Ui.ghostButton(this, "Вернуть", new View.OnClickListener() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SavesActivity.this.lambda$render$5(file, view);
                }
            }));
            this.backups.addView(linearLayoutRow2);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$render$3(final SaveStore.Save save, View view) {
        new AlertDialog.Builder(this).setTitle("Удалить '" + save.title() + "'?").setPositiveButton("Удалить", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SavesActivity.this.lambda$render$2(save, dialogInterface, i);
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$render$2(SaveStore.Save save, DialogInterface dialogInterface, int i) {
        this.store.delete(save);
        render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$render$5(final File file, View view) {
        new AlertDialog.Builder(this).setTitle("Восстановить копию?").setMessage("Файлы из копии будут добавлены к текущим (одноимённые — заменены).").setPositiveButton("Восстановить", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda3
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SavesActivity.this.lambda$render$4(file, dialogInterface, i);
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$render$4(File file, DialogInterface dialogInterface, int i) {
        try {
            this.store.restoreBackup(file);
            toast("Восстановлено");
        } catch (Exception e) {
            toast("Ошибка: " + e.getMessage());
        }
        render();
    }

    private static String prettyStamp(String str) {
        return str.length() < 15 ? str : str.substring(6, 8) + "." + str.substring(4, 6) + "." + str.substring(0, 4) + " " + str.substring(9, 11) + ":" + str.substring(11, 13);
    }

    private void pickImport() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        startActivityForResult(intent, 1);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == 1 && i2 == -1 && intent != null) {
            final ArrayList arrayList = new ArrayList();
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i3 = 0; i3 < clipData.getItemCount(); i3++) {
                    arrayList.add(clipData.getItemAt(i3).getUri());
                }
            } else if (intent.getData() != null) {
                arrayList.add(intent.getData());
            }
            new Thread(new Runnable() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
        try {
                    SavesActivity.this.lambda$onActivityResult$7(arrayList);
                        } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
            }).start();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onActivityResult$7(List list) throws IOException {
        final StringBuilder sb = new StringBuilder();
        Iterator it = list.iterator();
        int iImportStream = 0;
        while (it.hasNext()) {
            Uri uri = (Uri) it.next();
            String strDisplayName = displayName(uri);
            try {
                InputStream inputStreamOpenInputStream = getContentResolver().openInputStream(uri);
                try {
                    iImportStream += this.store.importStream(strDisplayName, inputStreamOpenInputStream);
                    if (inputStreamOpenInputStream != null) {
                        inputStreamOpenInputStream.close();
                    }
                } catch (Throwable th) {
                    if (inputStreamOpenInputStream != null) {
                        try {
                            inputStreamOpenInputStream.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (Exception e) {
                sb.append('\n').append(e.getMessage());
            }
        }
        final int imported = iImportStream;
        runOnUiThread(new Runnable() { // from class: com.wastelanddroid.saves.SavesActivity$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                SavesActivity.this.lambda$onActivityResult$6(imported, sb);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onActivityResult$6(int i, StringBuilder sb) {
        toast("Добавлено сохранений: " + i + ((Object) sb));
        render();
    }

    private String displayName(Uri uri) {
        try {
            Cursor cursorQuery = getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null);
            if (cursorQuery != null) {
                try {
                    if (cursorQuery.moveToFirst()) {
                        String string = cursorQuery.getString(0);
                        if (cursorQuery != null) {
                            cursorQuery.close();
                        }
                        return string;
                    }
                } finally {
                }
            }
            if (cursorQuery != null) {
                cursorQuery.close();
            }
        } catch (Exception unused) {
        }
        String lastPathSegment = uri.getLastPathSegment();
        return lastPathSegment != null ? lastPathSegment : "save.fos";
    }

    private void exportAll() {
        try {
            toast("Скопировано в " + this.store.exportAll().getPath());
        } catch (Exception e) {
            toast("Ошибка экспорта: " + e.getMessage());
        }
    }

    private void toast(String str) {
        Toast.makeText(this, str, 1).show();
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
