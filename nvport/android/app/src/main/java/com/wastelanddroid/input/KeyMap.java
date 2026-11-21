package com.wastelanddroid.input;

import android.util.SparseIntArray;
import android.view.KeyEvent;

/** Key code tables: Android KeyEvent / Windows VK -> Linux evdev (the engine's codes). */
public final class KeyMap {
    private static final SparseIntArray ANDROID = new SparseIntArray();
    private static final SparseIntArray VK = new SparseIntArray();

    private KeyMap() {}

    private static void a(int android, int evdev) { ANDROID.put(android, evdev); }
    private static void v(int vk, int evdev) { VK.put(vk, evdev); }

    static {
        // letters
        int[] letters = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
        for (int i = 0; i < 26; i++) {
            a(KeyEvent.KEYCODE_A + i, letters[i]);
            v('A' + i, letters[i]);
        }
        // digits: evdev 2..10 = 1..9, 11 = 0
        for (int d = 0; d <= 9; d++) {
            int ev = d == 0 ? 11 : d + 1;
            a(KeyEvent.KEYCODE_0 + d, ev);
            v('0' + d, ev);
        }
        // function keys F1..F10 = 59..68, F11 = 87, F12 = 88
        for (int f = 0; f < 12; f++) {
            int ev = f < 10 ? 59 + f : (f == 10 ? 87 : 88);
            a(KeyEvent.KEYCODE_F1 + f, ev);
            v(0x70 + f, ev);
        }
        a(KeyEvent.KEYCODE_ESCAPE, 1);      v(0x1B, 1);
        a(KeyEvent.KEYCODE_TAB, 15);        v(0x09, 15);
        a(KeyEvent.KEYCODE_ENTER, 28);      v(0x0D, 28);
        a(KeyEvent.KEYCODE_SPACE, 57);      v(0x20, 57);
        a(KeyEvent.KEYCODE_DEL, 14);        v(0x08, 14);
        a(KeyEvent.KEYCODE_FORWARD_DEL, 111); v(0x2E, 111);
        a(KeyEvent.KEYCODE_SHIFT_LEFT, 42); v(0x10, 42); v(0xA0, 42);
        a(KeyEvent.KEYCODE_SHIFT_RIGHT, 54); v(0xA1, 54);
        a(KeyEvent.KEYCODE_CTRL_LEFT, 29);  v(0x11, 29); v(0xA2, 29);
        a(KeyEvent.KEYCODE_CTRL_RIGHT, 97); v(0xA3, 97);
        a(KeyEvent.KEYCODE_ALT_LEFT, 56);   v(0x12, 56); v(0xA4, 56);
        a(KeyEvent.KEYCODE_ALT_RIGHT, 100); v(0xA5, 100);
        a(KeyEvent.KEYCODE_CAPS_LOCK, 58);  v(0x14, 58);
        a(KeyEvent.KEYCODE_GRAVE, 41);      v(0xC0, 41);
        a(KeyEvent.KEYCODE_MINUS, 12);      v(0xBD, 12);
        a(KeyEvent.KEYCODE_EQUALS, 13);     v(0xBB, 13);
        a(KeyEvent.KEYCODE_LEFT_BRACKET, 26); v(0xDB, 26);
        a(KeyEvent.KEYCODE_RIGHT_BRACKET, 27); v(0xDD, 27);
        a(KeyEvent.KEYCODE_BACKSLASH, 43);  v(0xDC, 43);
        a(KeyEvent.KEYCODE_SEMICOLON, 39);  v(0xBA, 39);
        a(KeyEvent.KEYCODE_APOSTROPHE, 40); v(0xDE, 40);
        a(KeyEvent.KEYCODE_COMMA, 51);      v(0xBC, 51);
        a(KeyEvent.KEYCODE_PERIOD, 52);     v(0xBE, 52);
        a(KeyEvent.KEYCODE_SLASH, 53);      v(0xBF, 53);
        a(KeyEvent.KEYCODE_DPAD_UP, 103);   v(0x26, 103);
        a(KeyEvent.KEYCODE_DPAD_DOWN, 108); v(0x28, 108);
        a(KeyEvent.KEYCODE_DPAD_LEFT, 105); v(0x25, 105);
        a(KeyEvent.KEYCODE_DPAD_RIGHT, 106); v(0x27, 106);
        a(KeyEvent.KEYCODE_MOVE_HOME, 102); v(0x24, 102);
        a(KeyEvent.KEYCODE_MOVE_END, 107);  v(0x23, 107);
        a(KeyEvent.KEYCODE_PAGE_UP, 104);   v(0x21, 104);
        a(KeyEvent.KEYCODE_PAGE_DOWN, 109); v(0x22, 109);
        a(KeyEvent.KEYCODE_INSERT, 110);    v(0x2D, 110);
        // mouse buttons as VK
        v(0x01, 272); v(0x02, 273); v(0x04, 274);
    }

    /** Android KeyEvent code -> evdev, or 0. */
    public static int toEvdev(int androidKeyCode) {
        return ANDROID.get(androidKeyCode, 0);
    }

    /** Windows virtual-key -> evdev, or 0. */
    public static int vkToEvdev(int vk) {
        return VK.get(vk, 0);
    }
}
