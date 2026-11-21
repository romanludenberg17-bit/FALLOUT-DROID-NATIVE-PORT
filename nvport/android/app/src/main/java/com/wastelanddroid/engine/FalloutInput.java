package com.wastelanddroid.engine;

import android.content.Context;
import android.content.SharedPreferences;

public final class FalloutInput {
    public static final String BTN_LABELS = "btn_labels";
    public static final String BTN_OPACITY = "btn_opacity";
    public static final String BTN_SCALE = "btn_scale";
    public static final String DEBUG = "input_debug";
    public static final String FIRE_LOOK = "fire_look";
    public static final String GRAB_CURSOR = "grab_cursor";
    public static final String HAPTICS = "key_haptics";
    public static final String INVERT_Y = "invert_y";
    public static final String LOOK_RIGHT = "look_right_half";
    public static final String LOOK_SENS = "look_sensitivity";
    private static final String PREFS = "wasteland";
    public static final String SHOW_CURSOR = "show_cursor";
    public static final String TAP_FIRES = "tap_fires";

    private FalloutInput() {
    }

    private static SharedPreferences p(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static boolean lookOnRightOnly(Context context) {
        return p(context).getBoolean(LOOK_RIGHT, false);
    }

    public static float lookSensitivity(Context context) {
        return p(context).getInt(LOOK_SENS, 115) / 100.0f;
    }

    public static boolean tapFires(Context context) {
        return p(context).getBoolean(TAP_FIRES, true);
    }

    public static boolean fireLook(Context context) {
        return p(context).getBoolean(FIRE_LOOK, true);
    }

    public static boolean invertY(Context context) {
        return p(context).getBoolean(INVERT_Y, false);
    }

    public static boolean showCursor(Context context) {
        return p(context).getBoolean(SHOW_CURSOR, true);
    }

    public static boolean haptics(Context context) {
        return p(context).getBoolean(HAPTICS, true);
    }

    public static boolean debug(Context context) {
        return p(context).getBoolean(DEBUG, false);
    }

    public static boolean grabCursor(Context context) {
        return p(context).getBoolean(GRAB_CURSOR, true);
    }
}
