package org.libsdl.app;

import java.util.ArrayList;
import java.util.List;

/** Test double of the SDL activity: records what the controls send to the game. */
public class SDLActivity extends android.app.Activity {
    public static final List<String> events = new ArrayList<>();

    public static void onNativeKeyDown(int keycode) { events.add("down " + keycode); }

    public static void onNativeKeyUp(int keycode) { events.add("up " + keycode); }

    public static void onNativeMouse(int button, int action, float x, float y, boolean relative) {
        events.add("mouse b" + button + " a" + action + (relative ? " rel " : " abs ") + Math.round(x) + "," + Math.round(y));
    }
}
