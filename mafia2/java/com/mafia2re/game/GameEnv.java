package com.mafia2re.game;

import android.content.Context;
import android.system.Os;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Everything the game process needs before libmain.so starts: engine ini, game folder,
 * Vulkan driver choice, TU_DEBUG, DXVK log. Applied by the launcher and again by the game
 * activity itself, so a game restored by Android without the launcher still finds its files.
 * The port sets its own defaults with setenv(..., overwrite = 0): ours win.
 */
final class GameEnv {
    private GameEnv() {
    }

    static void apply(Context c) {
        GameConfig cfg = new GameConfig(c);
        DeviceProfile dp = DeviceProfile.detectCached(c);
        cfg.computeAutoKeys(dp);
        cfg.writeIni();
        String game = cfg.gamePath();
        String tu = cfg.turnipDebug(dp);
        File drv = null;
        try {
            if (game != null) Os.setenv("MAFIA2RE_GAME", game, true);
            if (tu == null) Os.unsetenv("TU_DEBUG");
            else Os.setenv("TU_DEBUG", tu, true);
            // A driver the user dropped into files/driver wins; else the newer bundled Turnip
            // when asked for (the port loads MAFIA2RE_CUSTOM_DRIVER through adrenotools).
            drv = installCustomDriver(c);
            if (drv == null && cfg.useNextTurnip(dp) && dp.isAdreno()) {
                File next = new File(c.getApplicationInfo().nativeLibraryDir, "libvulkan_freedreno_next.so");
                if (next.canRead()) drv = next;
            }
            if (drv == null) Os.unsetenv("MAFIA2RE_CUSTOM_DRIVER");
            else Os.setenv("MAFIA2RE_CUSTOM_DRIVER", drv.getAbsolutePath(), true);
            // DXVK says why it gives up (missing feature, failed pipeline) only in its own log.
            Os.setenv("DXVK_LOG_LEVEL", "warn", true);
        } catch (Throwable ignored) {
            // Some firmwares (and host-side tests) have no setenv: the port then uses its defaults.
        }
        writeLaunchNote(c, cfg, game, tu, drv);
    }

    /** What was handed to the game; first block of "Отправить лог". */
    private static void writeLaunchNote(Context c, GameConfig cfg, String game, String tu, File drv) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) return;
        String s = "ReMaf2 " + LauncherActivity.VERSION + " launch " + new java.util.Date() + "\n"
                + "MAFIA2RE_GAME=" + game + "\n"
                + "TU_DEBUG=" + (tu == null ? "(unset)" : "\"" + tu + "\"") + "\n"
                + "MAFIA2RE_CUSTOM_DRIVER=" + (drv == null ? "(unset)" : drv.getAbsolutePath()) + "\n"
                + "vulkan_driver=" + GameConfig.DRIVER.name(GameConfig.DRIVER.indexOf(cfg.get(GameConfig.DRIVER))) + "\n";
        try (FileOutputStream out = new FileOutputStream(new File(dir, "remaf2-launch.txt"))) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    /** A .so dropped into files/driver is copied to private storage (same rule as the port's setup). */
    private static File installCustomDriver(Context c) {
        File ext = c.getExternalFilesDir(null);
        File[] list = ext == null ? null : new File(ext, "driver").listFiles((d, n) -> n.endsWith(".so"));
        File dst = new File(new File(c.getFilesDir(), "driver"), "libvulkan_custom.so");
        if (list == null || list.length == 0) {
            dst.delete();
            return null;
        }
        File src = list[0];
        if (dst.exists() && dst.length() == src.length() && dst.lastModified() >= src.lastModified()) return dst;
        dst.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] b = new byte[1 << 16];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return dst;
        } catch (Exception e) {
            dst.delete();
            return null;
        }
    }
}
