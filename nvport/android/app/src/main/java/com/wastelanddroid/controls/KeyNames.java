package com.wastelanddroid.controls;

import java.util.ArrayList;

public final class KeyNames {
    public static final int[] CODES;
    public static final String[] NAMES;

    private KeyNames() {
    }

    static {
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        int i = 0;
        Object[][] objArr = {new Object[]{"ЛКМ", 1}, new Object[]{"ПКМ", 2}, new Object[]{"СКМ", 4}, new Object[]{"Пробел", 32}, new Object[]{"Enter", 13}, new Object[]{"Esc", 27}, new Object[]{"Tab", 9}, new Object[]{"Shift", 16}, new Object[]{"Ctrl", 17}, new Object[]{"Alt", 18}, new Object[]{"Caps Lock", 20}, new Object[]{"Backspace", 8}, new Object[]{"Вверх", 38}, new Object[]{"Вниз", 40}, new Object[]{"Влево", 37}, new Object[]{"Вправо", 39}, new Object[]{"`/~ (консоль)", 192}};
        for (int i2 = 0; i2 < 17; i2++) {
            Object[] objArr2 = objArr[i2];
            arrayList.add((String) objArr2[0]);
            arrayList2.add((Integer) objArr2[1]);
        }
        for (char c = 'A'; c <= 'Z'; c = (char) (c + 1)) {
            arrayList.add(String.valueOf(c));
            arrayList2.add(Integer.valueOf(c));
        }
        for (char c2 = '0'; c2 <= '9'; c2 = (char) (c2 + 1)) {
            arrayList.add(String.valueOf(c2));
            arrayList2.add(Integer.valueOf(c2));
        }
        for (int i3 = 1; i3 <= 12; i3++) {
            arrayList.add("F" + i3);
            arrayList2.add(Integer.valueOf(i3 + 111));
        }
        NAMES = (String[]) arrayList.toArray(new String[0]);
        CODES = new int[arrayList2.size()];
        for (; i < CODES.length; i++) {
            CODES[i] = ((Integer) arrayList2.get(i)).intValue();
        }
    }

    public static String name(int i) {
        int i2 = 0;
        while (true) {
            int[] iArr = CODES;
            if (i2 >= iArr.length) {
                return String.format("0x%02X", Integer.valueOf(i));
            }
            if (iArr[i2] == i) {
                return NAMES[i2];
            }
            i2++;
        }
    }

    public static int indexOf(int i) {
        int i2 = 0;
        while (true) {
            int[] iArr = CODES;
            if (i2 >= iArr.length) {
                return 0;
            }
            if (iArr[i2] == i) {
                return i2;
            }
            i2++;
        }
    }
}
