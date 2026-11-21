package com.wastelanddroid.runtime;

import android.util.Log;
import android.view.InputDevice;

public class InputMode {
    /* JADX WARN: Type inference failed for: r2v0, types: [long] */
    /* JADX WARN: Type inference failed for: r2v1, types: [long] */
    public static boolean isPhysicalGamepad(InputDevice inputDevice) {
        if (inputDevice == null || inputDevice.getId() <= 0 || inputDevice.isVirtual()) {
            return false;
        }
        int sources = inputDevice.getSources();
        return (sources & 1025) == 1025 || (sources & 16777232) == 16777232;
    }

    public static void log(String str) {
        Log.i("FalloutInputMode", str);
    }
}
