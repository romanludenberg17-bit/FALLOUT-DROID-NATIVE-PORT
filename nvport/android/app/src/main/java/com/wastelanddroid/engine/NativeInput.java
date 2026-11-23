package com.wastelanddroid.engine;

/**
 * Input into the native engine (libnvforever.so, android_input.rs). Codes are Linux evdev
 * codes (the codes Fallout Droid layouts use): 17 = W, 57 = Space, 272/273/274 = mouse
 * left/right/middle. Calls are thread-safe and cheap (a queue drained once per frame).
 */
public final class NativeInput {
    private NativeInput() {}

    public static native void key(int evdevCode, boolean down);

    /** One character typed into the console bar (Android's keyboard commits text, not keys). */
    public static native void typeChar(int codePoint);

    /** The render height the player picked in the game's settings (0 the screen's own), or -1. */
    public static native int renderHeightAsk();

    /** The engine's frame time, smoothed (ms; 0 not known yet). */
    public static native float frameMs();

    /** The game's Dynamic Resolution switch. */
    public static native boolean dynamicResolution();

    /** A whole line for the console: its characters, then Enter. */
    public static void typeLine(String line) {
        try {
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                typeChar(cp);
                i += Character.charCount(cp);
            }
            key(28, true);
            key(28, false);
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    /** Relative mouse motion in pixels (look). */
    public static native void mouseMove(float dx, float dy);

    /** Absolute cursor position in surface pixels (menus, Pip-Boy). Use {@link #pointTo}. */
    private static native void cursorTo(float x, float y);

    /**
     * What the game shows: 0 not known yet, 1 playing, 2 a menu / Pip-Boy / conversation /
     * message box / pause menu (the pointer is free), 3 a film or a scene (no pointer).
     */
    public static native int uiState();

    /** Bit 0 a weapon is drawn; bits 1..5 fighting, sneaking, movement, Pip-Boy, POV locked by scripts. */
    public static native int playFlags();

    /** The engine's frame-rate cap, live (0: none): the in-game panel's FPS limit. */
    public static native void setFrameCap(int fps);

    /** The engine asked for a fresh run (loading a save, back to the main menu): the
     * arguments file holds its command line. */
    public static native boolean restartRequested();

    /** The engine's main() returned (Quit in its menu, or an error): back to the launcher. */
    public static native boolean engineExited();

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

    private static native boolean quickLootHit(float x, float y);

    private static native void quickLootTap(float x, float y);

    /** The Quick Loot list shows and the point (view pixels) is on it. */
    public static boolean quickLootAt(float x, float y) {
        try {
            return quickLootHit(x * scaleX, y * scaleY);
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /** A tap on the Quick Loot list (view pixels): the row under it is taken. */
    public static void quickLootTapAt(float x, float y) {
        try {
            quickLootTap(x * scaleX, y * scaleY);
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    /** Wheel, in lines (positive = up). */
    public static native void scroll(float lines);

    /** Thermal Adaptive Mode (0 Normal .. 3 Critical), the system's status and headroom (-1 unknown). */
    public static native void thermal(int mode, int status, float headroom);
}
