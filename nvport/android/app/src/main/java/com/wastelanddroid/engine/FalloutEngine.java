package com.wastelanddroid.engine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.util.Prefs;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Native engine launcher (replaces the Proton/FEX container of Fallout Droid 1.x).
 *
 * The game runs in {@link NativeGameActivity} (GameActivity + libnvforever.so). The engine's
 * command line is written to {@code filesDir/nvport_args.txt} (one argument per line,
 * {@code env:KEY=VALUE} lines set environment variables before the engine starts); the
 * engine log goes to {@code externalFilesDir/nvforever.log}.
 */
public final class FalloutEngine {
    public static final String ARGS_FILE = "nvport_args.txt";
    public static final String LOG_FILE = "nvforever.log";
    /** Written by android_main: running / ok / error ... / panic ... ; or by the activity: java ... */
    public static final String EXIT_FILE = "nvforever.exit";
    public static final String REPORT_FILE = "fd_report.txt";
    /** Prefs key prefix: a save to start straight into (the saves screen). */
    public static final String PENDING_LOAD = "pending_load_";
    /** The game's process (manifest: NativeGameActivity android:process). */
    public static final String GAME_PROCESS_SUFFIX = ":game";
    /** Bumped when the bundled engine data (schemas) changes. */
    private static final int ENGINE_DATA_VERSION = 2; // 2: + lang (NVForever's own words)

    private FalloutEngine() {}

    /** Progress callback (kept for the launcher's install/prepare UI). */
    public interface Progress {
        void on(String stage, int percent);
    }

    public static abstract class Prepared {
        private Prepared() {}

        public static final class Ok extends Prepared {
            private final GameDef game;
            private final List<String> args;

            public Ok(GameDef game, List<String> args) {
                this.game = game;
                this.args = args;
            }

            public GameDef getGame() { return game; }
            public List<String> getArgs() { return args; }
            /** Per-game scope (saves/settings home). */
            public String getScope() { return game.id; }
        }

        public static final class Error extends Prepared {
            private final String message;

            public Error(String message) { this.message = message; }

            public String getMessage() { return message; }
        }
    }

    /** The engine is bundled with the APK: nothing to download or install. */
    public static boolean ready(Context context) {
        return true;
    }

