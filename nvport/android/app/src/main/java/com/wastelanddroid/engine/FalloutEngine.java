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
    /** Bumped when the bundled engine data (schemas) changes. */
    private static final int ENGINE_DATA_VERSION = 1;

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
        return game == GameDef.FNV;
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
        if (data == null) {
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
        args.add("--menu");
        return new Prepared.Ok(game, args);
    }

    public static void launch(Activity activity, Prepared.Ok ok) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String a : ok.getArgs()) sb.append(a).append('\n');
        writeText(new File(activity.getFilesDir(), ARGS_FILE), sb.toString());
        Intent intent = new Intent(activity, NativeGameActivity.class);
        intent.putExtra("game", ok.getGame().id);
        activity.startActivity(intent);
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
        int masters = out.size();
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
        return out.size() > masters ? out : new ArrayList<>();
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

    public static File lastSessionLogs(Context context) {
        File base = context.getExternalFilesDir(null);
        File log = base == null ? null : new File(base, LOG_FILE);
        return log != null && log.isFile() ? log : null;
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
