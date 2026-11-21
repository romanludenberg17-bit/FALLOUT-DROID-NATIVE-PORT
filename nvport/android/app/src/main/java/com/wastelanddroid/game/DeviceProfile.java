package com.wastelanddroid.game;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import java.util.Locale;

public final class DeviceProfile {
    public final Quality quality;
    public final int renderHeight;
    public final int renderWidth;
    public final String soc;
    public final long totalRamMb;

    public enum Quality {
        LOW,
        MEDIUM,
        HIGH
    }

    private DeviceProfile(Quality quality, int i, int i2, long j, String str) {
        this.quality = quality;
        this.renderWidth = i;
        this.renderHeight = i2;
        this.totalRamMb = j;
        this.soc = str;
    }

    public static Quality autoQuality(Context context) {
        String lowerCase = socModel().toLowerCase(Locale.ROOT);
        return (lowerCase.matches(".*sm8[6-9]\\d0.*") || lowerCase.contains("8 elite")) ? totalRamMb(context) >= 11000 ? Quality.HIGH : Quality.MEDIUM : lowerCase.matches(".*sm8(4|5)\\d0.*") ? Quality.MEDIUM : Quality.LOW;
    }

    public static DeviceProfile create(Context context, Quality quality, int i) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int iMax = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
        int iMin = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
        int i2 = AnonymousClass1.$SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality[quality.ordinal()];
        int iMin2 = (Math.min(i2 != 1 ? i2 != 2 ? 720 : 900 : 1080, iMin) * Math.max(50, Math.min(100, i))) / 100;
        return new DeviceProfile(quality, Math.round((iMin2 * iMax) / iMin) & (-2), iMin2 & (-2), totalRamMb(context), socModel());
    }

    /* renamed from: com.wastelanddroid.game.DeviceProfile$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality;

        static {
            int[] iArr = new int[Quality.values().length];
            $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality = iArr;
            try {
                iArr[Quality.HIGH.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality[Quality.MEDIUM.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
        }
    }

    static String socModel() {
        return Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL : Build.HARDWARE;
    }

    static long totalRamMb(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService("activity");
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem / 1048576L;
    }
}
