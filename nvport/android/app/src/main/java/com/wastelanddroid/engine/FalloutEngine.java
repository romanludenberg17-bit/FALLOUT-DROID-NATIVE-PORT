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
    /**
     * After the graphics driver lost the device (NativeGameActivity's start-over): 1 no
     * sun shadows, no MSAA, no grass; 2 also at most 720p and one more texture level
     * skipped. Cleared when the graphics settings are saved again.
     */
    public static final String GPU_SAFE = "gpu_safe_level";
    /** The game's process (manifest: NativeGameActivity android:process). */
    public static final String GAME_PROCESS_SUFFIX = ":game";
    /** Bumped when the bundled engine data (schemas) changes. */
    private static final int ENGINE_DATA_VERSION = 4; // 2: + lang (NVForever's own words)

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
        if (lighterAfterDeath(context)) android.util.Log.w("NVForever", "the last run died for memory or in native code: GPU_SAFE " + Prefs.get(context).getInt(GPU_SAFE, 0));
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
        // Russian (Cyrillic) game text is Windows-1251: New Vegas's Russian edition keeps it in
        // *_lang.esp over an English master, which the engine's own guess reads as 1252
        // (names like "Î ìâû"). Latin letters are the same in both, so an English game is safe.
        String phoneLang = java.util.Locale.getDefault().getLanguage();
        if (phoneLang.equals("ru") || phoneLang.equals("uk") || phoneLang.equals("be") || hasLangPlugin(data)) {
            args.add("env:NVF_CODE_PAGE=1251");
        }
        // The log: warnings, errors and what the story does; the per-frame render lines
        // (glow, exposure, interior fog, file reads, the winit cursor) stay out — each
        // line goes to two files and logcat.
        args.add("env:RUST_LOG=info,wgpu=error,naga=warn,bevy_winit=warn,bevy_render=warn,"
                + "nvforever::post=warn,nvforever::eye_adapt=warn,nvforever::sky=warn,nvforever::imagespace=warn,"
                + "nvforever::pipeline_disk_cache=warn,nvf_play::file_bytes=warn,nvf_play::music=warn,"
                + "nvf_play::tiles=warn,nvf_play::pause=warn,nvf_play::settings=warn,nvf_assets=warn");
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
        // Multisampling 4x / 8x kept from an earlier choice: 2x (the phone's settings page no
        // longer offers more).
        String aa = iniValue(new File(homeDir(context, game.id), "NVForever.ini"), "anti-aliasing");
        if (aa != null && (aa.equalsIgnoreCase("4x") || aa.equalsIgnoreCase("8x"))) ini.get("Settings").put("anti-aliasing", "2x");
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
            // Fallout 3 aims down the sights as New Vegas does (true iron sights; the
            // crosshair hides while aiming, nvf_play::reticle).
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
            // Fallout 3's room markers and portals (Vault 101 and the like) left the walls of
            // rooms in view culled from some angles: every room of an interior is drawn.
            args.add("--no-room-culling");
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

    /** The engine's command line for its next start (both copies). */
    public static void writeArgs(Context context, String text) throws IOException {
        writeText(new File(context.getFilesDir(), ARGS_FILE), text);
        File ext = context.getExternalFilesDir(null);
        if (ext != null) {
            new File(ext, EXIT_FILE).delete();
            writeText(new File(ext, "nvport_args.txt"), text);
        }
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
        Intent intent = new Intent(activity, NativeGameActivityFo3.forGame(ok.getGame().id));
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
        boolean fxTerrain, fxShafts, fxAo, fxClouds, fxHaze, fxWater;
        int safe;

        static Profile of(Context context) {
            Profile p = new Profile();
            p.quality = quality(context);
            p.g = com.wastelanddroid.game.GraphicsSettings.load(context, p.quality);
            // textureQuality 0 high, 1 medium, 2 low -> levels skipped; phones always lose one
            // level below High (FNV's 1024/2048 textures on a 8 GB phone).
            // At least one level on every phone: the engine and the GPU share the RAM (the
            // logs: under 1 GB free of 7 GB with full-size textures).
            p.mipSkip = Math.min(3, Math.max(1, p.g.textureQuality + (p.quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 0 : 1)));
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
            p.safe = Prefs.get(context).getInt(GPU_SAFE, 0);
            SharedPreferences fx = Prefs.get(context);
            p.fxTerrain = fx.getBoolean("fx_terrain", false);
            p.fxShafts = fx.getBoolean("fx_shafts", false);
            p.fxAo = fx.getBoolean("fx_ao", false);
            p.fxClouds = fx.getBoolean("fx_clouds", false);
            p.fxHaze = fx.getBoolean("fx_haze", false);
            p.fxWater = fx.getBoolean("fx_water", false);
            if (p.safe >= 1) {
                p.g.shadows = 0;
                p.g.grassDensity = 0;
            }
            if (p.safe >= 2) p.mipSkip = Math.min(3, Math.max(p.mipSkip, 2));
            return p;
        }

        void addArgs(List<String> a) {
            int vd = Math.max(1, g.viewDistance); // 4 low, 6 medium, 8 high (1..10)
            boolean high = quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH;
            boolean low = quality == com.wastelanddroid.game.DeviceProfile.Quality.LOW;
            // 5 x 5 cells only when asked for (view distance 9+): 3 x 3 keeps phones in budget.
            add(a, "--radius", vd >= 9 ? 2 : 1);
            // The game's own distant LOD, as the original draws it (the generated terrain
            // looked blurred and odd at a phone's settings and took memory).
            a.add("--no-lodgen");
            if (low) {
                // A low class (Mali-G57 and the like): not far.
                vd = Math.min(vd, 3);
            }
            add(a, "--lod-distance", 10000 + vd * 6000);
            add(a, "--lod-near-distance", 5000 + vd * 2500);
            add(a, "--tree-distance", low ? 0 : vd * 2500);
            add(a, "--lodgen-radius", vd >= 9 ? 3 : vd >= 6 ? 2 : 1);
            add(a, "--lodgen-res", 256);
            add(a, "--lodgen-step", high ? 4 : 8);
            add(a, "--cell-fade", 0.5f);
            add(a, "--msaa", high && safe == 0 ? 2 : 1);
            // The game's own logic at 60 Hz on every phone (the default follows the frame
            // rate up to 160: CPU time the GPU-bound frame does not have).
            add(a, "--physics-hz", 60);
            add(a, "--fov", g.fov);
            // The arms and the gun a little nearer than the default 55: on a wide phone
            // screen the sights read clearly when aiming.
            add(a, "--viewmodel-fov", 50);
            // The music under the voices and effects, as the original's defaults mix it.
            add(a, "--music-volume", 0.45f);
            if (g.grassDensity > 0) {
                add(a, "--grass-distance", Math.min(3500, 1000 + g.grassDensity * 800));
                add(a, "--grass-density", Math.min(0.7f, 0.2f + g.grassDensity * 0.15f));
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
            // No water reflections on a phone: a second camera draws the whole scene again
            // and reads pixels back from the GPU every frame (a device lost on Adreno).
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
            // The picture rows the player sets in the game's own settings page: the launcher
            // gives a first value only ("?"), except in the GPU safe mode.
            set.put("anti-aliasing", safe > 0 ? "FXAA" : quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? "?2x" : "?FXAA");
            // No upscaler on a phone: FSR 3.1's compute passes at full precision (the
            // Adreno driver reports no 16-bit floats) are heavier than the pixels they save.
            set.put("upscaler", "Off");
            set.put("sun-shadows", safe > 0 ? "Off" : g.shadows <= 0 ? "?Off" : g.shadows >= 3 ? "?Quality" : "?Performance");
            set.put("grass", safe > 0 ? "0" : g.grassDensity > 0 ? "?1" : "?0");
            set.put("distant-lod", "1");
            // The engine's frame-rate counter (pref "fps_counter", on in the alpha builds to
            // measure the phone's frame rate).
            set.put("fps-counter", fpsCounter ? "1" : "0");
            // Rows Android cannot read (CPU and GPU use): "not available" only.
            set.put("fps-counter-cpu", "0");
            set.put("fps-counter-gpu", "0");
            // Aiming: the gun's own sights lined up with the eye, clear in the middle of the
            // view, the crosshair gone (the shipped aim pose leaves them 60-85 px low).
            set.put("true-iron-sights", "1");
            // The fields of view come from the command line (a null value drops the row the
            // engine's settings page kept, as a part of its slider).
            set.put("field-of-view", null);
            set.put("viewmodel-field-of-view", null);
            // The rows that stand for this command line's numbers: the engine's settings page
            // saved them all once and they override the launcher's profile (a "fps" physics
            // rate up to 160 Hz; draw distances as parts of the command line's distances).
            set.put("physics-rate", "60");
            for (String k : new String[]{"object-distance", "actor-distance", "item-distance", "grass-distance"}) {
                set.put(k, "1.000");
            }
            // Trees: a part of the distant LOD's far limit, not of --tree-distance (1.000
            // drew them out to the LOD's 46 000 units): no row, the flag's distance.
            set.put("tree-distance", null);
            // Phones: the desktop effects stay off, whatever the engine's own settings page
            // kept (its INI wins over the command line): ambient occlusion (GTAO), the fine
            // and cloud/terrain shadows, light shafts, water effects, the mesh/terrain blend
            // (a 1536² rgba16f atlas), anti-tiling, hazes; sun shadow maps at 2048, not 4096.
            boolean highClass = quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH;
            set.put("shadow-resolution", "2048");
            for (String k : new String[]{"fine-shadows", "sharpen", "mesh-terrain-blend", "terrain-shadows"}) {
                set.put(k, "0");
            }
            // The launcher's "Эффекты" switches (off by default, each costs frames; all off in
            // the GPU safe mode): the engine's own counterparts of the effect mods (Vegas
            // Shaders, Vanilla Plus Terrain), whose DLLs cannot run here.
            boolean fx = safe == 0;
            set.put("terrain-height-blend", fx && fxTerrain ? "1" : "0");
            set.put("terrain-anti-tiling", fx && fxTerrain ? "1" : "0");
            set.put("light-shafts", fx && fxShafts ? "1" : "0");
            set.put("ambient-occlusion", fx && fxAo ? "Performance" : "Off");
            set.put("cloud-shadows", fx && fxClouds ? "1" : "0");
            set.put("heat-haze", fx && fxHaze ? "1" : "0");
            set.put("desert-haze", fx && fxHaze ? "1" : "0");
            set.put("water-effects", fx && fxWater ? "1" : "0");
            set.put("bloom", g.bloom ? "?1" : "?0");
            set.put("anisotropic", g.anisotropy >= 8 ? "?1" : "?0");
            set.put("water-reflections", "0");
            // The rest of the rows the phone's settings page hides (android_input.rs
            // PHONE_HIDDEN), at their light values.
            set.put("water-mirror", "0");
            set.put("smooth-terrain", "Off");
            set.put("frame-generation", "Off");
            set.put("display-sync", null);
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
    /** A row's value in an INI's [Settings] section, or null. */
    static String iniValue(File ini, String key) {
        if (!ini.isFile()) return null;
        try {
            boolean in = false;
            for (String line : readText(ini).split("\\r?\\n")) {
                String t = line.trim();
                if (t.startsWith("[")) in = t.equalsIgnoreCase("[Settings]");
                else if (in && t.contains("=") && t.substring(0, t.indexOf('=')).trim().equalsIgnoreCase(key)) return t.substring(t.indexOf('=') + 1).trim();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

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
                    String v = sections.get(current).get(found);
                    if (v != null && v.startsWith("?")) {
                        // A default only: the row the player (the engine's settings page) set stays.
                        if (done.add(current + "/" + found)) out.append(line).append('\n');
                        continue;
                    }
                    if (done.add(current + "/" + found) && v != null) out.append(found).append('=').append(v).append('\n');
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
            String v = e.getValue();
            if (v != null && v.startsWith("?")) v = v.substring(1);
            if (done.add(section + "/" + e.getKey()) && v != null) out.append(e.getKey()).append('=').append(v).append('\n');
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
            return n <= 0 ? auto : n <= 30 ? 30 : n <= 60 ? 60 : n <= 90 ? 90 : n <= 120 ? 120 : 144;
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
        // The launcher's old per-game resolution row is gone (its "2400x1080" left the game
        // drawing at the screen's full size): the class's height and the render scale only.
        String res = null;
        if (res != null && res.contains("x")) {
            try {
                int h = Integer.parseInt(res.substring(res.indexOf('x') + 1).trim());
                return prefs.getInt(GPU_SAFE, 0) >= 2 ? Math.min(h, 660) : h;
            } catch (NumberFormatException ignored) {
            }
        }
        com.wastelanddroid.game.DeviceProfile.Quality q = quality(context);
        int base = q == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 810
                : q == com.wastelanddroid.game.DeviceProfile.Quality.MEDIUM ? 660 : 480;
        int scale = Math.max(50, Math.min(100, prefs.getInt(Prefs.RES_SCALE, 100)));
        int h = Math.max(360, base * scale / 100);
        if (prefs.getInt(GPU_SAFE, 0) >= 2) h = Math.min(h, 660);
        return h;
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

    /** A Russian edition's language plugin (FalloutNV_lang.esp and the like) in Data. */
    static boolean hasLangPlugin(File data) {
        String[] names = data.list();
        if (names == null) return false;
        for (String n : names) if (n.toLowerCase(java.util.Locale.ROOT).endsWith("_lang.esp")) return true;
        return false;
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
        sb.append("\n===== how the game process ended (Android) =====\n").append(exitReasons(context, 6)).append('\n');
        section(sb, LOG_FILE, log, 96 * 1024);
        // Both games, the last played first: a crash while loading a save is followed by
        // the restart's own run, so the last three runs and crash reports of each go in.
        String scope = Prefs.get(context).getString("last_scope", "fnv");
        String[] scopes = "fo3".equals(scope) ? new String[]{"fo3", "fnv"} : new String[]{"fnv", "fo3"};
        for (String sc : scopes) {
            File logs = new File(homeDir(context, sc), "logs");
            List<File> runs = newestN(logs, "nvforever-", ".log", 3);
            if (runs.isEmpty() && !sc.equals(scope)) continue;
            String g = sc.equals("fo3") ? "Fallout 3" : "New Vegas";
            for (int i = 0; i < runs.size(); i++) {
                section(sb, g + ": run log" + (i == 0 ? "" : " -" + i), runs.get(i), i == 0 ? 448 * 1024 : 128 * 1024);
            }
            if (runs.isEmpty()) section(sb, g + ": run log", null, 0);
            for (File crash : newestN(logs, "crash-", ".txt", 3)) section(sb, g + ": crash report", crash, 48 * 1024);
        }
        File report = new File(base, REPORT_FILE);
        try {
            writeText(report, sb.toString());
        } catch (IOException e) {
            return log.isFile() ? log : null;
        }
        return report;
    }

    /** The system's record of how the game's process ended, the newest first. */
    public static String exitReasons(Context context, int max) {
        if (android.os.Build.VERSION.SDK_INT < 30) return "(Android 11+)";
        StringBuilder sb = new StringBuilder();
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            String game = context.getPackageName() + GAME_PROCESS_SUFFIX;
            int n = 0;
            boolean anrShown = false;
            for (android.app.ApplicationExitInfo e : am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 30)) {
                if (!game.equals(e.getProcessName())) continue;
                sb.append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date(e.getTimestamp())))
                        .append(" pid ").append(e.getPid()).append(": ").append(reasonName(e.getReason()))
                        .append(" status ").append(e.getStatus())
                        .append(", pss ").append(e.getPss() / 1024).append(" MB, rss ").append(e.getRss() / 1024).append(" MB")
                        .append(e.getDescription() == null ? "" : ", " + e.getDescription()).append('\n');
                // The newest "not responding": where the main thread stood (its stack).
                if (e.getReason() == android.app.ApplicationExitInfo.REASON_ANR && !anrShown) {
                    anrShown = true;
                    try (java.io.InputStream in = e.getTraceInputStream()) {
                        if (in != null) {
                            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                            byte[] chunk = new byte[16384];
                            for (int r; (r = in.read(chunk)) > 0 && buf.size() < 4 << 20; ) buf.write(chunk, 0, r);
                            sb.append(mainThreadOf(buf.toString("UTF-8")));
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (++n >= max) break;
            }
        } catch (Exception e) {
            sb.append(e).append('\n');
        }
        return sb.length() == 0 ? "(no record)" : sb.toString();
    }

    /** The "main" thread's part of an ANR trace (at most 60 lines). */
    private static String mainThreadOf(String trace) {
        int at = trace.indexOf("\"main\"");
        if (at < 0) return "  (no main thread in the trace)\n";
        String[] lines = trace.substring(at).split("\n");
        StringBuilder sb = new StringBuilder("  ANR main thread:\n");
        for (int i = 0; i < lines.length && i < 60; i++) {
            if (i > 0 && lines[i].trim().isEmpty()) break;
            sb.append("    ").append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    private static String reasonName(int r) {
        switch (r) {
            case 1: return "EXIT_SELF";
            case 2: return "SIGNALED";
            case 3: return "LOW_MEMORY (killed by the system for memory)";
            case 4: return "CRASH (java)";
            case 5: return "CRASH_NATIVE";
            case 6: return "ANR";
            case 7: return "INITIALIZATION_FAILURE";
            case 8: return "PERMISSION_CHANGE";
            case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED";
            case 11: return "USER_STOPPED";
            case 12: return "DEPENDENCY_DIED";
            case 13: return "OTHER";
            case 14: return "FREEZER";
            case 15: return "PACKAGE_STATE_CHANGE";
            case 16: return "PACKAGE_UPDATED";
            default: return "UNKNOWN(" + r + ")";
        }
    }

    /**
     * The game's process died since the last start for memory or in native code (no crash
     * report, the run log cut off): the next start takes one more GPU_SAFE level. True when
     * it did.
     */
    public static boolean lighterAfterDeath(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 30) return false;
        SharedPreferences prefs = Prefs.get(context);
        long seen = prefs.getLong("game_death_seen", 0);
        long newest = seen;
        boolean bad = false;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            String game = context.getPackageName() + GAME_PROCESS_SUFFIX;
            for (android.app.ApplicationExitInfo e : am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 10)) {
                if (!game.equals(e.getProcessName()) || e.getTimestamp() <= seen) continue;
                newest = Math.max(newest, e.getTimestamp());
                int r = e.getReason();
                if (r == android.app.ApplicationExitInfo.REASON_LOW_MEMORY || r == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE) bad = true;
            }
        } catch (Exception e) {
            return false;
        }
        SharedPreferences.Editor ed = prefs.edit().putLong("game_death_seen", newest);
        if (bad && seen != 0) ed.putInt(GPU_SAFE, Math.min(2, prefs.getInt(GPU_SAFE, 0) + 1));
        ed.commit();
        return bad && seen != 0;
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

    /**
     * What the engine found in the mods at its last start, per game, for the launcher's mod
     * page: {"fnv":{"when":ms,"plugins":[{"name","scripts","bytes","missing":[..]}],
     * "dropped":[..]},"fo3":{..}} (NVFOREVER_HOME/mod_report.tsv and mod_dropped.txt).
     */
    public static String modReportJson(Context context) {
        org.json.JSONObject all = new org.json.JSONObject();
        for (String sc : new String[]{"fnv", "fo3"}) {
            try {
                File home = homeDir(context, sc);
                File tsv = new File(home, "mod_report.tsv");
                File dropped = new File(home, "mod_dropped.txt");
                org.json.JSONObject g = new org.json.JSONObject();
                g.put("when", tsv.isFile() ? tsv.lastModified() : 0);
                org.json.JSONArray plugins = new org.json.JSONArray();
                if (tsv.isFile()) {
                    for (String line : readText(tsv).split("\n")) {
                        String[] f = line.split("\t", -1);
                        if (f.length < 4 || f[0].isEmpty()) continue;
                        org.json.JSONObject p = new org.json.JSONObject();
                        p.put("name", f[0]);
                        p.put("scripts", parseIntOr(f[1], 0));
                        p.put("bytes", parseIntOr(f[2], 0));
                        org.json.JSONArray miss = new org.json.JSONArray();
                        for (String m : f[3].split(",")) if (!m.isEmpty()) miss.put(m);
                        p.put("missing", miss);
                        plugins.put(p);
                    }
                }
                g.put("plugins", plugins);
                org.json.JSONArray drop = new org.json.JSONArray();
                if (dropped.isFile()) for (String line : readText(dropped).split("\n")) if (!line.trim().isEmpty()) drop.put(line.trim());
                g.put("dropped", drop);
                all.put(sc, g);
            } catch (Exception ignored) {
            }
        }
        return all.toString();
    }

    private static int parseIntOr(String s, int d) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static List<File> newestN(File dir, String prefix, String suffix, int n) {
        List<File> out = new java.util.ArrayList<>();
        File[] kids = dir.listFiles();
        if (kids == null) return out;
        for (File f : kids) {
            String name = f.getName();
            if (name.startsWith(prefix) && name.endsWith(suffix)) out.add(f);
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out.size() > n ? out.subList(0, n) : out;
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
