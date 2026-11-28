package com.mafia2re.game;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the phone is: chip, GPU, memory, CPU features. Used to pick a graphics preset that
 * keeps the game playable (Helio G85 / Mali-G52 class up to flagship Adreno) and to refuse
 * launching on CPUs the recompiled game cannot run on.
 */
final class DeviceProfile {
    static final int TIER_LOW = 0, TIER_MID = 1, TIER_HIGH = 2, TIER_ULTRA = 3;

    final String soc;
    final String gpu;
    final long ramMiB;
    final int bigCoreMHz;
    final int cores;
    final boolean atomics;   // ARMv8.1 LSE
    final boolean lrcpc;     // ARMv8.3 RCpc (present on A55/A75/A76+)
    final int vulkanApi;     // 0 = none, else VK_MAKE_VERSION packed
    final int tier;
    final String gpuFamily;  // "adreno" / "mali" / "xclipse" / "powervr" / "other"

    private DeviceProfile(String soc, String gpu, long ramMiB, int bigCoreMHz, int cores,
                          boolean atomics, boolean lrcpc, int vulkanApi) {
        this.soc = soc;
        this.gpu = gpu;
        this.ramMiB = ramMiB;
        this.bigCoreMHz = bigCoreMHz;
        this.cores = cores;
        this.atomics = atomics;
        this.lrcpc = lrcpc;
        this.vulkanApi = vulkanApi;
        String g = gpu.toLowerCase(Locale.ROOT);
        if (g.contains("adreno")) gpuFamily = "adreno";
        else if (g.contains("mali") || g.contains("immortalis")) gpuFamily = "mali";
        else if (g.contains("xclipse") || g.contains("samsung")) gpuFamily = "xclipse";
        else if (g.contains("powervr") || g.contains("img ")) gpuFamily = "powervr";
        else gpuFamily = "other";
        this.tier = computeTier();
    }

    /** The recompiled game is built for ARMv8.2-class cores (LSE atomics + RCpc). */
    boolean cpuSupported() {
        return atomics && lrcpc;
    }

    boolean hasVulkan11() {
        return vulkanApi >= ((1 << 22) | (1 << 12));
    }

    boolean isAdreno() {
        return "adreno".equals(gpuFamily);
    }

    /**
     * The weakest GPUs the port runs on: Mali-G52/G51/G31 and older Bifrost (Helio G80/G85/G88,
     * Exynos 850), Adreno below 615, PowerVR. These get 480p in the Speed mode.
     */
    boolean entryGpu() {
        String g = gpu.toLowerCase(Locale.ROOT);
        if ("mali".equals(gpuFamily)) {
            int n = num(g, "g([0-9]{2,3})");
            return n == 52 || n == 51 || n == 31 || n == 71 || n == 72 || n == 76 || n < 0;
        }
        if (isAdreno()) return adrenoModel() > 0 && adrenoModel() < 615;
        return "powervr".equals(gpuFamily);
    }

    /** 4 for phones with about 4 GB of RAM (and less), 6 for about 6 GB, 8 for more. */
    int ramClass() {
        if (ramMiB <= 0) return 8;
        if (ramMiB < 4700) return 4;
        if (ramMiB < 7000) return 6;
        return 8;
    }

    /** One line for the launcher: what the automatic profile does on this phone. */
    String profileNote() {
        StringBuilder sb = new StringBuilder(tierName(false));
        if (entryGpu()) sb.append(" · слабый GPU: 480p в «Скорости»");
        if (ramClass() == 4) sb.append(" · 4 ГБ: экономия памяти");
        else if (ramClass() == 6) sb.append(" · 6 ГБ: средние текстуры");
        if (!isAdreno()) sb.append(" · без DXT: 16-бит распаковка");
        return sb.toString();
    }

    /** 650, 740, 829...; 0 when not an Adreno or unknown. */
    int adrenoModel() {
        return isAdreno() ? Math.max(0, num(gpu.toLowerCase(Locale.ROOT), "adreno[^0-9]*([0-9]{3})")) : 0;
    }



