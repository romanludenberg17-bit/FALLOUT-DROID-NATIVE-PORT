package com.mafia2re.game;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * All user settings. Engine options end up in {@code <externalFilesDir>/mafia2re.ini}, which
 * libmain.so reads at start-up; touch options stay in shared preferences for the overlay.
 */
final class GameConfig {
    static final String PREFS = "m2launcher";

    /** One option row: key, labels, values. {@code engine} rows are written to mafia2re.ini. */
    static final class Option {
        final String key, ru, en;
        final String[] namesRu, namesEn;
        final int[] values;
        final boolean engine;
        final String hintRu, hintEn;

        Option(String key, String ru, String en, String[] namesRu, String[] namesEn, int[] values, boolean engine,
               String hintRu, String hintEn) {
            this.key = key;
            this.ru = ru;
            this.en = en;
            this.namesRu = namesRu;
            this.namesEn = namesEn;
            this.values = values;
            this.engine = engine;
            this.hintRu = hintRu;
            this.hintEn = hintEn;
        }

        String label() {
            return Ui.t(ru, en);
        }

        String name(int i) {
            return Ui.english() ? namesEn[i] : namesRu[i];
        }

        String hint() {
            return Ui.t(hintRu, hintEn);
        }

        int indexOf(int value) {
            for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
            return 0;
        }
    }

    private static String[] s(String... a) {
        return a;
    }

    private static int[] v(int... a) {
        return a;
    }

