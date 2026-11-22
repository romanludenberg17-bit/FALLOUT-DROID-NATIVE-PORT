package com.wastelanddroid.engine;

/** Fallout 3's game activity: {@link NativeGameActivity} under its own theme (loading picture). */
public final class NativeGameActivityFo3 extends NativeGameActivity {
    /** The activity class that runs a game. */
    public static Class<? extends NativeGameActivity> forGame(String gameId) {
        return "fo3".equals(gameId) ? NativeGameActivityFo3.class : NativeGameActivity.class;
    }
}
