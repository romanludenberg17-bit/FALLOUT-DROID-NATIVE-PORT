package com.wastelanddroid.engine;

import android.content.Context;

/**
 * Mouse into the game. Fallout Droid 1.x wrote XTEST commands into the container's X server;
 * the native build calls the engine directly ({@link NativeInput}). API kept for the 1.x code.
 */
public final class XMouse {
    private XMouse() {}

    /** X11 button number for an evdev mouse code (1 left, 2 middle, 3 right). */
    public static int x11Button(int evdev) {
        if (evdev != 273) return evdev != 274 ? 1 : 2;
        return 3;
    }

    public static boolean ready(Context context) {
        return true;
    }

    public static void move(Context context, int dx, int dy) {
        if (dx != 0 || dy != 0) NativeInput.mouseMove(dx, dy);
    }

    public static void moveTo(Context context, int x, int y) {
        NativeInput.pointTo(x, y);
    }

    /** @param button X11 button: 1 left, 2 middle, 3 right. */
    public static void button(Context context, int button, boolean down) {
        int code = button == 3 ? 273 : button == 2 ? 274 : 272;
        NativeInput.key(code, down);
    }

    public static void wheel(Context context, int clicks) {
        if (clicks != 0) NativeInput.scroll(clicks);
    }
}
