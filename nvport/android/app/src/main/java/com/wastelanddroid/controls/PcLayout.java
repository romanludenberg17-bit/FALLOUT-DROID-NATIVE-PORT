package com.wastelanddroid.controls;

import android.content.Context;
import com.wastelanddroid.input.ScreenKey;
import com.wastelanddroid.input.ScreenKeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PcLayout {
    private static final int A = 30;
    private static final int CAPS = 58;
    private static final int D = 32;
    private static final int E = 18;
    private static final int ESC = 1;
    private static final int F = 33;
    private static final int F9 = 67;
    private static final int K1 = 2;
    private static final int K2 = 3;
    private static final int K3 = 4;
    private static final int LAYOUT_VERSION = 4;
    private static final int LCTRL = 29;
    private static final int LSHIFT = 42;
    private static final int R = 19;
    private static final int S = 31;
    private static final int SPACE = 57;
    private static final int T = 20;
    private static final int TAB = 15;
    private static final int V = 47;
    private static final int W = 17;
    private static final int Z = 44;
    public static final int BTN_LEFT = 272;
    public static final int BTN_RIGHT = 273;
    public static final int BTN_MIDDLE = 274;
    private static final int F5 = 63;
    public static final Object[][] CHOICES = {new Object[]{Integer.valueOf(BTN_LEFT), "Левая мышь — огонь"}, new Object[]{Integer.valueOf(BTN_RIGHT), "Правая мышь — прицел"}, new Object[]{Integer.valueOf(BTN_MIDDLE), "Средняя мышь"}, new Object[]{18, "E — действие"}, new Object[]{19, "R — перезарядка"}, new Object[]{57, "Пробел — прыжок"}, new Object[]{29, "Ctrl — присед"}, new Object[]{42, "Shift — бег"}, new Object[]{58, "Caps — шаг"}, new Object[]{15, "Tab — Пип-бой"}, new Object[]{47, "V — V.A.T.S."}, new Object[]{33, "F — вид от 1/3 лица"}, new Object[]{44, "Z — взять предмет"}, new Object[]{20, "T — ожидание"}, new Object[]{1, "Esc — меню"}, new Object[]{Integer.valueOf(F5), "F5 — быстрое сохранение"}, new Object[]{67, "F9 — быстрая загрузка"}, new Object[]{2, "1 — слот 1"}, new Object[]{3, "2 — слот 2"}, new Object[]{4, "3 — слот 3"}, new Object[]{5, "4 — слот 4"}, new Object[]{6, "5 — слот 5"}, new Object[]{7, "6 — слот 6"}, new Object[]{8, "7 — слот 7"}, new Object[]{9, "8 — слот 8"}, new Object[]{17, "W — вперёд"}, new Object[]{31, "S — назад"}, new Object[]{30, "A — влево"}, new Object[]{32, "D — вправо"}, new Object[]{28, "Enter — ввод"}, new Object[]{41, "~ — консоль"}};

    public static boolean isMouse(int i) {
        return i >= 272 && i <= 279;
    }

    private PcLayout() {
    }

    private static ScreenKey key(int i, float f, float f2, int i2, String str, int i3, boolean z) {
        return new ScreenKey(Collections.singletonList(Integer.valueOf(i)), f, f2, i2, "", str, "", 1.0f, i3, z, true);
    }

    private static int sz(Context context, float f) {
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return Math.round(f * (Math.min(dm.widthPixels, dm.heightPixels) / dm.density));
    }

    private static ScreenKey key(Context context, int i, float f, float f2, float f3, String str, int i2) {
        return key(i, f, f2, sz(context, f3), str, i2, false);
    }

    public static List<ScreenKey> defaults(Context context) {
        ArrayList arrayList = new ArrayList();
        arrayList.add(new ScreenKey(Arrays.asList(17, 0, 32, 0, 31, 0, 30, 0), 0.14f, 0.7f, sz(context, 0.34f), "", "", "", 1.0f, 6, false, true));
        arrayList.add(key(context, BTN_LEFT, 0.9f, 0.68f, 0.21f, "ОГОНЬ", 2));
        arrayList.add(key(context, BTN_RIGHT, 0.92f, 0.36f, 0.14f, "ПРИЦЕЛ", 2));
        arrayList.add(key(context, 18, 0.76f, 0.86f, 0.14f, "ВЗЯТЬ", 2));
        arrayList.add(key(context, 57, 0.775f, 0.56f, 0.13f, "ПРЫЖОК", 2));
        arrayList.add(key(context, 19, 0.655f, 0.88f, 0.12f, "ПЕРЕЗ.", 2));
        arrayList.add(key(context, 47, 0.66f, 0.66f, 0.12f, "V.A.T.S.", 2));
        arrayList.add(key(context, 29, 0.05f, 0.4f, 0.12f, "ПРИСЕСТЬ", 2));
        arrayList.add(key(context, 58, 0.05f, 0.22f, 0.1f, "БЕГ/ШАГ", 2));
        arrayList.add(key(context, 1, 0.06f, 0.08f, 0.075f, "МЕНЮ", 0));
        arrayList.add(key(context, F5, 0.18f, 0.08f, 0.075f, "СОХР", 0));
        arrayList.add(key(context, 67, 0.3f, 0.08f, 0.075f, "ЗАГР", 0));
        arrayList.add(key(context, 20, 0.42f, 0.08f, 0.075f, "ЖДАТЬ", 0));
        arrayList.add(key(context, 33, 0.78f, 0.08f, 0.075f, "ВИД", 0));
        arrayList.add(key(context, 15, 0.9f, 0.08f, 0.075f, "ПИП-БОЙ", 0));
        for (int i = 1; i <= 8; i++) {
            arrayList.add(key(context, i + 1, (i * 0.045f) + 0.24f, 0.94f, 0.08f, String.valueOf(i), 2));
        }
        return arrayList;
    }

    /** Installs the default PC layout when no layout is saved yet. */
    public static void installIfEmpty(Context context) {
        if (ScreenKeyStore.load(context).isEmpty()) {
            ScreenKeyStore.save(context, defaults(context));
        }
    }
}
