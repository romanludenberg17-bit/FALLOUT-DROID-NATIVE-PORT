package com.wastelanddroid.engine;

/**
 * Input into the native engine (libnvforever.so, android_input.rs). Codes are Linux evdev
 * codes (the codes Fallout Droid layouts use): 17 = W, 57 = Space, 272/273/274 = mouse
 * left/right/middle. Calls are thread-safe and cheap (a queue drained once per frame).
 */
public final class NativeInput {
    private NativeInput() {}

    public static native void key(int evdevCode, boolean down);

    /** Relative mouse motion in pixels (look). */
    public static native void mouseMove(float dx, float dy);

    /** Absolute cursor position in surface pixels (menus, Pip-Boy). Use {@link #pointTo}. */
    private static native void cursorTo(float x, float y);

    /**
     * What the game shows: 0 not known yet, 1 playing, 2 a menu / Pip-Boy / conversation /
     * message box / pause menu (the pointer is free), 3 a film or a scene (no pointer).
     */
    public static native int uiState();

    /** View pixels -> surface pixels (the surface is drawn smaller and scaled up: render scale). */
    private static volatile float scaleX = 1f, scaleY = 1f;

    public static void setViewToSurface(float sx, float sy) {
        scaleX = sx;
        scaleY = sy;
    }

    /** Absolute cursor position in view (screen) pixels. */
    public static void pointTo(float x, float y) {
        cursorTo(x * scaleX, y * scaleY);
    }

    /** Wheel, in lines (positive = up). */
    public static native void scroll(float lines);
}
