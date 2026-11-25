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
    /**
     * The weakest GPUs met (Mali-G52/G57 MC1-2, PowerVR GE8320: Helio G85/G88/G80/G35/P35,
     * Unisoc T606-T616): 8 FPS at 480p indoors in testers' reports, 360p for them.
     */
    public static boolean veryLow() {
        String soc = (socModel() + " " + Build.HARDWARE + " " + Build.BOARD).toLowerCase(Locale.ROOT);
        return soc.matches(".*(mt676[0-9]|mt6769|mt6768|mt6762|mt6765|ums9230|ums512|t606|t610|t612|t615|t616|t618|sc9863).*");
    }

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

    /** The chip's market name from its SoC model ("SM7325" -> "Snapdragon 778G"); the raw model if unknown. */
    public static String chipName() {
        String raw = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : Build.HARDWARE;
        String key = (raw + " " + Build.HARDWARE + " " + Build.BOARD).toLowerCase(Locale.ROOT);
        String[][] table = {
            {"sm8850", "Snapdragon 8 Elite Gen 5"}, {"sm8845", "Snapdragon 8 Gen 5"}, {"sm8750", "Snapdragon 8 Elite"},
            {"sm8735", "Snapdragon 8s Gen 4"}, {"sm8650", "Snapdragon 8 Gen 3"}, {"sm8635", "Snapdragon 8s Gen 3"},
            {"sm8550", "Snapdragon 8 Gen 2"}, {"sm8475", "Snapdragon 8+ Gen 1"}, {"sm8450", "Snapdragon 8 Gen 1"},
            {"sm8350", "Snapdragon 888"}, {"sm8250", "Snapdragon 865"}, {"sm8150", "Snapdragon 855"},
            {"sm7750", "Snapdragon 7 Gen 4"}, {"sm7675", "Snapdragon 7+ Gen 3"}, {"sm7635", "Snapdragon 7s Gen 3"},
            {"sm7550", "Snapdragon 7 Gen 3"}, {"sm7475", "Snapdragon 7+ Gen 2"}, {"sm7450", "Snapdragon 7 Gen 1"},
            {"sm7435", "Snapdragon 7s Gen 2"}, {"sm7350", "Snapdragon 780G"}, {"sm7325", "Snapdragon 778G"},
            {"sm7250", "Snapdragon 765G"}, {"sm7225", "Snapdragon 750G"}, {"sm7150", "Snapdragon 730G"},
            {"sm6450", "Snapdragon 6 Gen 1"}, {"sm6375", "Snapdragon 695"}, {"sm6225", "Snapdragon 680"},
            {"sm6150", "Snapdragon 675"}, {"sm4450", "Snapdragon 4 Gen 2"}, {"sm4375", "Snapdragon 4 Gen 1"},
            {"mt6991", "Dimensity 9400"}, {"mt6989", "Dimensity 9300"}, {"mt6985", "Dimensity 9200"},
            {"mt6983", "Dimensity 9000"}, {"mt6899", "Dimensity 8400"}, {"mt6897", "Dimensity 8300"},
            {"mt6896", "Dimensity 8200"}, {"mt6895", "Dimensity 8100"}, {"mt6893", "Dimensity 1200"},
            {"mt6891", "Dimensity 1100"}, {"mt6886", "Dimensity 7200"}, {"mt6878", "Dimensity 7300"},
            {"mt6877", "Dimensity 1080"}, {"mt6855", "Dimensity 7020"}, {"mt6853", "Dimensity 720"},
            {"mt6835", "Dimensity 6100+"}, {"mt6833", "Dimensity 700"}, {"mt6789v", "Helio G100"}, {"mt6789", "Helio G99"},
            {"mt6785", "Helio G95"}, {"mt6781", "Helio G96"}, {"mt6769", "Helio G85"}, {"mt6768", "Helio G85"},
            {"mt6765", "Helio G35"}, {"mt6762", "Helio P22"},
            {"s5e9955", "Exynos 2500"}, {"s5e9945", "Exynos 2400"}, {"s5e9925", "Exynos 2200"},
            {"s5e8845", "Exynos 1480"}, {"s5e8835", "Exynos 1380"}, {"s5e8825", "Exynos 1280"},
            {"laguna", "Tensor G5"}, {"zumapro", "Tensor G4"}, {"zuma", "Tensor G3"}, {"gs201", "Tensor G2"}, {"gs101", "Tensor"},
            {"ums9230", "Unisoc T606"}, {"t618", "Unisoc T618"}, {"t616", "Unisoc T616"},
        };
        for (String[] r : table) if (key.contains(r[0])) return r[1];
        return raw == null || raw.isEmpty() || raw.equals("unknown") ? Build.HARDWARE : raw;
    }

    /**
     * Body and clothing replacers (more polygons and larger skin textures on every actor):
     * Snapdragon 7+ Gen 2 and stronger only.
     */
    public static boolean replacersAllowed() {
        String key = ((Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "") + " " + Build.HARDWARE + " " + Build.BOARD).toLowerCase(Locale.ROOT);
        return key.matches(".*(sm7475|sm7550|sm7675|sm7750|sm8450|sm8475|sm8550|sm8635|sm8650|sm8735|sm8750|sm8845|sm8850).*");
    }

    /**
     * The Galaxy S25 Ultra (SM-S938*, Snapdragon 8 Elite, 12 GB): the owner's flagship
     * profile, every effect on at 1080p (FalloutEngine.Profile).
     */
    public static boolean flagshipProfile() {
        String m = Build.MODEL == null ? "" : Build.MODEL.toUpperCase(Locale.ROOT);
        return m.startsWith("SM-S938");
    }

    /** The GPU family of the chip, for the launcher. */
    public static String gpuName() {
        String c = chipName();
        String m = (Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER : Build.HARDWARE).toLowerCase(Locale.ROOT);
        if (c.startsWith("Snapdragon") || m.contains("qti") || m.contains("qualcomm")) return "Adreno";
        if (c.startsWith("Exynos 2")) return "Xclipse";
        if (c.startsWith("Helio G35") || c.startsWith("Helio P22")) return "PowerVR";
        return "Mali";
    }

    static String socModel() {
        return Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL : Build.HARDWARE;
    }

    public static long totalRamMb(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService("activity");
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem / 1048576L;
    }
}
