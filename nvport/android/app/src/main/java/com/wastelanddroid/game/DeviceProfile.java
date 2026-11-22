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

    /**
     * The device's class for the native engine, by SoC (GPU power), RAM second:
     * HIGH: Snapdragon 8 Gen 3 / 8s Gen 3 / 8 Elite / 8 Gen 5 / 8 Elite Gen 5, Dimensity 9300+,
     * Exynos 2500; MEDIUM: Snapdragon 8 Gen 1..2, 8+ Gen 1, 888, 7+ Gen 3, Dimensity 8200..8400,
     * Exynos 2400, Tensor G3..G5; LOW: the rest (Helio G99, Snapdragon 6/7 Gen x, Dimensity 7xxx).
     */
    public static Quality autoQuality(Context context) {
        String soc = (socModel() + " " + Build.HARDWARE + " " + Build.BOARD).toLowerCase(Locale.ROOT);
        long ram = totalRamMb(context);
        Quality q;
        if (soc.matches(".*(sm8[6-9][0-9]{2}|sm8635|sm8735|mt699[0-9]|mt6989|s5e9955|8 elite).*")) {
            q = Quality.HIGH;
        } else if (soc.matches(".*(sm8[3-5][0-9]{2}|sm7675|sm7750|mt689[6-9]|s5e9945|zuma|laguna|tensor g[3-5]).*")) {
            q = Quality.MEDIUM;
        } else {
            q = Quality.LOW;
        }
        // The engine and the GPU share the RAM: less than 8 GB holds a class back.
        if (q == Quality.HIGH && ram < 7000) q = Quality.MEDIUM;
        if (q == Quality.MEDIUM && ram < 5500) q = Quality.LOW;
        return q;
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
