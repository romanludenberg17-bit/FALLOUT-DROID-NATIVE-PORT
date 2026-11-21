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

    /** Absolute cursor position in surface pixels (menus, Pip-Boy). */
    public static native void cursorTo(float x, float y);

    /** Wheel, in lines (positive = up). */
    public static native void scroll(float lines);
}
