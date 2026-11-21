package com.wastelanddroid.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final String AUTOSAVE = "autosave";
    public static final String AUTOSTART = "autostart";
    public static final String AUTOSTART_BLOCKED = "autostart_blocked";
    public static final String CRASH_NOTICE = "crash_notice";
    public static final String DIRECT_AUDIO = "direct_audio";
    public static final String FIX_REGISTRY = "fix_registry";
    public static final String FPS_LOCK = "fps_lock";
    public static final String GAMEPAD = "gamepad_in_game";
    public static final String GAME_DIR = "game_dir";
    public static final String GAME_LOGS = "game_logs";
    public static final String LAA = "laa";
    public static final String LAST_GAME = "last_game";
    public static final String LAUNCH_PENDING = "launch_pending";
    public static final String OVERLAY_MODE = "overlay_mode";
    public static final String PIN_BIG_CORES = "pin_big_cores";
    public static final String QUALITY = "quality";
    public static final String RES_SCALE = "res_scale";
    public static final String SHOW_HUD = "show_hud";
    public static final String SKIP_INTRO = "skip_intro";
    public static final String USE_FOSE = "use_fose";
    public static final String WATCHDOG = "watchdog";

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences("wasteland", 0);
    }
}
