package com.wastelanddroid.input;

/** Short names of evdev codes for key captions. */
public final class KeyNames {
    private KeyNames() {}

    public static String name(int code) {
        switch (code) {
            case 272: return "ЛКМ";
            case 273: return "ПКМ";
            case 274: return "СКМ";
            case 1: return "Esc";
            case 15: return "Tab";
            case 28: return "Enter";
            case 57: return "Space";
            case 29: return "Ctrl";
            case 42: return "Shift";
            case 56: return "Alt";
            case 58: return "Caps";
            case 41: return "~";
            case 14: return "Bksp";
            default:
        }
        if (code >= 2 && code <= 10) return String.valueOf(code - 1);
        if (code == 11) return "0";
        if (code >= 59 && code <= 68) return "F" + (code - 58);
        if (code == 87) return "F11";
        if (code == 88) return "F12";
        String row1 = "QWERTYUIOP", row2 = "ASDFGHJKL", row3 = "ZXCVBNM";
        if (code >= 16 && code <= 25) return String.valueOf(row1.charAt(code - 16));
        if (code >= 30 && code <= 38) return String.valueOf(row2.charAt(code - 30));
        if (code >= 44 && code <= 50) return String.valueOf(row3.charAt(code - 44));
        return "#" + code;
    }
}
