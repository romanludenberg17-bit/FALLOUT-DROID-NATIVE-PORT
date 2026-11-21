package com.wastelanddroid.controls;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.wastelanddroid.R;
import com.wastelanddroid.util.Ui;
import java.util.Map;
import java.util.Set;
import org.json.JSONException;

public final class GamepadEditor {
    private GamepadEditor() {
    }

    public static void show(final Activity activity) throws JSONException {
        final GamepadMapping gamepadMappingLoad = GamepadMapping.load(activity);
        LinearLayout linearLayoutColumn = Ui.column(activity);
        int iDp = Ui.dp(activity, 16.0f);
        int i = iDp / 2;
        linearLayoutColumn.setPadding(iDp, i, iDp, i);
        linearLayoutColumn.addView(Ui.withIcon(Ui.hint(activity, GamepadInput.anyConnected() ? "Геймпад подключён" : "Геймпад не обнаружен — подключите по Bluetooth или USB"), R.drawable.ic_gamepad, Ui.FG_DIM));
        linearLayoutColumn.addView(Ui.hint(activity, "Левый стик — ходьба, правый — обзор"));
        linearLayoutColumn.addView(Ui.slider(activity, "Скорость обзора, %", 20, 300, Math.round(gamepadMappingLoad.lookSpeed * 100.0f), new Ui.IntCallback() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda0
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i2) {
                gamepadMappingLoad.lookSpeed = i2 / 100.0f;
            }
        }));
        linearLayoutColumn.addView(Ui.slider(activity, "Мёртвая зона стиков, %", 5, 40, Math.round(gamepadMappingLoad.deadzone * 100.0f), new Ui.IntCallback() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda1
            @Override // com.wastelanddroid.util.Ui.IntCallback
            public final void on(int i2) {
                gamepadMappingLoad.deadzone = i2 / 100.0f;
            }
        }));
        CheckBox checkBoxCheck = Ui.check(activity, "Инвертировать ось Y", gamepadMappingLoad.invertY);
        checkBoxCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda2
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                gamepadMappingLoad.invertY = z;
            }
        });
        linearLayoutColumn.addView(checkBoxCheck);
        for (final Map.Entry<Integer, String> entry : GamepadMapping.BUTTON_NAMES.entrySet()) {
            final int iIntValue = entry.getKey().intValue();
            LinearLayout linearLayoutRow = Ui.row(activity);
            linearLayoutRow.addView(Ui.label(activity, entry.getValue()), Ui.weight());
            final Integer num = gamepadMappingLoad.keys.get(Integer.valueOf(iIntValue));
            final Button buttonGhostButton = Ui.ghostButton(activity, num == null ? "—" : KeyNames.name(num.intValue()), null);
            buttonGhostButton.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    Activity activity2 = activity;
                    Map.Entry entry2 = entry;
                    Integer num2 = num;
                    GamepadMapping gamepadMapping = gamepadMappingLoad;
                    int i2 = iIntValue;
                    Button button = buttonGhostButton;
                    new AlertDialog.Builder(activity2).setTitle((CharSequence) entry2.getValue()).setSingleChoiceItems(KeyNames.NAMES, num2 == null ? -1 : KeyNames.indexOf(gamepadMapping.keys.getOrDefault(Integer.valueOf(i2), 0).intValue()), new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda7
                        @Override // android.content.DialogInterface.OnClickListener
                        public final void onClick(DialogInterface dialogInterface, int i3) {
                            GamepadEditor.lambda$show$3(gamepadMapping, i2, button, dialogInterface, i3);
                        }
                    }).setNeutralButton("Не назначать", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda8
                        @Override // android.content.DialogInterface.OnClickListener
                        public final void onClick(DialogInterface dialogInterface, int i3) {
                            GamepadEditor.lambda$show$4(gamepadMapping, i2, button, dialogInterface, i3);
                        }
                    }).show();
                }
            });
            linearLayoutRow.addView(buttonGhostButton);
            CheckBox checkBoxCheck2 = Ui.check(activity, "фикс.", gamepadMappingLoad.toggles.contains(Integer.valueOf(iIntValue)));
            checkBoxCheck2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda4
                @Override // android.widget.CompoundButton.OnCheckedChangeListener
                public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                    GamepadEditor.lambda$show$6(gamepadMappingLoad, iIntValue, compoundButton, z);
                }
            });
            linearLayoutRow.addView(checkBoxCheck2);
            linearLayoutColumn.addView(linearLayoutRow);
        }
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(linearLayoutColumn);
        new AlertDialog.Builder(activity).setTitle("Геймпад").setView(scrollView).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda5
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                GamepadEditor.lambda$show$7(gamepadMappingLoad, activity, dialogInterface, i2);
            }
        }).setNeutralButton("По умолчанию", new DialogInterface.OnClickListener() { // from class: com.wastelanddroid.controls.GamepadEditor$$ExternalSyntheticLambda6
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
        try {
                GamepadMapping.defaults().save(activity);
                    } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    static /* synthetic */ void lambda$show$3(GamepadMapping gamepadMapping, int i, Button button, DialogInterface dialogInterface, int i2) {
        gamepadMapping.keys.put(Integer.valueOf(i), Integer.valueOf(KeyNames.CODES[i2]));
        button.setText(KeyNames.NAMES[i2]);
        dialogInterface.dismiss();
    }

    static /* synthetic */ void lambda$show$4(GamepadMapping gamepadMapping, int i, Button button, DialogInterface dialogInterface, int i2) {
        gamepadMapping.keys.remove(Integer.valueOf(i));
        button.setText("—");
    }

    static /* synthetic */ void lambda$show$6(GamepadMapping gamepadMapping, int i, CompoundButton compoundButton, boolean z) {
        Set<Integer> set = gamepadMapping.toggles;
        Integer numValueOf = Integer.valueOf(i);
        if (z) {
            set.add(numValueOf);
        } else {
            set.remove(numValueOf);
        }
    }

    static /* synthetic */ void lambda$show$7(GamepadMapping gamepadMapping, Activity activity, DialogInterface dialogInterface, int i) {
        try {
            gamepadMapping.save(activity);
            Toast.makeText(activity, "Раскладка геймпада сохранена", 0).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Ошибка: " + e.getMessage(), 1).show();
        }
    }
}
