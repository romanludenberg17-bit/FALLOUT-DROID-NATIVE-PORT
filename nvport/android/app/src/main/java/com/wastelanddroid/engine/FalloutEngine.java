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
    /** One start with the light picture after a repeated lost device (NativeGameActivity). */
    public static final String RECOVERY_LITE = "recovery_lite_once";
    /** The render height picked in the game's settings (0 the screen's own; unset: the class's). */
    public static final String RENDER_HEIGHT_PICK = "render_height_pick";
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
        // Automatic setup for this phone's class (the launcher's button, and once with this
        // version): the effects off and the picture rows set from the class, over the
        // player's earlier choices.
        SharedPreferences ap = Prefs.get(context);
        // The flagship profile is put in once (its values replace the player's then), after
        // which the engine's settings page owns them.
        boolean flagshipFirst = com.wastelanddroid.game.DeviceProfile.flagshipProfile() && !ap.getBoolean("flagship_v2", false);
        if (flagshipFirst) {
            ap.edit().putBoolean("flagship_v2", true).commit();
            new java.io.File(homeDir(context, game.id), "texture_quality.txt").delete();
        }
        // v27: the class values once more for everyone (a low class without FXAA and with
        // sharpening, cloud shadows on a strong chip, the hidden shadow rows reset).
        boolean auto = flagshipFirst || ap.getBoolean("gfx_auto", false) || !ap.getBoolean("gfx_auto_v27", false);
        if (auto) {
            SharedPreferences.Editor ed = ap.edit().putBoolean("gfx_auto", false).putBoolean("gfx_auto_v25", true).putBoolean("gfx_auto_v26", true).putBoolean("gfx_auto_v27", true).putBoolean("gfx_custom", false).remove(GPU_SAFE);
            for (String k : new String[]{"terrain", "shafts", "ao", "clouds", "haze", "water"}) ed.putBoolean("fx_" + k, false);
            ed.commit();
        }
        Profile profile = Profile.of(context);
        // The light start is for this one start: the next one is the player's again.
        if (profile.lite) ap.edit().remove(RECOVERY_LITE).commit();
        // The engine's own Texture Quality row (android_input.rs) wins once the player set it.
        int mip = profile.mipSkip;
        try {
            java.io.File tq = new java.io.File(homeDir(context, game.id), "texture_quality.txt");
            if (tq.isFile()) mip = Math.max(0, Math.min(6, Integer.parseInt(readText(tq).trim())));
        } catch (Exception ignored) {
        }
        args.add("env:NVF_TEX_MIP_SKIP=" + mip);
        // GPU mesh preprocessing for the engine (console `fdgpu none|preprocess|culling|auto`,
        // kept in gpu_preprocess.txt): auto lets the engine choose (the CPU path on an
        // Adreno 8xx).
        try {
            java.io.File gp = new java.io.File(homeDir(context, game.id), "gpu_preprocess.txt");
            String mode = gp.isFile() ? readText(gp).trim() : "auto";
            if (mode.matches("none|preprocess|culling|auto")) args.add("env:NVPORT_GPU_PREPROCESS=" + mode);
        } catch (Exception ignored) {
        }
        // Skinning: joints from uniform buffers on Adreno 5xx/6xx (characters drawn as a
        // floating head on Snapdragon 778G), Bevy's storage buffers elsewhere; console
        // `fdskin uniform|storage|auto` (skinning.txt) chooses.
        try {
            java.io.File home = homeDir(context, game.id);
            java.io.File sk = new java.io.File(home, "skinning.txt");
            String mode = sk.isFile() ? readText(sk).trim() : "auto";
            boolean uniform = mode.equals("uniform") || (mode.equals("auto") && com.wastelanddroid.game.DeviceProfile.adreno6OrOlder(home));
            if (uniform) args.add("env:NVPORT_SKIN_UNIFORM=1");
        } catch (Exception ignored) {
        }
        // The resolution row of the game's settings shows the height in use (0: native).
        args.add("env:NVPORT_RENDER_HEIGHT=" + Math.max(0, renderHeight(context)));
        // Snapdragon 7+ Gen 2 and up, and every high class chip: the engine's settings page
        // shows the graphics rows again and the player's choices there stand.
        if (strongGraphics(context)) args.add("env:NVPORT_FULL_GFX=1");
        profile.addArgs(args);
        java.util.Map<String, java.util.Map<String, String>> ini = new java.util.LinkedHashMap<>();
        ini.put("Settings", profile.iniSettings());
        if (auto) {
            // The class's values replace the player's ("?" rows are first values only).
            for (java.util.Map.Entry<String, String> e : ini.get("Settings").entrySet()) {
                if (e.getValue() != null && e.getValue().startsWith("?")) e.setValue(e.getValue().substring(1));
            }
            ini.get("Settings").put("shadow-filter", "Smooth");
            ini.get("Settings").put("shadow-distance", null);
            for (String k : new String[]{"object-distance", "actor-distance", "item-distance", "grass-distance"}) ini.get("Settings").put(k, "1.000");
        }
        // Multisampling 4x / 8x kept from an earlier choice: 2x (the phone's settings page no
        // longer offers more).
        String aa = iniValue(new File(homeDir(context, game.id), "NVForever.ini"), "anti-aliasing");
        if (aa != null && (aa.equalsIgnoreCase("4x") || aa.equalsIgnoreCase("8x"))) ini.get("Settings").put("anti-aliasing", "2x");
        // The temporal shadow filter kept from an earlier choice: Smooth (it shimmered).
        String filter = iniValue(new File(homeDir(context, game.id), "NVForever.ini"), "shadow-filter");
        if (filter != null && filter.equalsIgnoreCase("Temporal")) ini.get("Settings").put("shadow-filter", "Smooth");
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
            // Keep room culling enabled on LOW class (Helio G85/G99/G100) for interior FPS.
            if (profile.quality != com.wastelanddroid.game.DeviceProfile.Quality.LOW) {
                args.add("--no-room-culling");
            }
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
        // Little free memory (other apps hold it): the engine reads the whole game's data at
        // start and the system may refuse it (a phone with under 1 GB free ended so).
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long freeMb = mi.availMem / (1024 * 1024);
            if (freeMb < 1500) {
                android.widget.Toast.makeText(activity, "Мало свободной памяти (" + freeMb + " МБ). Закройте другие приложения — иначе игра может вылететь при загрузке.", android.widget.Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {
        }
        StringBuilder sb = new StringBuilder();
        for (String a : ok.getArgs()) sb.append(a).append('\n');
        writeText(new File(activity.getFilesDir(), ARGS_FILE), sb.toString());
        File ext = activity.getExternalFilesDir(null);
        if (ext != null) {
            new File(ext, EXIT_FILE).delete();
            writeText(new File(ext, "nvport_args.txt"), sb.toString());
        }
        Prefs.get(activity).edit().putString("last_scope", ok.getScope()).commit();
        final Intent intent = new Intent(activity, NativeGameActivityFo3.forGame(ok.getGame().id));
        intent.putExtra("game", ok.getGame().id);
        // Both games run in the one ":game" process, and the engine can start only once in a
        // process (winit's event loop, the engine's global state). The other game still alive
        // there (put in the background, not quit) made the new one start inside it and the
        // app stopped ("Во время работы приложения произошёл сбой"). That process ends first,
        // and the new game starts once it is gone. The same game alive is brought back as it is.
        String running = Prefs.get(activity).getString(RUNNING_GAME, null);
        boolean alive = gameProcessAlive(activity);
        Prefs.get(activity).edit().putString(RUNNING_GAME, ok.getGame().id).commit();
        if (!alive || ok.getGame().id.equals(running)) {
            activity.startActivity(intent);
            return;
        }
        android.util.Log.i("NVForever", "launch: " + running + " still runs; its process ends before " + ok.getGame().id + " starts");
        killGameProcess(activity);
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final long since = android.os.SystemClock.uptimeMillis();
        h.post(new Runnable() {
            @Override
            public void run() {
                if (gameProcessAlive(activity) && android.os.SystemClock.uptimeMillis() - since < 6000) {
                    killGameProcess(activity);
                    h.postDelayed(this, 100);
                    return;
                }
                activity.startActivity(intent);
            }
        });
    }

    /** The game started last (pref), to tell whether the live game process is this one. */
    static final String RUNNING_GAME = "running_game";

    /** Ends the game process (both games run in it). */
    static void killGameProcess(Context context) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningAppProcessInfo> list = am == null ? null : am.getRunningAppProcesses();
        if (list == null) return;
        String name = context.getPackageName() + GAME_PROCESS_SUFFIX;
        for (android.app.ActivityManager.RunningAppProcessInfo p : list) {
            if (name.equals(p.processName)) android.os.Process.killProcess(p.pid);
        }
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
        /** FalloutEngine.strongGraphics: the engine's settings page owns the graphics rows. */
        boolean strong;
        /** DeviceProfile.flagshipProfile. */
        boolean flagship;
        /** This start only: the light picture after a repeated lost device. */
        boolean lite;

        static Profile of(Context context) {
            Profile p = new Profile();
            p.quality = quality(context);
            p.g = com.wastelanddroid.game.GraphicsSettings.load(context, p.quality);
            // textureQuality 0 high, 1 medium, 2 low -> levels skipped; phones always lose one
            // level below High (FNV's 1024/2048 textures on a 8 GB phone).
            // At least one level on every phone: the engine and the GPU share the RAM (the
            // logs: under 1 GB free of 7 GB with full-size textures).
            p.mipSkip = Math.min(3, Math.max(1, p.g.textureQuality + (p.quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 0 : 1)));
            // Targets: 60 FPS on a low and a medium class (30 on the weakest), up to 120 on a
            // high one (Snapdragon 8 Gen 5 class: ~100). A chosen limit (the in-game panel,
            // "fps_lock": auto / 30 / 60 / 90 / 120 / off) wins.
            // A low class was capped at 30, which testers' Mali phones reached exactly: 60
            // now (the weakest chips keep 30); the frame log shows what each one reaches.
            int auto = p.quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 120
                    : p.quality == com.wastelanddroid.game.DeviceProfile.Quality.MEDIUM ? 60
                    : com.wastelanddroid.game.DeviceProfile.veryLow() ? 30 : 60;
            p.frameCap = fpsLock(context, auto);
            // Sun shadows cost the most and showed artefacts: only when chosen in the
            // graphics settings (a custom setup), never from a preset.
            p.custom = Prefs.get(context).getBoolean("gfx_custom", false);
            p.fpsCounter = Prefs.get(context).getBoolean("fps_counter", true);
            // Sun shadows: on (Performance) for a high class (their shimmer is fixed: every
            // cascade drawn every frame, no temporal filter), off below it.
            if (!p.custom) p.g.shadows = p.quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 2 : 0;
            if (p.quality == com.wastelanddroid.game.DeviceProfile.Quality.LOW) {
                if (!p.custom) p.g.grassDensity = 0;
                // Two levels dropped (a quarter of the detail) left the textures blurred on an
                // 8 GB 778G; with ETC2 (nvf-assets texture.rs) one is enough there.
                p.mipSkip = Math.max(p.mipSkip, com.wastelanddroid.game.DeviceProfile.totalRamMb(context) >= 7000 ? 1 : 2);
            }
            // A strong chip keeps the player's graphics after a crash (the recovery reloads the
            // save instead); a safe level set by an older build is dropped.
            if (!mayDegrade(context, p.quality)) Prefs.get(context).edit().remove(GPU_SAFE).apply();
            p.safe = Prefs.get(context).getInt(GPU_SAFE, 0);
            p.strong = strongGraphics(context);
            if (Prefs.get(context).getBoolean(RECOVERY_LITE, false)) {
                p.safe = Math.max(p.safe, 1);
                p.lite = true;
            }
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
            // The flagship profile (DeviceProfile.flagshipProfile): every level of every
            // texture, the sun's shadows at Quality, all of the engine's effects.
            p.flagship = com.wastelanddroid.game.DeviceProfile.flagshipProfile() && !p.lite;
            if (p.flagship) {
                p.mipSkip = 0;
                p.g.shadows = 3;
                p.g.bloom = true;
                p.g.anisotropy = 16;
                if (p.g.grassDensity <= 0) p.g.grassDensity = 1;
                // The engine's desktop effects stay off here too: with all of them the game
                // froze on going outdoors on this phone (a tester's S25 Ultra, alpha38).
                p.fxClouds = true;
                p.fxTerrain = p.fxShafts = p.fxAo = p.fxHaze = p.fxWater = false;
            }
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
            // A low class draws at 480p: FXAA's blur over so few pixels made the picture
            // "soap" (players' reports); it gets none, and the sharpening below instead.
            boolean low = quality == com.wastelanddroid.game.DeviceProfile.Quality.LOW;
            set.put("anti-aliasing", flagship ? "?4x" : safe > 0 ? "FXAA" : quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? "?2x" : low ? "?Off" : "?FXAA");
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
            // The weapon's own field of view is the player's from the settings page once
            // set (a long rifle's sights look small at 50; a lower value draws the gun
            // larger): the launcher's 50 only stands while the row is not set.
            // The rows that stand for this command line's numbers: the engine's settings page
            // saved them all once and they override the launcher's profile (a "fps" physics
            // rate up to 160 Hz; draw distances as parts of the command line's distances).
            set.put("physics-rate", "60");
            // First values only ("?"): the game's Graphics Profile and its rows own the
            // distances once set (a forced 1.000 every start undid a Potato or Low profile
            // after a restart: the page then read Custom, the world drew everything).
            for (String k : new String[]{"object-distance", "actor-distance", "item-distance", "grass-distance"}) {
                set.put(k, "?1.000");
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
            for (String k : new String[]{"fine-shadows", "mesh-terrain-blend", "terrain-shadows"}) {
                set.put(k, "0");
            }
            // Contrast-adaptive sharpening (one cheap pass): it takes the blur off a phone's
            // reduced resolution, so it is on below the high class, a first value only.
            set.put("sharpen", quality == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? "?0" : strong ? "?1" : "1");
            // The launcher's "Эффекты" switches (off by default, each costs frames; all off in
            // the GPU safe mode): the engine's own counterparts of the effect mods (Vegas
            // Shaders, Vanilla Plus Terrain), whose DLLs cannot run here.
            boolean fx = safe == 0;
            // First values only, as the rows above: the game's settings (its Graphics Profile)
            // own them after the first start; the GPU safe mode alone forces them off.
            String first = fx ? "?" : "";
            set.put("terrain-height-blend", first + (fx && fxTerrain ? "1" : "0"));
            set.put("terrain-anti-tiling", first + (fx && fxTerrain ? "1" : "0"));
            set.put("light-shafts", first + (fx && fxShafts ? "1" : "0"));
            set.put("ambient-occlusion", first + (fx && fxAo ? "Performance" : "Off"));
            // Cloud shadows: on by default on a strong chip (its settings page has the row),
            // off on the others unless the launcher's switch is on.
            set.put("cloud-shadows", strong ? (fx ? "?1" : "0") : first + (fx && fxClouds ? "1" : "0"));
            set.put("heat-haze", first + (fx && fxHaze ? "1" : "0"));
            set.put("desert-haze", first + (fx && fxHaze ? "1" : "0"));
            set.put("water-effects", first + (fx && fxWater ? "1" : "0"));
            set.put("bloom", g.bloom ? "?1" : "?0");
            set.put("anisotropic", g.anisotropy >= 8 ? "?1" : "?0");
            set.put("water-reflections", first + "0");
            // The rest of the rows the phone's settings page hides (android_input.rs
            // PHONE_HIDDEN), at their light values.
            set.put("water-mirror", "0");
            set.put("smooth-terrain", "Off");
            set.put("frame-generation", "Off");
            set.put("display-sync", null);
            // The one light start after repeated lost devices: also no cloud shadows and no
            // eye adaptation (its compute pass), whatever the settings page holds.
            if (lite) {
                set.put("cloud-shadows", "0");
                set.put("eye-adaptation", "0");
            }
            // A strong chip (strongGraphics): the rows its settings page shows again are the
            // player's; the launcher's values are first values only there.
            if (strong && !lite) {
                // (Shadow resolution, fine, terrain and cloud shadows stay the launcher's:
                // 4096² maps and those passes broke the picture on an 8 Gen 5, alpha35.)
                for (String k : new String[]{"sharpen", "mesh-terrain-blend",
                        "terrain-height-blend", "terrain-anti-tiling", "light-shafts", "ambient-occlusion",
                        "heat-haze", "desert-haze", "water-effects", "water-reflections", "water-mirror", "smooth-terrain",
                        "anti-aliasing", "sun-shadows", "grass"}) {
                    String v = set.get(k);
                    if (v != null && !v.startsWith("?")) set.put(k, "?" + v);
                }
            }
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
        // The player's pick in the game's settings wins (0: the screen's own size).
        if (prefs.contains(RENDER_HEIGHT_PICK)) {
            int pick = prefs.getInt(RENDER_HEIGHT_PICK, -1);
            if (pick == 0) return -1;
            if (pick > 0) return prefs.getInt(GPU_SAFE, 0) >= 2 ? Math.min(pick, 660) : pick;
        }
        com.wastelanddroid.game.DeviceProfile.Quality q = quality(context);
        int base = com.wastelanddroid.game.DeviceProfile.flagshipProfile() ? 1080
                : q == com.wastelanddroid.game.DeviceProfile.Quality.HIGH ? 810
                : q == com.wastelanddroid.game.DeviceProfile.Quality.MEDIUM ? 660
                : 480;
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
        section(sb, "java crash of the game", new File(base, "java_crash.txt"), 16 * 1024);
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
                if (i == 0) headSection(sb, g + ": run log, start", runs.get(i), 96 * 1024);
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
    /**
     * Whether a crash may lower this phone's graphics: never on a high class chip or one of
     * Snapdragon 7+ Gen 2 and up (their players keep the graphics they chose).
     */
    /** Snapdragon 7+ Gen 2 and up, or a high class chip: the full graphics settings. */
    public static boolean strongGraphics(Context context) {
        return quality(context) == com.wastelanddroid.game.DeviceProfile.Quality.HIGH || com.wastelanddroid.game.DeviceProfile.replacersAllowed();
    }

    public static boolean mayDegrade(Context context, com.wastelanddroid.game.DeviceProfile.Quality q) {
        return q != com.wastelanddroid.game.DeviceProfile.Quality.HIGH && !com.wastelanddroid.game.DeviceProfile.replacersAllowed();
    }

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
        // The engine ends its own process after a lost graphics device (EXIT_SELF) before the
        // game activity can start it over: a crash report saying so, newer than the last
        // check, counts as such a death too.
        long reportSeen = prefs.getLong("gpu_report_seen", 0), reportNewest = reportSeen;
        for (String sc : new String[]{"fnv", "fo3"}) {
            for (File crash : newestN(new File(homeDir(context, sc), "logs"), "crash-", ".txt", 2)) {
                if (crash.lastModified() <= reportSeen) continue;
                reportNewest = Math.max(reportNewest, crash.lastModified());
                try {
                    String t = readText(crash).toLowerCase(java.util.Locale.ROOT);
                    if (t.contains("device lost") || t.contains("devicelost")) bad = true;
                } catch (IOException ignored) {
                }
            }
        }
        SharedPreferences.Editor ed = prefs.edit().putLong("game_death_seen", newest).putLong("gpu_report_seen", reportNewest);
        boolean first = seen == 0 && reportSeen == 0;
        if (bad && !first && mayDegrade(context, quality(context))) ed.putInt(GPU_SAFE, Math.min(2, prefs.getInt(GPU_SAFE, 0) + 1));
        ed.commit();
        return bad && !first;
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

    /** The first `max` characters of a file (a long run log's start-up). */
    private static void headSection(StringBuilder sb, String title, File f, int max) {
        if (f == null || !f.isFile()) return;
        try {
            String t = readText(f);
            if (t.length() <= 448 * 1024) return; // the tail section holds it all
            sb.append("\n===== ").append(title).append(" (").append(f.getAbsolutePath()).append(") =====\n");
            sb.append(t, 0, Math.min(max, t.length())).append("\n...\n");
        } catch (IOException ignored) {
        }
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

    static String versionName(Context context) {
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