    // ------------------------------------------------------------------ engine options
    static final Option RES = new Option("render_height", "Разрешение", "Resolution",
            s("540p", "720p", "900p", "1080p"), s("540p", "720p", "900p", "1080p"), v(540, 720, 900, 1080), true,
            "Внутреннее разрешение рендера. Главный рычаг производительности.", "Internal render height. The main performance knob.");
    static final Option TEX = new Option("texture_skip_mips", "Текстуры", "Textures",
            s("Полные", "Средние", "Низкие"), s("Full", "Medium", "Low"), v(0, 1, 2), true,
            "«Низкие» экономят видеопамять: для телефонов с 4 ГБ.", "Low saves video memory: for 4 GB phones.");
    static final Option DETAIL = new Option("game_detail", "Детализация", "Detail",
            s("Низкая", "Средняя", "Высокая"), s("Low", "Medium", "High"), v(0, 1, 2), true,
            "Дальность прорисовки, тени, эффекты.", "Draw distance, shadows, effects.");
    static final Option FPS = new Option("fps_limit", "Лимит FPS", "FPS limit",
            s("Нет", "30", "45", "60"), s("None", "30", "45", "60"), v(0, 30, 45, 60), true,
            "30 FPS — ровно и без перегрева на слабых чипах.", "30 FPS is smooth and cool on entry chips.");
    static final Option BATTERY = new Option("battery_saver", "Экономия батареи", "Battery saver",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), true,
            "Снижает нагрузку в меню и на паузе.", "Lowers the load in menus and while paused.");
    static final Option DRIVER = new Option("vulkan_driver", "Драйвер Vulkan", "Vulkan driver",
            s("Авто", "Системный", "Turnip"), s("Auto", "System", "Turnip"), v(0, 1, 2), true,
            "«Авто» — порт сам проверяет Turnip и системный драйвер. Меняйте, только если игра не стартует или рисует мусор.", "");
    static final Option VOICE = new Option("game_voice", "Озвучка", "Voice",
            s("Русская", "Английская"), s("Russian", "English"), v(0, 1), true,
            "Английской нужна папка pc/sds_ru_en.", "English needs pc/sds_ru_en.");
    static final Option LANG = new Option("game_lang", "Язык игры", "Game language",
            s("Русский", "English"), s("Russian", "English"), v(0, 1), true,
            "Английскому нужна папка pc/sds_en.", "English needs pc/sds_en.");

    // ------------------------------------------------------------------ touch options
    static final Option OPACITY = new Option("ui_opacity", "Прозрачность кнопок", "Button opacity",
            s("30%", "45%", "60%", "80%", "100%"), s("30%", "45%", "60%", "80%", "100%"), v(30, 45, 60, 80, 100), false, "", "");
    static final Option SCALE = new Option("ui_scale", "Размер кнопок", "Button size",
            s("80%", "90%", "100%", "115%", "130%"), s("80%", "90%", "100%", "115%", "130%"), v(80, 90, 100, 115, 130), false, "", "");
    static final Option CAM = new Option("ui_cam", "Чувствительность камеры", "Camera sensitivity",
            s("50%", "75%", "100%", "130%", "170%", "220%"), s("50%", "75%", "100%", "130%", "170%", "220%"), v(50, 75, 100, 130, 170, 220), false, "", "");
    static final Option INVERT = new Option("ui_invert", "Инверсия по Y", "Invert Y",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false, "", "");
    static final Option GYRO = new Option("ui_gyro", "Гироскоп", "Gyro aim",
            s("Выкл", "При прицеле", "Всегда"), s("Off", "While aiming", "Always"), v(0, 1, 2), false, "", "");
    static final Option AIM = new Option("ui_aim_toggle", "Прицел", "Aim button",
            s("Удержание", "Переключение"), s("Hold", "Toggle"), v(0, 1), false, "", "");
    static final Option STICK = new Option("ui_stick_mode", "Стик", "Stick",
            s("Плавающий", "Фиксированный"), s("Floating", "Fixed"), v(0, 1), false,
            "Плавающий появляется там, где коснулись левой половины экрана.", "Floating appears wherever you touch the left half.");
    static final Option AUTORUN = new Option("ui_stick_run", "Автобег от стика", "Stick sprint",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false,
            "Стик вперёд до упора — бег. Дотяните до замка и отпустите — бег зафиксируется.", "");
    static final Option FIRE_LOOK = new Option("ui_fire_look", "Обзор с кнопки огня", "Look while firing",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false,
            "Палец на «Огне» одновременно ведёт камеру.", "");
    static final Option VIBRATE = new Option("ui_vibrate", "Вибрация", "Vibration",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false, "", "");
    static final Option SHOW_FPS = new Option("ui_fps", "Счётчик FPS", "FPS counter",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false, "", "");
    static final Option LABELS = new Option("ui_labels", "Подписи кнопок", "Button labels",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false, "", "");
    static final Option PAD_HIDE = new Option("ui_pad_hide", "Скрывать при геймпаде", "Hide with gamepad",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false, "", "");

    /**
     * Turnip's TU_DEBUG=sysmem (render straight to memory, no GMEM tiling). The port turns it on
     * only for GPUs named "Adreno810"/"Adreno825"; other Adreno 8xx (829, 830, 840...) render
     * striped garbage in GMEM mode. Not an ini key: the launcher sets the variable, and the port
     * keeps an existing TU_DEBUG (setenv without overwrite).
     */
    static final Option TURNIP = new Option("tu_sysmem", "Совместимость Turnip", "Turnip compatibility",
            s("Авто", "Вкл", "Выкл"), s("Auto", "On", "Off"), v(0, 1, 2), true,
            "Убирает полосы и мусор на Adreno 8xx. «Авто» включает её на Adreno 8xx.", "");

    static final Option[] GRAPHICS = {RES, TEX, DETAIL, FPS, BATTERY, DRIVER, TURNIP};
    static final Option[] AUDIO_LANG = {VOICE, LANG};
    static final Option[] TOUCH = {OPACITY, SCALE, CAM, INVERT, GYRO, AIM, STICK, AUTORUN, FIRE_LOOK, VIBRATE, LABELS, SHOW_FPS, PAD_HIDE};

    static final int MODE_QUALITY = 0, MODE_BALANCE = 1, MODE_SPEED = 2, MODE_CUSTOM = 3;

    /** Preset per phone tier and mode: {render_height, texture_skip_mips, game_detail, fps_limit, battery_saver}. */
    private static final int[][][] PRESETS = {
            // TIER_LOW: Helio G85..G99, Mali-G52/G57, Adreno 610..620, 4 GB
            {{720, 1, 1, 30, 0}, {540, 1, 1, 30, 0}, {540, 2, 0, 30, 1}},
            // TIER_MID: Adreno 640..725, Mali-G68/G610/G77, Xclipse 920, 6 GB
            {{900, 0, 2, 30, 0}, {720, 0, 1, 45, 0}, {540, 1, 0, 60, 0}},
            // TIER_HIGH: Adreno 730..750, Mali-G715, Immortalis
            {{1080, 0, 2, 45, 0}, {900, 0, 2, 60, 0}, {720, 0, 1, 60, 0}},
            // TIER_ULTRA: Adreno 8xx
            {{1080, 0, 2, 60, 0}, {1080, 0, 2, 60, 0}, {900, 0, 1, 60, 0}},
    };

    final SharedPreferences prefs;
    final Context ctx;

    GameConfig(Context c) {
        ctx = c.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int[] preset(int tier, int mode) {
        return PRESETS[Math.max(0, Math.min(3, tier))][Math.max(0, Math.min(2, mode))];
    }

    /** "720p · 30 FPS" summary for the mode buttons. */
    static String presetSummary(int tier, int mode) {
        int[] p = preset(tier, mode);
        return p[0] + "p · " + (p[3] == 0 ? "∞" : String.valueOf(p[3])) + " FPS";
    }

    int get(Option o) {
        int def = defaultValue(o);
        return prefs.getInt(o.key, def);
    }

    void set(Option o, int value) {
        prefs.edit().putInt(o.key, value).apply();
    }

    private int defaultValue(Option o) {
        switch (o.key) {
            case "ui_opacity": return 80;
            case "ui_scale": return 100;
            case "ui_cam": return 100;
            case "ui_vibrate": return 1;
            case "ui_stick_run": return 1;
            case "ui_fire_look": return 1;
            case "ui_pad_hide": return 1;
            case "ui_labels": return 1;
            case "game_voice": return Ui.english() ? 1 : 0;
            case "game_lang": return Ui.english() ? 1 : 0;
            default: return o.values[0];
        }
    }

    int mode() {
        return prefs.getInt("graphics_mode", -1);
    }

    /** First run: pick the mode for this phone so the game starts playable. */
    void ensureDefaults(DeviceProfile dp) {
        if (prefs.getInt("graphics_mode", -1) < 0) {
            applyMode(dp.tier, dp.tier == DeviceProfile.TIER_LOW ? MODE_SPEED : MODE_BALANCE);
            set(DRIVER, 0);
        }
        // 1.1.0 forced the system driver on Adreno 8xx; the port's own choice ("Auto": it
        // probes Turnip and the system driver itself) is what actually starts there. Undo it.
        if (prefs.getInt("defaults_v", 1) == 2 && dp.adrenoModel() >= 800 && get(DRIVER) == 1) set(DRIVER, 0);
        if (prefs.getInt("defaults_v", 1) < 3) prefs.edit().putInt("defaults_v", 3).apply();
    }

    /** Value for TU_DEBUG, "" to block the port's own default, or null to leave it unset. */
    String turnipDebug(DeviceProfile dp) {
        int v = get(TURNIP);
        if (v == 1 || (v == 0 && dp.adrenoModel() >= 800)) return "sysmem";
        if (v == 2) return "";
        return null;
    }

    // ------------------------------------------------------------------ start-up watchdog

    /** Marks a game start; {@link #markGameAlive()} confirms it survived the first seconds. */
    void markGameStart() {
        prefs.edit().putLong("game_started_at", System.currentTimeMillis()).putBoolean("game_alive", false).commit();
    }

    void markGameAlive() {
        prefs.edit().putBoolean("game_alive", true).commit();
    }

    /** True once if the last start ended before the game confirmed it was running. */
    boolean takeFailedStart() {
        long at = prefs.getLong("game_started_at", 0);
        boolean failed = at > 0 && !prefs.getBoolean("game_alive", true);
        prefs.edit().remove("game_started_at").putBoolean("game_alive", true).apply();
        return failed;
    }

    void applyMode(int tier, int mode) {
        int[] p = preset(tier, mode);
        prefs.edit()
                .putInt("graphics_mode", mode)
                .putInt(RES.key, p[0])
                .putInt(TEX.key, p[1])
                .putInt(DETAIL.key, p[2])
                .putInt(FPS.key, p[3])
                .putInt(BATTERY.key, p[4])
                .apply();
    }

    /** Called when the user edits a graphics row by hand. */
    void markCustom() {
        prefs.edit().putInt("graphics_mode", MODE_CUSTOM).apply();
    }

    String gamePath() {
        return prefs.getString("game_path", null);
    }

    void setGamePath(String p) {
        prefs.edit().putString("game_path", p).apply();
    }

    long playSeconds() {
        return prefs.getLong("play_seconds", 0);
    }

    void addPlaySeconds(long s) {
        if (s > 0) prefs.edit().putLong("play_seconds", playSeconds() + s).apply();
    }

    /**
     * Writes the engine rows into mafia2re.ini, keeping any other line a user or the port put
     * there. "vulkan_driver = auto" is expressed by leaving the key out.
     */
    void writeIni() {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) return;
        File f = new File(dir, "mafia2re.ini");
        StringBuilder sb = new StringBuilder();
        try {
            if (f.exists()) {
                for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    String k = line.split("=", 2)[0].trim();
                    boolean ours = false;
                    for (Option o : GRAPHICS) ours |= o.key.equals(k);
                    for (Option o : AUDIO_LANG) ours |= o.key.equals(k);
                    if (!ours && !line.trim().isEmpty()) sb.append(line).append('\n');
                }
            }
            for (Option o : GRAPHICS) {
                if (o == TURNIP) continue;   // environment variable, see turnipDebug()
                if (o == DRIVER) {
                    int d = get(DRIVER);
                    if (d == 1) sb.append("vulkan_driver = system\n");
                    else if (d == 2) sb.append("vulkan_driver = turnip\n");
                    continue;
                }
                sb.append(o.key).append(" = ").append(get(o)).append('\n');
            }
            for (Option o : AUDIO_LANG) sb.append(o.key).append(" = ").append(get(o)).append('\n');
            File tmp = new File(dir, "mafia2re.ini.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
                tmp.delete();
            }
        } catch (Exception ignored) {
        }
        // The port's own activity picks the display refresh rate from this index: 2 (45 FPS)
        // asks for a 90 Hz mode so frames pace evenly, everything else for 60 Hz.
        ctx.getSharedPreferences("controls", Context.MODE_PRIVATE).edit()
                .putInt("fps_limit", FPS.indexOf(get(FPS)))
                .putInt("ui_gyro", 0)
                .putInt("ui_fps", 0)
                .apply();
    }
}
