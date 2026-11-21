package com.wastelanddroid.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wastelanddroid.game.DeviceProfile;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.ScanlineView;
import com.wastelanddroid.util.Ui;

public final class GraphicsActivity extends Activity {
    private DeviceProfile.Quality base;
    private GraphicsSettings g;

    /* JADX INFO: Access modifiers changed from: private */
    interface BoolCb {
        void on(boolean z);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.immersive(this);
        String string = Prefs.get(this).getString(Prefs.QUALITY, "auto");
        DeviceProfile.Quality qualityAutoQuality = "auto".equals(string) ? DeviceProfile.autoQuality(this) : DeviceProfile.Quality.valueOf(string.toUpperCase());
        this.base = qualityAutoQuality;
        this.g = GraphicsSettings.load(this, qualityAutoQuality);
        build();
    }

    private void build() {
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackground(Ui.screenBackground());
        LinearLayout linearLayoutColumn = Ui.column(this);
        int iDp = Ui.dp(this, 20.0f);
        linearLayoutColumn.setPadding(iDp, Ui.dp(this, 12.0f), iDp, Ui.dp(this, 12.0f));
        LinearLayout linearLayoutRow = Ui.row(this);
        TextView textViewLabel = Ui.label(this, "НАСТРОЙКИ ГРАФИКИ");
        textViewLabel.setTextSize(20.0f);
        textViewLabel.setTypeface(Ui.mono(true));
        linearLayoutRow.addView(textViewLabel, Ui.weight());
        linearLayoutRow.addView(Ui.ghostButton(this, "Низкие", new View.OnClickListener() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GraphicsActivity.this.lambda$build$0(view);
            }
        }));
        linearLayoutRow.addView(Ui.ghostButton(this, "Средние", new View.OnClickListener() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda15
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GraphicsActivity.this.lambda$build$1(view);
            }
        }));
        linearLayoutRow.addView(Ui.ghostButton(this, "Высокие", new View.OnClickListener() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda16
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GraphicsActivity.this.lambda$build$2(view);
            }
        }));
        linearLayoutRow.addView(Ui.button(this, "Сохранить", new View.OnClickListener() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GraphicsActivity.this.lambda$build$3(view);
            }
        }));
        linearLayoutColumn.addView(linearLayoutRow);
        LinearLayout linearLayoutRow2 = Ui.row(this);
        linearLayoutRow2.setGravity(48);
        LinearLayout linearLayoutColumn2 = Ui.column(this);
        LinearLayout linearLayoutCard = Ui.card(this, "Текстуры и фильтрация");
        linearLayoutCard.addView(Ui.slider(this, "Качество текстур (0 — высокое, 2 — низкое)", 0, 2, this.g.textureQuality, new Ui.IntCallback() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda2
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                GraphicsActivity.this.lambda$build$4(i);
            }
        }));
        linearLayoutCard.addView(Ui.slider(this, "Анизотропная фильтрация", 1, 16, this.g.anisotropy, new Ui.IntCallback() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda3
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                GraphicsActivity.this.lambda$build$5(i);
            }
        }));
        linearLayoutCard.addView(Ui.slider(this, "Поле зрения (FOV)", 60, 100, this.g.fov, new Ui.IntCallback() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda4
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                GraphicsActivity.this.lambda$build$6(i);
            }
        }));
        linearLayoutColumn2.addView(linearLayoutCard);
        LinearLayout linearLayoutCard2 = Ui.card(this, "Свет и тени");
        linearLayoutCard2.addView(Ui.slider(this, "Тени (0 — выкл, 3 — высокие)", 0, 3, this.g.shadows, new Ui.IntCallback() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda5
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                GraphicsActivity.this.lambda$build$7(i);
            }
        }));
        linearLayoutCard2.addView(check("Тени от деревьев (дорого)", this.g.treeShadows, new BoolCb() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda6
            @Override // com.wastelanddroid.game.GraphicsActivity.BoolCb
            public final void on(boolean z) {
                GraphicsActivity.this.lambda$build$8(z);
            }
        }));
        linearLayoutCard2.addView(check("HDR (иначе — Bloom)", this.g.hdr, new BoolCb() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda7
            @Override // com.wastelanddroid.game.GraphicsActivity.BoolCb
            public final void on(boolean z) {
                GraphicsActivity.this.lambda$build$9(z);
            }
        }));
        linearLayoutCard2.addView(check("Bloom", this.g.bloom, new BoolCb() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda9
            @Override // com.wastelanddroid.game.GraphicsActivity.BoolCb
            public final void on(boolean z) {
                GraphicsActivity.this.lambda$build$10(z);
            }
        }));
        linearLayoutColumn2.addView(linearLayoutCard2, Ui.matchWrap(this, 10));
        linearLayoutRow2.addView(scroll(linearLayoutColumn2), new LinearLayout.LayoutParams(0, -1, 1.0f));
        linearLayoutRow2.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 14.0f), 1));
        LinearLayout linearLayoutColumn3 = Ui.column(this);
        LinearLayout linearLayoutCard3 = Ui.card(this, "Дальность");
        linearLayoutCard3.addView(Ui.slider(this, "Дальность ландшафта и объектов", 1, 10, this.g.viewDistance, new Ui.IntCallback() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda10
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                GraphicsActivity.this.lambda$build$11(i);
            }
        }));
        linearLayoutCard3.addView(Ui.slider(this, "Прорисовка персонажей и предметов", 1, 10, this.g.fade, new Ui.IntCallback() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda11
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                GraphicsActivity.this.lambda$build$12(i);
            }
        }));
        linearLayoutCard3.addView(Ui.slider(this, "Трава (0 — выкл)", 0, 3, this.g.grassDensity, new Ui.IntCallback() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda12
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                GraphicsActivity.this.lambda$build$13(i);
            }
        }));
        linearLayoutColumn3.addView(linearLayoutCard3);
        LinearLayout linearLayoutCard4 = Ui.card(this, "Эффекты");
        linearLayoutCard4.addView(check("Отражения в воде (самое дорогое)", this.g.waterReflections, new BoolCb() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda13
            @Override // com.wastelanddroid.game.GraphicsActivity.BoolCb
            public final void on(boolean z) {
                GraphicsActivity.this.lambda$build$14(z);
            }
        }));
        linearLayoutCard4.addView(check("Следы от пуль и крови (декали)", this.g.decals, new BoolCb() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda14
            @Override // com.wastelanddroid.game.GraphicsActivity.BoolCb
            public final void on(boolean z) {
                GraphicsActivity.this.lambda$build$15(z);
            }
        }));
        linearLayoutCard4.addView(Ui.hint(this, "Размер мира (uGridsToLoad) зафиксирован на 5: большие значения вызывают вылеты."));
        linearLayoutColumn3.addView(linearLayoutCard4, Ui.matchWrap(this, 10));
        linearLayoutRow2.addView(scroll(linearLayoutColumn3), new LinearLayout.LayoutParams(0, -1, 1.0f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams.topMargin = Ui.dp(this, 10.0f);
        linearLayoutColumn.addView(linearLayoutRow2, layoutParams);
        frameLayout.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -1));
        frameLayout.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frameLayout);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$0(View view) {
        preset(DeviceProfile.Quality.LOW);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$1(View view) {
        preset(DeviceProfile.Quality.MEDIUM);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$2(View view) {
        preset(DeviceProfile.Quality.HIGH);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$3(View view) {
        this.g.save(this);
        Toast.makeText(this, "Сохранено", 0).show();
        finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$4(int i) {
        this.g.textureQuality = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$5(int i) {
        this.g.anisotropy = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$6(int i) {
        this.g.fov = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$7(int i) {
        this.g.shadows = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$8(boolean z) {
        this.g.treeShadows = z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$9(boolean z) {
        this.g.hdr = z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$10(boolean z) {
        this.g.bloom = z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$11(int i) {
        this.g.viewDistance = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$12(int i) {
        this.g.fade = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$13(int i) {
        this.g.grassDensity = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$14(boolean z) {
        this.g.waterReflections = z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$15(boolean z) {
        this.g.decals = z;
    }

    private CheckBox check(String str, boolean z, final BoolCb boolCb) {
        CheckBox checkBoxCheck = Ui.check(this, str, z);
        checkBoxCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.game.GraphicsActivity$$ExternalSyntheticLambda0
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z2) {
                boolCb.on(z2);
            }
        });
        return checkBoxCheck;
    }

    private ScrollView scroll(View view) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(view);
        return scrollView;
    }

    private void preset(DeviceProfile.Quality quality) {
        this.g = GraphicsSettings.preset(quality);
        build();
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            Ui.immersive(this);
        }
    }
}
