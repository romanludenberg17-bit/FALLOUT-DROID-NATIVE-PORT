package com.wastelanddroid.controls;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wastelanddroid.R;
import com.wastelanddroid.input.ScreenKeyStore;
import com.wastelanddroid.input.ScreenKey;
import com.wastelanddroid.input.ScreenKeysOverlay;
import com.wastelanddroid.engine.FalloutInput;
import com.wastelanddroid.util.Anim;
import com.wastelanddroid.util.Ui;
import com.wastelanddroid.util.WastelandBackdrop;
import java.util.Collections;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

public final class ControlsEditorActivity extends Activity {
    private ScreenKeysOverlay keys;
    private LinearLayout mini;
    private LinearLayout panel;
    private LinearLayout panelBody;
    private FrameLayout root;
    private List<Integer> selected;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.immersive(this);
        PcLayout.installIfEmpty(this);
        FrameLayout frameLayout = new FrameLayout(this);
        this.root = frameLayout;
        frameLayout.setBackgroundColor(-16777216);
        WastelandBackdrop wastelandBackdrop = new WastelandBackdrop(this);
        wastelandBackdrop.setAlpha(0.5f);
        this.root.addView(wastelandBackdrop, new FrameLayout.LayoutParams(-1, -1));
        TextView textViewLabel = Ui.label(this, "Обзор: свайп по пустому месту экрана");
        textViewLabel.setTextColor(Ui.FG_DIM);
        this.root.addView(textViewLabel, new FrameLayout.LayoutParams(-2, -2, 17));
        ScreenKeysOverlay screenKeys = new ScreenKeysOverlay(this, new Function2() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda19
            @Override // kotlin.jvm.functions.Function2
            public final Object invoke(Object obj, Object obj2) {
                return Unit.INSTANCE;
            }
        });
        this.keys = screenKeys;
        screenKeys.setOnSelect(new Function1() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda20
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return ControlsEditorActivity.this.lambda$onCreate$1((List) obj);
            }
        });
        this.root.addView((View) this.keys, (ViewGroup.LayoutParams) new FrameLayout.LayoutParams(-1, -1));
        this.keys.load(ScreenKeyStore.encode(ScreenKeyStore.load(this)));
        this.keys.startEditing();
        buildPanel();
        setContentView(this.root);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ Unit lambda$onCreate$1(List list) {
        showMini(list);
        return Unit.INSTANCE;
    }

    private void buildPanel() {
        LinearLayout linearLayoutColumn = Ui.column(this);
        this.panel = linearLayoutColumn;
        linearLayoutColumn.setBackground(Ui.rounded(Color.argb(238, 14, 20, 16), Ui.LINE, 16.0f, this));
        int iDp = Ui.dp(this, 10.0f);
        this.panel.setPadding(iDp, Ui.dp(this, 4.0f), iDp, iDp);
        LinearLayout linearLayoutRow = Ui.row(this);
        TextView textViewLabel = Ui.label(this, "===  УПРАВЛЕНИЕ  ===");
        textViewLabel.setTypeface(Ui.mono(true));
        textViewLabel.setTextColor(Ui.AMBER);
        textViewLabel.setGravity(17);
        linearLayoutRow.addView(textViewLabel, Ui.weight());
        final Button buttonGhostButton = Ui.ghostButton(this, R.drawable.ic_arrow_up, "", null);
        buttonGhostButton.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ControlsEditorActivity.this.lambda$buildPanel$2(buttonGhostButton, view);
            }
        });
        linearLayoutRow.addView(buttonGhostButton);
        this.panel.addView(linearLayoutRow);
        final float[] fArr = new float[2];
        textViewLabel.setOnTouchListener(new View.OnTouchListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnTouchListener
            public final boolean onTouch(View view, MotionEvent motionEvent) {
                return ControlsEditorActivity.this.lambda$buildPanel$3(fArr, view, motionEvent);
            }
        });
        LinearLayout linearLayoutColumn2 = Ui.column(this);
        this.panelBody = linearLayoutColumn2;
        linearLayoutColumn2.addView(Ui.hint(this, "Перетаскивайте кнопки. Нажмите на кнопку — рядом откроется её настройка."));
        final SharedPreferences sharedPreferences = getSharedPreferences("wasteland", 0);
        LinearLayout linearLayoutRow2 = Ui.row(this);
        final int[] iArr = {sharedPreferences.getInt(FalloutInput.BTN_SCALE, 100)};
        linearLayoutRow2.addView(Ui.slider(this, "Размер всех кнопок, %", 60, 160, iArr[0], new Ui.IntCallback() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda3
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                ControlsEditorActivity.this.lambda$buildPanel$4(iArr, sharedPreferences, i);
            }
        }), Ui.weight());
        linearLayoutRow2.addView(Ui.slider(this, "Прозрачность всех, %", 20, 100, sharedPreferences.getInt(FalloutInput.BTN_OPACITY, 100), new Ui.IntCallback() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda4
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                ControlsEditorActivity.this.lambda$buildPanel$5(sharedPreferences, i);
            }
        }), Ui.weight());
        this.panelBody.addView(linearLayoutRow2);
        LinearLayout linearLayoutRow3 = Ui.row(this);
        linearLayoutRow3.addView(Ui.slider(this, "Чувствительность обзора, %", 30, 300, sharedPreferences.getInt(FalloutInput.LOOK_SENS, 115), new Ui.IntCallback() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda5
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                sharedPreferences.edit().putInt(FalloutInput.LOOK_SENS, i).apply();
            }
        }), Ui.weight());
        this.panelBody.addView(linearLayoutRow3);
        LinearLayout linearLayoutRow4 = Ui.row(this);
        linearLayoutRow4.addView(pref(sharedPreferences, "Обзор с кнопки огня", FalloutInput.FIRE_LOOK, true), Ui.weight());
        linearLayoutRow4.addView(pref(sharedPreferences, "Обзор правой половиной", FalloutInput.LOOK_RIGHT, false), Ui.weight());
        this.panelBody.addView(linearLayoutRow4);
        LinearLayout linearLayoutRow5 = Ui.row(this);
        linearLayoutRow5.addView(pref(sharedPreferences, "Вибрация", FalloutInput.HAPTICS, true), Ui.weight());
        linearLayoutRow5.addView(pref(sharedPreferences, "Касание = клик мыши", FalloutInput.TAP_FIRES, true), Ui.weight());
        CheckBox checkBoxCheck = Ui.check(this, "Подписи", sharedPreferences.getBoolean(FalloutInput.BTN_LABELS, true));
        checkBoxCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda6
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                ControlsEditorActivity.this.lambda$buildPanel$7(sharedPreferences, compoundButton, z);
            }
        });
        linearLayoutRow5.addView(checkBoxCheck, Ui.weight());
        this.panelBody.addView(linearLayoutRow5);
        LinearLayout linearLayoutRow6 = Ui.row(this);
        linearLayoutRow6.addView(pref(sharedPreferences, "Показывать курсор", FalloutInput.SHOW_CURSOR, true), Ui.weight());
        linearLayoutRow6.addView(pref(sharedPreferences, "Инверсия по вертикали", FalloutInput.INVERT_Y, false), Ui.weight());
        this.panelBody.addView(linearLayoutRow6);
        LinearLayout linearLayoutRow7 = Ui.row(this);
        linearLayoutRow7.addView(Ui.hint(this, "Цвет кнопок:"));
        int[] iArr2 = {Ui.ACCENT_COLORS[1], Ui.ACCENT_COLORS[0], Ui.ACCENT_COLORS[2]};
        String[] strArr = {"Оранжевый", "Зелёный", "Белый"};
        for (int i = 0; i < 3; i++) {
            final int i2 = iArr2[i];
            Button buttonStyledButton = Ui.styledButton(this, strArr[i], new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda7
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    ControlsEditorActivity.this.lambda$buildPanel$8(i2, view);
                }
            }, i2, Color.argb(255, 10, 16, 10), 0);
            buttonStyledButton.setTextSize(11.0f);
            linearLayoutRow7.addView(buttonStyledButton);
        }
        this.panelBody.addView(linearLayoutRow7);
        LinearLayout linearLayoutRow8 = Ui.row(this);
        linearLayoutRow8.addView(Ui.ghostButton(this, R.drawable.ic_plus, "Добавить кнопку", new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ControlsEditorActivity.this.lambda$buildPanel$9(view);
            }
        }), Ui.weight());
        linearLayoutRow8.addView(Ui.ghostButton(this, R.drawable.ic_restore, "Сбросить", new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ControlsEditorActivity.this.lambda$buildPanel$10(sharedPreferences, view);
            }
        }), Ui.weight());
        linearLayoutRow8.addView(Ui.button(this, R.drawable.ic_check, "Готово", new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ControlsEditorActivity.this.lambda$buildPanel$11(view);
            }
        }));
        this.panelBody.addView(linearLayoutRow8);
        this.panel.addView(this.panelBody);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(Ui.dp(this, 580.0f), -2, 49);
        layoutParams.topMargin = Ui.dp(this, 6.0f);
        this.root.addView(this.panel, layoutParams);
        Anim.enterFromTop(this.panel, 80L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$2(Button button, View view) {
        boolean z = this.panelBody.getVisibility() == 0;
        this.panelBody.setVisibility(z ? 8 : 0);
        Ui.withIcon(button, z ? R.drawable.ic_arrow_down : R.drawable.ic_arrow_up, Ui.FG);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$buildPanel$3(float[] fArr, View view, MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0) {
            fArr[0] = motionEvent.getRawX() - this.panel.getX();
            fArr[1] = motionEvent.getRawY() - this.panel.getY();
            return true;
        }
        if (actionMasked != 2) {
            return true;
        }
        this.panel.setX(clamp(motionEvent.getRawX() - fArr[0], 0.0f, this.root.getWidth() - this.panel.getWidth()));
        this.panel.setY(clamp(motionEvent.getRawY() - fArr[1], 0.0f, this.root.getHeight() - this.panel.getHeight()));
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$4(int[] iArr, SharedPreferences sharedPreferences, int i) {
        int i2 = iArr[0];
        if (i == i2) {
            return;
        }
        this.keys.scaleAll(i / i2);
        iArr[0] = i;
        sharedPreferences.edit().putInt(FalloutInput.BTN_SCALE, i).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$5(SharedPreferences sharedPreferences, int i) {
        this.keys.opacityAll(i / 100.0f);
        sharedPreferences.edit().putInt(FalloutInput.BTN_OPACITY, i).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$7(SharedPreferences sharedPreferences, CompoundButton compoundButton, boolean z) {
        sharedPreferences.edit().putBoolean(FalloutInput.BTN_LABELS, z).apply();
        this.keys.labelsAll(z);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$8(int i, View view) {
        ScreenKeyStore.setTint(this, i);
        this.keys.refreshAppearance();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$9(View view) {
        addKey();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$10(SharedPreferences sharedPreferences, View view) {
        ScreenKeyStore.save(this, PcLayout.defaults(this));
        sharedPreferences.edit().putInt(FalloutInput.BTN_SCALE, 100).putInt(FalloutInput.BTN_OPACITY, 100).apply();
        this.keys.load(ScreenKeyStore.encode(PcLayout.defaults(this)));
        this.keys.startEditing();
        closeMini();
        Toast.makeText(this, "Стандартная раскладка Fallout", 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildPanel$11(View view) {
        finish();
    }

    private CheckBox pref(final SharedPreferences sharedPreferences, String str, final String str2, boolean z) {
        CheckBox checkBoxCheck = Ui.check(this, str, sharedPreferences.getBoolean(str2, z));
        checkBoxCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda12
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z2) {
                sharedPreferences.edit().putBoolean(str2, z2).apply();
            }
        });
        return checkBoxCheck;
    }

    private void addKey() {
        int iIntValue;
        Object[][] objArr = PcLayout.CHOICES;
        int length = objArr.length;
        int i = 0;
        while (true) {
            if (i >= length) {
                iIntValue = 18;
                break;
            }
            iIntValue = ((Integer) objArr[i][0]).intValue();
            if (this.keys.stateOf(Collections.singletonList(Integer.valueOf(iIntValue))) == null) {
                break;
            } else {
                i++;
            }
        }
        this.keys.add(Collections.singletonList(Integer.valueOf(iIntValue)));
        showMini(Collections.singletonList(Integer.valueOf(iIntValue)));
    }

    private void closeMini() {
        LinearLayout linearLayout = this.mini;
        if (linearLayout != null) {
            this.root.removeView(linearLayout);
            this.mini = null;
        }
        this.selected = null;
    }

    private void showMini(final List<Integer> list) {
        ScreenKey screenKeyStateOf;
        float f;
        closeMini();
        if (list == null || (screenKeyStateOf = this.keys.stateOf(list)) == null) {
            return;
        }
        this.selected = list;
        boolean z = screenKeyStateOf.getShape() == 6 || screenKeyStateOf.getShape() == 5;
        LinearLayout linearLayoutColumn = Ui.column(this);
        this.mini = linearLayoutColumn;
        linearLayoutColumn.setBackground(Ui.rounded(Color.argb(246, 14, 20, 16), Ui.AMBER, 14.0f, this));
        int iDp = Ui.dp(this, 10.0f);
        this.mini.setPadding(iDp, iDp, iDp, iDp);
        LinearLayout linearLayoutRow = Ui.row(this);
        TextView textViewLabel = Ui.label(this, z ? "Стик движения (WASD)" : "Кнопка: " + screenKeyStateOf.shownLabel(this));
        textViewLabel.setTypeface(Ui.mono(true));
        textViewLabel.setTextColor(Ui.AMBER);
        linearLayoutRow.addView(textViewLabel, Ui.weight());
        linearLayoutRow.addView(Ui.ghostButton(this, R.drawable.ic_cross, "", new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ControlsEditorActivity.this.lambda$showMini$13(view);
            }
        }));
        this.mini.addView(linearLayoutRow);
        this.mini.addView(Ui.slider(this, "Размер", 24, 140, screenKeyStateOf.getSize(), new Ui.IntCallback() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda11
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                ControlsEditorActivity.this.lambda$showMini$14(list, i);
            }
        }));
        this.mini.addView(Ui.slider(this, "Прозрачность, %", 15, 100, Math.round(screenKeyStateOf.getOpacity() * 100.0f), new Ui.IntCallback() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda13
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i) {
                ControlsEditorActivity.this.lambda$showMini$15(list, i);
            }
        }));
        if (!z) {
            this.mini.addView(Ui.hint(this, "Форма:"));
            LinearLayout linearLayoutRow2 = Ui.row(this);
            int[][] iArr = {new int[]{2, 0}, new int[]{3, 0}, new int[]{7, 0}, new int[]{8, 0}, new int[]{9, 0}, new int[]{10, 0}};
            String[] strArr = {"Круг", "Квадрат", "Овал", "Ромб", "Шестиуг.", "Без формы"};
            int i = 0;
            while (true) {
                f = 11.0f;
                if (i >= 6) {
                    break;
                }
                final int i2 = iArr[i][0];
                Button button = screenKeyStateOf.getShape() == i2 ? Ui.button(this, strArr[i], null) : Ui.ghostButton(this, strArr[i], null);
                button.setTextSize(11.0f);
                button.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda14
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        ControlsEditorActivity.this.lambda$showMini$16(list, i2, view);
                    }
                });
                linearLayoutRow2.addView(button);
                i++;
            }
            this.mini.addView(linearLayoutRow2);
            CheckBox checkBoxCheck = Ui.check(this, "Фиксация (нажал — держится)", screenKeyStateOf.getToggle());
            checkBoxCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda15
                @Override // android.widget.CompoundButton.OnCheckedChangeListener
                public final void onCheckedChanged(CompoundButton compoundButton, boolean z2) {
                    ControlsEditorActivity.this.lambda$showMini$17(list, compoundButton, z2);
                }
            });
            this.mini.addView(checkBoxCheck);
            this.mini.addView(Ui.hint(this, "Что делает кнопка:"));
            GridLayout gridLayout = new GridLayout(this);
            gridLayout.setColumnCount(2);
            int iIntValue = list.isEmpty() ? 0 : list.get(0).intValue();
            Object[][] objArr = PcLayout.CHOICES;
            int length = objArr.length;
            int i3 = 0;
            while (i3 < length) {
                Object[] objArr2 = objArr[i3];
                final int iIntValue2 = ((Integer) objArr2[0]).intValue();
                final String str = (String) objArr2[1];
                Button button2 = iIntValue2 == iIntValue ? Ui.button(this, str, null) : Ui.ghostButton(this, str, null);
                button2.setTextSize(f);
                final int i4 = iIntValue;
                button2.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda16
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        ControlsEditorActivity.this.lambda$showMini$18(iIntValue2, i4, list, str, view);
                    }
                });
                gridLayout.addView(button2);
                i3++;
                length = length;
                f = 11.0f;
            }
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(gridLayout);
            this.mini.addView(scrollView, new LinearLayout.LayoutParams(-1, Ui.dp(this, 170.0f)));
            this.mini.addView(Ui.styledButton(this, "Удалить кнопку", new View.OnClickListener() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda17
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    ControlsEditorActivity.this.lambda$showMini$19(list, view);
                }
            }, Color.argb(60, 255, 100, 90), Ui.RED, Ui.RED));
        }
        this.root.addView(this.mini, new FrameLayout.LayoutParams(Ui.dp(this, 340.0f), -2));
        final float x = screenKeyStateOf.getX() * this.root.getWidth();
        final float y = screenKeyStateOf.getY() * this.root.getHeight();
        this.mini.post(new Runnable() { // from class: com.wastelanddroid.controls.ControlsEditorActivity$$ExternalSyntheticLambda18
            @Override // java.lang.Runnable
            public final void run() {
                ControlsEditorActivity.this.lambda$showMini$20(x, y);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$13(View view) {
        closeMini();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$14(List list, int i) {
        this.keys.resize(list, i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$15(List list, int i) {
        this.keys.setOpacity(list, i / 100.0f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$16(List list, int i, View view) {
        this.keys.setShape(list, i);
        showMini(list);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$17(List list, CompoundButton compoundButton, boolean z) {
        this.keys.setToggle(list, z);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$18(int i, int i2, List list, String str, View view) {
        if (this.keys.stateOf(Collections.singletonList(Integer.valueOf(i))) != null && i != i2) {
            Toast.makeText(this, "Такая кнопка уже есть", 0).show();
            return;
        }
        ScreenKey codes = this.keys.setCodes(list, Collections.singletonList(Integer.valueOf(i)));
        this.keys.setText(Collections.singletonList(Integer.valueOf(i)), shortLabel(str));
        showMini(codes != null ? codes.getCodes() : Collections.singletonList(Integer.valueOf(i)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$19(List list, View view) {
        this.keys.remove(list);
        closeMini();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showMini$20(float f, float f2) {
        float fDp = Ui.dp(this, 60.0f);
        this.mini.setX(clamp(f < ((float) this.root.getWidth()) / 2.0f ? f + fDp : (f - fDp) - this.mini.getWidth(), Ui.dp(this, 8.0f), (this.root.getWidth() - this.mini.getWidth()) - Ui.dp(this, 8.0f)));
        this.mini.setY(clamp(f2 - (this.mini.getHeight() / 2.0f), Ui.dp(this, 8.0f), (this.root.getHeight() - this.mini.getHeight()) - Ui.dp(this, 8.0f)));
        Anim.enter(this.mini, 0L);
    }

    private static String shortLabel(String str) {
        int iIndexOf = str.indexOf(8212);
        String strTrim = iIndexOf > 0 ? str.substring(0, iIndexOf).trim() : str;
        return (!strTrim.toLowerCase().contains("мышь") || iIndexOf <= 0) ? strTrim : str.substring(iIndexOf + 1).trim();
    }

    private static float clamp(float f, float f2, float f3) {
        return Math.max(f2, Math.min(Math.max(f2, f3), f));
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        ScreenKeyStore.save(this, ScreenKeyStore.decode(this.keys.export()));
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            Ui.immersive(this);
        }
    }
}