    private static int num(String s, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(s);
        if (!m.find()) return -1;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int gpuTier() {
        String g = gpu.toLowerCase(Locale.ROOT);
        switch (gpuFamily) {
            case "adreno": {
                int n = num(g, "adreno[^0-9]*([0-9]{3})");
                if (n < 0) return TIER_LOW;
                if (n >= 800) return n >= 825 ? TIER_ULTRA : TIER_HIGH;  // 8xx: SD 8 Elite and newer
                if (n >= 730) return TIER_HIGH;                          // SD 8 Gen 1..3
                if (n >= 710) return TIER_MID;                           // 7 Gen x
                if (n >= 640) return TIER_MID;                           // 640/642L/650/660
                if (n >= 618) return TIER_LOW;                           // 618/619/620
                return TIER_LOW;                                         // 5xx/6xx entry
            }
            case "mali": {
                if (g.contains("immortalis")) return TIER_HIGH;
                int n = num(g, "g([0-9]{2,3})");
                int mc = num(g, "mc([0-9]+)");
                if (n < 0) return TIER_LOW;
                if (n >= 710 || n == 78 || n == 77) return n >= 715 ? TIER_HIGH : TIER_MID;  // G710/G715/G720, G77/G78
                if (n >= 610) return TIER_MID;                           // G610/G615 (Dimensity 7xxx/8xxx)
                if (n == 68 || n == 57) return mc >= 4 ? TIER_MID : TIER_LOW;
                return TIER_LOW;                                         // G52 (Helio G85..G99), G51, G31, G7x
            }
            case "xclipse":
                return num(g, "xclipse[^0-9]*([0-9]{3})") >= 940 ? TIER_HIGH : TIER_MID;
            default:
                return TIER_LOW;
        }
    }

    private int computeTier() {
        int t = gpuTier();
        if (ramMiB > 0 && ramMiB < 4700) t = Math.min(t, TIER_LOW);       // 4 GB phones
        else if (ramMiB > 0 && ramMiB < 7000) t = Math.min(t, TIER_MID);  // 6 GB phones
        if (bigCoreMHz > 0 && bigCoreMHz < 2050) t = Math.min(t, TIER_LOW);
        return t;
    }

    String tierName(boolean en) {
        switch (tier) {
            case TIER_ULTRA: return en ? "Flagship" : "Флагман";
            case TIER_HIGH: return en ? "High" : "Высокий";
            case TIER_MID: return en ? "Mid-range" : "Средний";
            default: return en ? "Entry (Helio G85 class)" : "Начальный (класс Helio G85)";
        }
    }

    // ------------------------------------------------------------------ detection

    /** Host-side tests pin a phone here (the JVM has no Android CPU/GPU to probe). */
    static DeviceProfile testOverride;

    static DeviceProfile forTest(String soc, String gpu, long ramMiB, int mhz, boolean modernCpu) {
        return new DeviceProfile(soc, gpu, ramMiB, mhz, 8, modernCpu, modernCpu, (1 << 22) | (1 << 12));
    }

    static DeviceProfile detect(Context ctx) {
        if (testOverride != null) return testOverride;
        String soc = socName();
        String gpu = glRenderer();
        long ram = 0;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ram = mi.totalMem >> 20;
        } catch (Exception ignored) {
        }
        int maxKHz = 0, cores = 0;
        for (int i = 0; i < 16; i++) {
            File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (!new File("/sys/devices/system/cpu/cpu" + i).exists()) break;
            cores++;
            maxKHz = Math.max(maxKHz, readInt(f));
        }
        boolean atomics = false, lrcpc = false;
        int featureLines = 0, atomicsLines = 0, lrcpcLines = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("Features")) continue;
                featureLines++;
                String feats = " " + line.substring(line.indexOf(':') + 1).trim() + " ";
                if (feats.contains(" atomics ")) atomicsLines++;
                if (feats.contains(" lrcpc ")) lrcpcLines++;
            }
        } catch (Exception ignored) {
        }
        // Every core must have the feature: the game thread can land on any of them.
        atomics = featureLines > 0 && atomicsLines == featureLines;
        lrcpc = featureLines > 0 && lrcpcLines == featureLines;
        int vk = 0;
        try {
            FeatureInfo[] fis = ctx.getPackageManager().getSystemAvailableFeatures();
            if (fis != null) for (FeatureInfo fi : fis) {
                if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(fi.name)) vk = Math.max(vk, fi.version);
            }
        } catch (Exception ignored) {
        }
        return new DeviceProfile(soc, gpu, ram, maxKHz / 1000, cores, atomics, lrcpc, vk);
    }

    private static int readInt(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            return Integer.parseInt(r.readLine().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String socName() {
        String s = null;
        if (Build.VERSION.SDK_INT >= 31) {
            String m = Build.SOC_MODEL, v = Build.SOC_MANUFACTURER;
            if (m != null && !m.isEmpty() && !"unknown".equalsIgnoreCase(m)) {
                s = (v != null && !"unknown".equalsIgnoreCase(v) ? v + " " : "") + m;
            }
        }
        if (s == null) {
            try (BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("Hardware")) {
                        s = line.substring(line.indexOf(':') + 1).trim();
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (s == null || s.isEmpty()) s = Build.HARDWARE;
        return s;
    }

    /** GL_RENDERER from a throw-away 1x1 pbuffer context ("Mali-G52 MC2", "Adreno (TM) 650"). */
    private static String glRenderer() {
        EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (dpy == EGL14.EGL_NO_DISPLAY) return "unknown GPU";
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return "unknown GPU";
        String out = "unknown GPU";
        EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        EGLSurface surf = EGL14.EGL_NO_SURFACE;
        try {
            int[] attr = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            if (EGL14.eglChooseConfig(dpy, attr, 0, cfg, 0, 1, n, 0) && n[0] > 0) {
                ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT,
                        new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
                surf = EGL14.eglCreatePbufferSurface(dpy, cfg[0],
                        new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
                if (EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) {
                    String r = GLES20.glGetString(GLES20.GL_RENDERER);
                    if (r != null) out = r;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf);
            if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx);
            EGL14.eglTerminate(dpy);
        }
        return out;
    }
}
