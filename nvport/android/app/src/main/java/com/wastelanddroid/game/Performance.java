package com.wastelanddroid.game;

import android.app.Activity;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.PowerManager;
import android.os.Process;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

public final class Performance {
    private Performance() {
    }

    public static void apply(Activity activity) throws SecurityException, IllegalArgumentException {
        Display defaultDisplay;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        try {
            defaultDisplay = activity.getDisplay();
        } catch (RuntimeException unused) {
            defaultDisplay = activity.getWindowManager().getDefaultDisplay();
        }
        if (defaultDisplay != null) {
            Display.Mode mode = defaultDisplay.getMode();
            Display.Mode[] supportedModes = defaultDisplay.getSupportedModes();
            int length = supportedModes.length;
            Display.Mode mode2 = null;
            int i = 0;
            while (true) {
                if (i >= length) {
                    break;
                }
                Display.Mode mode3 = supportedModes[i];
                if (mode3.getPhysicalWidth() == mode.getPhysicalWidth() && mode3.getPhysicalHeight() == mode.getPhysicalHeight()) {
                    if (Math.abs(mode3.getRefreshRate() - 60.0f) < 1.0f) {
                        mode2 = mode3;
                        break;
                    } else if (mode2 == null && Math.abs(mode3.getRefreshRate() % 60.0f) < 1.0f) {
                        mode2 = mode3;
                    }
                }
                i++;
            }
            if (mode2 != null) {
                attributes.preferredDisplayModeId = mode2.getModeId();
            }
            attributes.preferredRefreshRate = 60.0f;
        }
        attributes.flags |= 128;
        activity.getWindow().setAttributes(attributes);
        try {
            PowerManager powerManager = (PowerManager) activity.getSystemService("power");
            if (powerManager != null && powerManager.isSustainedPerformanceModeSupported()) {
                activity.getWindow().setSustainedPerformanceMode(true);
            }
        } catch (RuntimeException unused2) {
        }
        Process.setThreadPriority(-4);
    }

    public static void setSurfaceFrameRate(Surface surface) {
        if (Build.VERSION.SDK_INT < 30 || surface == null || !surface.isValid()) {
            return;
        }
        surface.setFrameRate(60.0f, 1);
    }

    public static PerformanceHintManager.Session hintSession(Activity activity, int[] iArr) {
        PerformanceHintManager performanceHintManager;
        if (Build.VERSION.SDK_INT < 31 || (performanceHintManager = (PerformanceHintManager) activity.getSystemService(PerformanceHintManager.class)) == null || iArr.length == 0) {
            return null;
        }
        return performanceHintManager.createHintSession(iArr, 16666667L);
    }
}