    /** Per-game engine home: settings, plugins.txt, saves, logs, caches. */
    public static File homeDir(Context context, String scope) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "home_" + scope);
        dir.mkdirs();
        return dir;
    }

    /** Kept name from 1.x: the per-game data directory the launcher manages. */
    public static File prefixDir(Context context, String scope) {
        return homeDir(context, scope);
    }

    public static File engineDataDir(Context context) {
        return new File(context.getFilesDir(), "engine");
    }

    /** Copies the engine's bundled data (record/NIF/script schemas) out of the APK once. */
    public static void ensureEngineData(Context context) throws IOException {
        File dir = engineDataDir(context);
        File stamp = new File(dir, ".version");
        String want = ENGINE_DATA_VERSION + ":" + versionCode(context);
        if (stamp.isFile() && want.equals(readText(stamp).trim())) return;
        copyAssetTree(context.getAssets(), "engine", dir);
        writeText(stamp, want);
    }

    /** Whether the bundled engine can run this game yet. */
    public static boolean supports(GameDef game) {
        return game == GameDef.FNV || game == GameDef.FO3;
    }

    public static Prepared prepare(Context context, GameDef game, File gameDir) {
        if (!supports(game)) {
            return new Prepared.Error(game.title + " пока не поддерживается нативным движком: "
                    + "профиль Fallout 3 (Fallout3.esm без FalloutNV.esm) ещё в разработке. "
                    + "Папку игры можно выбрать заранее — моды и проверка файлов уже работают.");
        }
        try {
            ensureEngineData(context);
        } catch (IOException e) {
            return new Prepared.Error("Не удалось распаковать данные движка: " + e.getMessage());
        }
        File data = findDataDir(gameDir);
        if (data == null || findChild(data, masterOf(game)) == null) {
            return new Prepared.Error("В папке " + gameDir + " нет Data/" + masterOf(game));
        }
        File schemas = new File(engineDataDir(context), "schemas");
        SharedPreferences prefs = Prefs.get(context);
        List<String> args = new ArrayList<>();
        args.add("env:NVFOREVER_HOME=" + homeDir(context, game.id).getAbsolutePath());
        args.add("env:NVF_PLAY_SCRIPT_FUNCTIONS=" + new File(schemas, "script/functions.json").getAbsolutePath());
        args.add("env:NVPORT_GAME=" + game.id);
        args.add("--data");
        args.add(data.getAbsolutePath());
        args.add("--esm-schema");
        args.add(new File(schemas, "fnv").getAbsolutePath());
        args.add("--nif-schema");
        args.add(new File(schemas, "nif").getAbsolutePath());
        args.add("--window=borderless");
        Profile profile = Profile.of(context);
        args.add("env:NVF_TEX_MIP_SKIP=" + profile.mipSkip);
        profile.addArgs(args);
        java.util.Map<String, java.util.Map<String, String>> ini = new java.util.LinkedHashMap<>();
        ini.put("Settings", profile.iniSettings());
        // NVForever's own words (menus, settings) in the phone's language: its translations
        // go into the home's lang folder, and the language is chosen once (the settings
        // page's choice is kept after that).
        File home = homeDir(context, game.id);
        try {
            copyTree(new File(engineDataDir(context), "lang"), new File(home, "lang"));
        } catch (IOException e) {
            android.util.Log.w("NVForever", "lang: " + e);
        }
        String lang = java.util.Locale.getDefault().getLanguage();
        File iniFile = new File(home, "NVForever.ini");
        boolean chosen = false;
        try {
            chosen = iniFile.isFile() && readText(iniFile).matches("(?s).*\\n\\s*language\\s*=.*");
        } catch (IOException ignored) {
        }
        if (!chosen && new File(home, "lang/" + lang + ".toml").isFile()) {
            ini.get("Settings").put("language", lang);
        }
        if (game == GameDef.FO3) {
            args.add("env:NVF_CHARGEN_QUEST=" + FO3_CHARGEN_QUEST);
            // Fallout 3's interface is green (its iSystemColorHUDMain / PipBoy 26, 255, 128),
            // New Vegas's amber.
            ini.get("Settings").put("hud-colour", "Green");
            ini.get("Settings").put("pipboy-colour", "Green");
            java.util.Map<String, String> general = new java.util.LinkedHashMap<>();
            general.put("sCharGenQuest", FO3_CHARGEN_QUEST);
            general.put("sIntroMovie", findChild(new File(data, "Video"), FO3_INTRO) != null ? FO3_INTRO : "");
            ini.put("General", general);
            java.util.Map<String, String> archive = new java.util.LinkedHashMap<>();
            archive.put("SArchiveList", existingArchives(data, FO3_ARCHIVES));
            ini.put("Archive", archive);
        }
        try {
            mergeIni(new File(homeDir(context, game.id), "NVForever.ini"), ini);
        } catch (IOException e) {
            android.util.Log.w("NVForever", "NVForever.ini: " + e);
        }
        if (game == GameDef.FO3) {
            args.add("--world");
            args.add("Wasteland");
        }
        List<String> plugins = loadOrder(game, data);
        if (!plugins.isEmpty()) {
            args.add("--plugins");
            args.addAll(plugins);
        }
        args.add("--play");
        // A save chosen on the saves screen: straight into it, else the main menu.
        String pending = prefs.getString(PENDING_LOAD + game.id, null);
        if (pending != null) {
            prefs.edit().remove(PENDING_LOAD + game.id).apply();
        }
        if (pending != null && new File(pending).isFile()) {
            args.add("--load");
            args.add(pending);
        } else {
            args.add("--menu");
        }
        return new Prepared.Ok(game, args);
    }

    public static void launch(Activity activity, Prepared.Ok ok) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String a : ok.getArgs()) sb.append(a).append('\n');
        writeText(new File(activity.getFilesDir(), ARGS_FILE), sb.toString());
        File ext = activity.getExternalFilesDir(null);
        if (ext != null) {
            new File(ext, EXIT_FILE).delete();
            writeText(new File(ext, "nvport_args.txt"), sb.toString());
        }
        Prefs.get(activity).edit().putString("last_scope", ok.getScope()).commit();
        Intent intent = new Intent(activity, NativeGameActivity.class);
        intent.putExtra("game", ok.getGame().id);
        activity.startActivity(intent);
    }

    // ---------------------------------------------------------------- mobile profile
    /** The launcher's quality (Pip-Boy / Настройки графики) as the engine's numbers. */
    static final class Profile {
        com.wastelanddroid.game.DeviceProfile.Quality quality;
        com.wastelanddroid.game.GraphicsSettings g;
        int mipSkip, frameCap;
        boolean custom, fpsCounter;

        static Profile of(Context context) {
            Profile p = new Profile();
            p.quality = quality(context);
            p.g = com.wastelanddroid.game.GraphicsSettings.load(context, p.quality);
            // textureQuality 0 high, 1 medium, 2 low -> levels skipped; phones always lose one
            // level below High (FNV's 1024/2048 textures on a 8 GB phone).
            p.mipSkip = Math.min(3, p.g.textureQuality + (p.quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 0 : 1));
            // Targets: 30 FPS on a low class (Helio G99), 60 on a medium one, up to 120 on a
            // high one (Snapdragon 8 Gen 5 class: ~100). A chosen limit (the in-game panel,
            // "fps_lock": auto / 30 / 60 / 90 / 120 / off) wins.
            int auto = p.quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 120 : p.quality == com.wastelanddroid.game.DeviceProfile.Quality.MEDIUM ? 60 : 30;
            p.frameCap = fpsLock(context, auto);
            // Sun shadows cost the most and showed artefacts: only when chosen in the
            // graphics settings (a custom setup), never from a preset.
            p.custom = Prefs.get(context).getBoolean("gfx_custom", false);
            p.fpsCounter = Prefs.get(context).getBoolean("fps_counter", true);
            if (!p.custom) p.g.shadows = 0;
            if (p.quality == com.wastelanddroid.game.DeviceProfile.Quality.LOW) {
                if (!p.custom) p.g.grassDensity = 0;
                p.mipSkip = Math.max(p.mipSkip, 2);
            }
            return p;
        }

        void addArgs(List<String> a) {
            int vd = Math.max(1, g.viewDistance); // 4 low, 6 medium, 8 high (1..10)
            boolean high = quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH;
            boolean low = quality == com.wastelanddroid.game.DeviceProfile.Quality.LOW;
            // 5 x 5 cells only when asked for (view distance 9+): 3 x 3 keeps phones in budget.
            add(a, "--radius", vd >= 9 ? 2 : 1);
            if (low) {
                // A low class (Mali-G57 and the like): no generated distant terrain, fixed
                // 60 Hz physics, the game's own LOD only and not far.
                a.add("--no-lodgen");
                add(a, "--physics-hz", 60);
                vd = Math.min(vd, 3);
            }
            add(a, "--lod-distance", 10000 + vd * 6000);
            add(a, "--lod-near-distance", 5000 + vd * 2500);
            add(a, "--tree-distance", low ? 0 : vd * 2500);
            add(a, "--lodgen-radius", vd >= 9 ? 3 : vd >= 6 ? 2 : 1);
            add(a, "--lodgen-res", g.textureQuality == 0 && high ? 512 : 256);
            add(a, "--lodgen-step", high ? 4 : 8);
            add(a, "--cell-fade", 0.5f);
            add(a, "--msaa", high ? 4 : quality == com.wastelanddroid.game.DeviceProfile.Quality.MEDIUM ? 2 : 1);
            add(a, "--fov", g.fov);
            // The music under the voices and effects, as the original's defaults mix it.
            add(a, "--music-volume", 0.45f);
            if (g.grassDensity > 0) {
                add(a, "--grass-distance", 1000 + g.grassDensity * 1000);
                add(a, "--grass-density", Math.min(1f, 0.25f + g.grassDensity * 0.2f));
            }
            if (g.shadows > 0) {
                a.add(g.shadows >= 3 ? "--sun-shadows=quality" : "--sun-shadows=performance");
                add(a, "--shadow-distance", 1500 + g.shadows * 1500);
            } else {
                a.add("--no-sun-shadows");
            }
            List<String> extra = new ArrayList<>();
            if (g.bloom) extra.add("bloom");
            if (g.anisotropy >= 8) extra.add("anisotropic");
            if (g.waterReflections && high) extra.add("water-reflections");
            if (extra.isEmpty()) {
                a.add("--graphics");
                a.add("vanilla");
            } else {
                a.add("--graphics-only");
                a.add(android.text.TextUtils.join(",", extra));
            }
        }

        /** The switches NVForever.ini keeps win over the command line: the launcher's go there too. */
        java.util.Map<String, String> iniSettings() {
            java.util.LinkedHashMap<String, String> set = new java.util.LinkedHashMap<>();
            set.put("frame-cap", frameCap <= 0 ? "Off" : String.valueOf(frameCap));
            // Multisampling also smooths alpha-tested cut-outs (grates, plants, fences), which
            // otherwise shimmer: cheap on tile-based mobile GPUs, resolved in tile memory.
            set.put("anti-aliasing", quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? "4x"
                    : quality == com.wastelanddroid.game.DeviceProfile.Quality.MEDIUM ? "2x" : "FXAA");
            set.put("sun-shadows", g.shadows <= 0 ? "Off" : g.shadows >= 3 ? "Quality" : "Performance");
            set.put("grass", g.grassDensity > 0 ? "1" : "0");
            set.put("distant-lod", "1");
            // The engine's frame-rate counter (pref "fps_counter", on in the alpha builds to
            // measure the phone's frame rate).
            set.put("fps-counter", fpsCounter ? "1" : "0");
            // As in the original: aiming holds the gun low with the crosshair (the engine's
            // "true iron sights" moves it to the eye and away from the usual framing).
            set.put("true-iron-sights", "0");
            return set;
        }

        private static void add(List<String> a, String flag, Object v) {
            a.add(flag);
            a.add(String.valueOf(v));
        }
    }

    /**
     * Sets `key=value` lines in the INI's sections (made where missing), keeping every other
     * line: the engine's own settings page writes the same file.
     */
    static void mergeIni(File ini, java.util.Map<String, java.util.Map<String, String>> sections) throws IOException {
        String text = ini.isFile() ? readText(ini) : "; NVForever settings.\n";
        StringBuilder out = new StringBuilder();
        String current = null;
        java.util.Set<String> done = new java.util.HashSet<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String line : text.split("\\r?\\n", -1)) {
            String t = line.trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                if (current != null) appendMissing(out, current, sections.get(current), done);
                current = null;
                for (String sec : sections.keySet()) if (t.equalsIgnoreCase("[" + sec + "]")) current = sec;
                if (current != null) seen.add(current);
            } else if (current != null && t.contains("=") && !t.startsWith(";")) {
                String k = t.substring(0, t.indexOf('=')).trim();
                String found = null;
                for (String key : sections.get(current).keySet()) if (key.equalsIgnoreCase(k)) found = key;
                if (found != null) {
                    if (done.add(current + "/" + found)) out.append(found).append('=').append(sections.get(current).get(found)).append('\n');
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        if (current != null) appendMissing(out, current, sections.get(current), done);
        for (java.util.Map.Entry<String, java.util.Map<String, String>> e : sections.entrySet()) {
            if (seen.contains(e.getKey())) continue;
            out.append('[').append(e.getKey()).append("]\n");
            appendMissing(out, e.getKey(), e.getValue(), done);
        }
        writeText(ini, out.toString().replaceAll("\n{3,}", "\n\n"));
    }

    private static void appendMissing(StringBuilder out, String section, java.util.Map<String, String> set, java.util.Set<String> done) {
        // Inside the section: before the blank lines that end it.
        int blank = 0;
        while (out.length() - blank >= 2 && out.charAt(out.length() - 1 - blank) == '\n' && out.charAt(out.length() - 2 - blank) == '\n') blank++;
        String tail = out.substring(out.length() - blank);
        out.setLength(out.length() - blank);
        for (java.util.Map.Entry<String, String> e : set.entrySet()) {
            if (done.add(section + "/" + e.getKey())) out.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        out.append(tail);
    }

    /** Fallout 3's opening quest, film and archives (the engine's FNV defaults otherwise). */
    static final String FO3_CHARGEN_QUEST = "CG00"; // the birth in Vault 101 (CG00..CG04: character creation)
    static final String FO3_INTRO = "Fallout INTRO Vsk.bik";
    static final String FO3_ARCHIVES = "Fallout - Textures.bsa, Fallout - Meshes.bsa, Fallout - Voices.bsa, "
            + "Fallout - Sound.bsa, Fallout - MenuVoices.bsa, Fallout - Misc.bsa";

    public static com.wastelanddroid.game.DeviceProfile.Quality quality(Context context) {
        String q = Prefs.get(context).getString(Prefs.QUALITY, "auto");
        try {
            return "auto".equals(q) ? com.wastelanddroid.game.DeviceProfile.autoQuality(context)
                    : com.wastelanddroid.game.DeviceProfile.Quality.valueOf(q.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            return com.wastelanddroid.game.DeviceProfile.Quality.LOW;
        }
    }

    /**
     * Height of the surface the engine draws (scaled up to the screen by the display
     * hardware): the chosen resolution, else by quality (540 / 720 / 900) and the
     * resolution scale slider.
     */
    /** The chosen frame-rate limit: 0 for none ("off"), `auto` when none is chosen. */
    public static int fpsLock(Context context, int auto) {
        String v;
        try {
            v = Prefs.get(context).getString(Prefs.FPS_LOCK, "auto");
        } catch (ClassCastException e) {
            v = "auto";
        }
        if (v == null || v.equals("auto")) return auto;
        if (v.equals("off")) return 0;
        try {
            int n = Integer.parseInt(v.replaceAll("[^0-9]", ""));
            return n <= 0 ? auto : n <= 30 ? 30 : n <= 60 ? 60 : n <= 90 ? 90 : 120;
        } catch (NumberFormatException e) {
            return auto;
        }
    }

    /** The frame-rate cap the profile gives (the activity asks the display for it). */
    public static int frameCap(Context context) {
        return Profile.of(context).frameCap;
    }

    public static int renderHeight(Context context) {
        SharedPreferences prefs = Prefs.get(context);
        String res = prefs.getString("resolution", null);
        if (res != null && res.contains("x")) {
            try {
                return Integer.parseInt(res.substring(res.indexOf('x') + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        com.wastelanddroid.game.DeviceProfile.Quality q = quality(context);
        int base = q == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 900
                : q == com.wastelanddroid.game.DeviceProfile.Quality.MEDIUM ? 720 : 480;
        int scale = Math.max(50, Math.min(100, prefs.getInt(Prefs.RES_SCALE, 100)));
        return Math.max(360, base * scale / 100);
    }

    private static final String[] FNV_MASTERS = {"FalloutNV.esm", "DeadMoney.esm", "HonestHearts.esm",
            "OldWorldBlues.esm", "LonesomeRoad.esm", "GunRunnersArsenal.esm", "ClassicPack.esm",
            "MercenaryPack.esm", "TribalPack.esm", "CaravanPack.esm"};
    private static final String[] FO3_MASTERS = {"Fallout3.esm", "Anchorage.esm", "ThePitt.esm",
            "BrokenSteel.esm", "PointLookout.esm", "Zeta.esm"};

    /**
     * Load order for the engine: the game's masters present in Data/, then the plugins listed
     * in Data/plugins.txt (written by the Pip-Boy mod manager: only enabled ones), in order.
     * Empty when there are no extra plugins (the engine then loads the masters it finds).
     */
    static List<String> loadOrder(GameDef game, File data) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String m : game == GameDef.FO3 ? FO3_MASTERS : FNV_MASTERS) {
            File f = findChild(data, m);
            if (f != null) {
                out.add(f.getName());
                seen.add(f.getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        File txt = findChild(data, "plugins.txt");
        if (txt != null) {
            try {
                for (String line : readText(txt).split("\\r?\\n")) {
                    String l = line.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    // Classic Fallout format: every listed plugin is on ("*" prefix tolerated).
                    if (l.startsWith("*")) l = l.substring(1).trim();
                    String low = l.toLowerCase(java.util.Locale.ROOT);
                    if (!(low.endsWith(".esm") || low.endsWith(".esp")) || seen.contains(low)) continue;
                    File f = findChild(data, l);
                    if (f != null) {
                        out.add(f.getName());
                        seen.add(low);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        // Always named: the engine's own default is New Vegas's masters.
        return out;
    }

    /** The names of `list` (comma-separated) that are in Data, in that order. */
    static String existingArchives(File data, String list) {
        StringBuilder sb = new StringBuilder();
        for (String n : list.split(",")) {
            File f = findChild(data, n.trim());
            if (f == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.getName());
        }
        return sb.toString();
    }

    private static File findChild(File dir, String name) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) if (f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }

    public static String masterOf(GameDef game) {
        return game == GameDef.FO3 ? "Fallout3.esm" : "FalloutNV.esm";
    }

    /** The game's Data folder (case-insensitive), or null. */
    public static File findDataDir(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory()) return null;
        File[] kids = gameDir.listFiles();
        if (kids == null) return null;
        for (File f : kids) {
            if (f.isDirectory() && f.getName().equalsIgnoreCase("Data")) return f;
        }
        return null;
    }

    /** Engine debug log switch (read by the launcher when sharing logs). */
    public static void setGameDebugLogs(Context context, boolean on) {
        Prefs.get(context).edit().putBoolean(Prefs.GAME_LOGS, on).apply();
    }

    /** Overlay mode for the in-game touch controls: "auto", "on" or "off". */
    public static void applyPcControls(Context context, String overlayMode) {
        Prefs.get(context).edit().putString(Prefs.OVERLAY_MODE, overlayMode).apply();
    }

    public static void setTouchControlsMode(Context context, String mode) {
        Prefs.get(context).edit().putString("touch_mode", mode).apply();
    }

    /** One text file with everything about the last run (shared by "Поделиться логами"). */
    public static File lastSessionLogs(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) return null;
        File log = new File(base, LOG_FILE);
        File exit = new File(base, EXIT_FILE);
        if (!log.isFile() && !exit.isFile()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("Fallout Droid ").append(versionName(context)).append(" (").append(versionCode(context)).append(")\n");
        sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                .append(", ").append(android.os.Build.HARDWARE).append(", SoC ").append(android.os.Build.SOC_MODEL)
                .append(", Android ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("Exit: ").append(exitStatus(context)).append(gameProcessAlive(context) ? " (game process alive)" : "").append("\n");
        section(sb, "command line", new File(base, "nvport_args.txt"), 16 * 1024);
        section(sb, LOG_FILE, log, 512 * 1024);
        String scope = Prefs.get(context).getString("last_scope", "fnv");
        File logs = new File(homeDir(context, scope), "logs");
        section(sb, "run log", newest(logs, "nvforever-", ".log"), 512 * 1024);
        section(sb, "crash report", newest(logs, "crash-", ".txt"), 256 * 1024);
        File report = new File(base, REPORT_FILE);
        try {
            writeText(report, sb.toString());
        } catch (IOException e) {
            return log.isFile() ? log : null;
        }
        return report;
    }

    /** What nvforever.exit says ("" when there is none). */
    public static String exitStatus(Context context) {
        File base = context.getExternalFilesDir(null);
        File f = base == null ? null : new File(base, EXIT_FILE);
        if (f == null || !f.isFile()) return "";
        try {
            return readText(f).trim();
        } catch (IOException e) {
            return "";
        }
    }

    public static boolean gameProcessAlive(Context context) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningAppProcessInfo> list = am == null ? null : am.getRunningAppProcesses();
        if (list == null) return false;
        String name = context.getPackageName() + GAME_PROCESS_SUFFIX;
        for (android.app.ActivityManager.RunningAppProcessInfo p : list) {
            if (name.equals(p.processName)) return true;
        }
        return false;
    }

    /**
     * Why the last run ended, for the launcher's dialog: null when it ended normally or is
     * still going; else the reason and the end of the engine's log.
     */
    public static String failureSummary(Context context) {
        String status = exitStatus(context);
        boolean alive = gameProcessAlive(context);
        String why;
        if (status.equals("ok")) return null;
        // "running" or nothing: the game's process was ended from outside (closed from the
        // recent apps, swiped away, killed by the system) — not an error to report.
        if (status.equals("running") || status.isEmpty()) {
            return null;
        } else if (status.startsWith("panic")) {
            why = "Ошибка движка: " + status.substring(5).trim();
        } else if (status.startsWith("java")) {
            why = "Ошибка запуска: " + status.substring(4).trim();
        } else {
            why = "Движок завершился с ошибкой (" + status + ").";
        }
        File base = context.getExternalFilesDir(null);
        String tail = base == null ? "" : tail(new File(base, LOG_FILE), 40);
        return tail.isEmpty() ? why : why + "\n\nКонец лога:\n" + tail;
    }

    private static void section(StringBuilder sb, String title, File f, int max) {
        sb.append("\n===== ").append(title);
        if (f == null || !f.isFile()) {
            sb.append(": нет =====\n");
            return;
        }
        sb.append(" (").append(f.getAbsolutePath()).append(") =====\n");
        try {
            String t = readText(f);
            if (t.length() > max) t = "...\n" + t.substring(t.length() - max);
            sb.append(t).append('\n');
        } catch (IOException e) {
            sb.append(e).append('\n');
        }
    }

    private static File newest(File dir, String prefix, String suffix) {
        File[] kids = dir.listFiles();
        File best = null;
        if (kids == null) return null;
        for (File f : kids) {
            String n = f.getName();
            if (n.startsWith(prefix) && n.endsWith(suffix) && (best == null || f.lastModified() > best.lastModified())) best = f;
        }
        return best;
    }

    private static String tail(File f, int lines) {
        if (!f.isFile()) return "";
        try {
            String[] all = readText(f).split("\\r?\\n");
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, all.length - lines); i < all.length; i++) sb.append(all[i]).append('\n');
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    public static Intent shareLogsIntent(Context context, File file) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
        return new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Fallout Droid: лог движка")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    // ---------------------------------------------------------------- helpers
    private static long versionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private static void copyAssetTree(AssetManager am, String path, File dest) throws IOException {
        String[] list = am.list(path);
        if (list == null || list.length == 0) {
            dest.getParentFile().mkdirs();
            try (InputStream in = am.open(path); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        dest.mkdirs();
        for (String child : list) copyAssetTree(am, path + "/" + child, new File(dest, child));
    }

    private static void copyTree(File from, File to) throws IOException {
        File[] kids = from.listFiles();
        if (kids == null) return;
        to.mkdirs();
        for (File f : kids) {
            File dest = new File(to, f.getName());
            if (f.isDirectory()) {
                copyTree(f, dest);
            } else if (!dest.isFile() || dest.length() != f.length() || dest.lastModified() < f.lastModified()) {
                java.nio.file.Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String readText(File f) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void writeText(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
