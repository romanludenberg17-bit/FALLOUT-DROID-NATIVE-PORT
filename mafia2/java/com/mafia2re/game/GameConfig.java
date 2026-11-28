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
            s("480p", "540p", "720p", "900p", "1080p"), s("480p", "540p", "720p", "900p", "1080p"), v(480, 540, 720, 900, 1080), true,
            "Внутреннее разрешение рендера. Главный рычаг производительности.", "Internal render height. The main performance knob.");
    /** render_max_aspect: the port caps the picture at 16:9 by default (black bars on 20:9 phones). */
    static final Option FULLSCREEN = new Option("render_max_aspect", "Полный экран", "Fullscreen",
            s("Вкл", "Выкл"), s("On", "Off"), v(0, 1), true,
            "Картинка на всю ширину экрана, без чёрных полос по бокам.", "");
    static final Option TEX = new Option("texture_skip_mips", "Текстуры", "Textures",
            s("Полные", "Средние", "Низкие"), s("Full", "Medium", "Low"), v(0, 1, 2), true,
            "«Низкие» экономят видеопамять: для телефонов с 4 ГБ.", "Low saves video memory: for 4 GB phones.");
    static final Option DETAIL = new Option("game_detail", "Детализация", "Detail",
            s("Низкая", "Средняя", "Высокая"), s("Low", "Medium", "High"), v(0, 1, 2), true,
            "Общий уровень: из него порт берёт тени, геометрию и AO, если у них стоит «Авто».", "Master level for shadows, geometry and AO set to Auto.");
    /** The port forces the game's own video options from these keys (absent = from game_detail). */
    static final Option SHADOWS = new Option("game_shadows", "Тени", "Shadows",
            s("Авто", "Низкие", "Средние", "Высокие"), s("Auto", "Low", "Medium", "High"), v(-1, 0, 1, 2), true,
            "Самая тяжёлая часть кадра: «Высокие» в городе — до 9 карт теней и тысячи лишних отрисовок.", "");
    static final Option GEOMETRY = new Option("game_geometry", "Геометрия", "Geometry",
            s("Авто", "Низкая", "Средняя", "Высокая"), s("Auto", "Low", "Medium", "High"), v(-1, 0, 1, 2), true,
            "Дальность и детальность моделей: нагружает процессор в городе и за рулём.", "");
    static final Option AO = new Option("game_ao", "Затенение (AO)", "Ambient occlusion",
            s("Авто", "Выкл", "Вкл"), s("Auto", "Off", "On"), v(-1, 0, 1), true,
            "Мягкие тени в углах. Нагружает видеочип.", "");
    static final Option FPS = new Option("fps_limit", "Лимит FPS", "FPS limit",
            s("Нет", "30", "45", "60", "90", "120"), s("None", "30", "45", "60", "90", "120"), v(0, 30, 45, 60, 90, 120), true,
            "30 FPS — ровно и без перегрева на слабых чипах. 90 и 120 — для экранов 90/120 Гц и мощных телефонов.", "");
    /**
     * Vertical sync. The game asks D3D9 for interval 0 already; the port then caps the display at
     * 60 Hz. Off: d3d9.presentInterval = 0 (Vulkan MAILBOX: a late frame shows at the next refresh,
     * no wait for the next whole vsync) and the screen's highest refresh rate. On: FIFO, interval 1.
     */
    static final Option VSYNC = new Option("vsync", "Вертикальная синхронизация", "V-Sync",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), true,
            "Выкл — кадр не ждёт обновления экрана, и экран работает на максимальной частоте (90/120 Гц): выше FPS и меньше задержка. "
                    + "Вкл — ровнее на некоторых драйверах, но при просадке FPS падает ступенькой.", "");
    static final Option BATTERY = new Option("battery_saver", "Экономия батареи", "Battery saver",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), true,
            "Снижает нагрузку в меню и на паузе.", "Lowers the load in menus and while paused.");
    static final Option DRIVER = new Option("vulkan_driver", "Драйвер Vulkan", "Vulkan driver",
            s("Авто", "Системный", "Turnip"), s("Auto", "System", "Turnip"), v(0, 1, 2), true,
            "«Авто» — порт сам выбирает. На Adreno системный драйвер Qualcomm с DXVK не работает (ошибка памяти) — оставьте «Авто».", "");
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
    static final Option SMART_HIDE = new Option("ui_smart_hide", "Умное скрытие кнопок", "Smart hide",
            s("Выкл", "Вкл"), s("Off", "On"), v(0, 1), false,
            "Без касаний 4 с кнопки гаснут (стик, огонь и прицел — наполовину). При прицеливании лишние кнопки прячутся.", "");
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
    static final Option TURNIP = new Option("tu_debug_mode", "Совместимость Turnip", "Turnip compatibility",
            s("Авто", "sysmem", "noubwc", "оба", "Выкл"), s("Auto", "sysmem", "noubwc", "both", "Off"), v(0, 1, 2, 3, 4), true,
            "Мусор и полосы на Adreno 8xx: без тайлового рендера (sysmem) и без сжатия (noubwc). «Авто» на Adreno 8xx — оба.", "");

    /** Which bundled Turnip build: the port's default or the newer "freedreno_next". */
    static final Option TURNIP_BUILD = new Option("turnip_build", "Версия Turnip", "Turnip build",
            s("Авто", "Стандартная", "Новая", "DD-Turnip"), s("Auto", "Standard", "Newer", "DD-Turnip"), v(0, 1, 2, 3), true,
            "DD-Turnip (Mesa 27) читает раскладку экрана через IMapper 5 и знает Adreno 8xx. «Авто» берёт его на Adreno 8xx.", "");

    static final int BUILD_PORT = 1, BUILD_NEXT = 2, BUILD_DD = 3;

    /** Set by GameEnv: whether lib/…/libvulkan_ddturnip.so is in this APK. */
    static boolean ddBundled = true;

    /** APEX cloth simulation (coats, clothes): heavy on the CPU of entry chips. */
    static final Option CLOTH = new Option("apex_cloth", "Ткань одежды (APEX)", "APEX cloth",
            s("Авто", "Вкл", "Выкл"), s("Auto", "On", "Off"), v(0, 1, 2), true,
            "Расчёт ткани нагружает процессор. «Авто» выключает её на слабых телефонах в «Скорости» и при 4 ГБ.", "");

    static final Option[] GRAPHICS = {RES, FULLSCREEN, TEX, DETAIL, SHADOWS, GEOMETRY, AO, FPS, VSYNC, BATTERY, CLOTH, DRIVER, TURNIP_BUILD, TURNIP};

    /** Engine keys the launcher derives from the phone (written to the ini, not shown as rows). */
    static final String[] AUTO_KEYS = {"max_aniso", "texture_skip_min_size", "decode_16bit", "no_apex_clothing"};
    static final Option[] AUDIO_LANG = {VOICE, LANG};
    static final Option[] TOUCH = {OPACITY, SCALE, CAM, INVERT, GYRO, AIM, STICK, AUTORUN, FIRE_LOOK, VIBRATE, LABELS, SMART_HIDE, SHOW_FPS, PAD_HIDE};

    static final int MODE_QUALITY = 0, MODE_BALANCE = 1, MODE_SPEED = 2, MODE_CUSTOM = 3;

    /**
     * Preset per phone tier and mode:
     * {render_height, texture_skip_mips, game_detail, fps_limit, battery_saver, game_shadows, game_geometry, game_ao}.
     * The last three are what game_detail stood for (shadows {0,1,2}, geometry {0,1,2}, AO {0,0,1});
     * 1.3.4 splits them: shadows at High cost Adreno 829 half its frame rate in the city.
     */
    private static final int[][][] PRESETS = {
            // TIER_LOW: Helio G85..G99, Mali-G52/G57, Adreno 610..620, 4 GB. No shadows outside Quality.
            {{720, 1, 1, 30, 0, 0, 1, 0}, {540, 1, 0, 30, 0, 0, 0, 0}, {540, 2, 0, 30, 1, 0, 0, 0}},
            // TIER_MID: Adreno 640..725, Mali-G68/G610/G77, Xclipse 920, 6 GB
            {{900, 0, 2, 30, 0, 1, 1, 0}, {720, 1, 1, 45, 0, 0, 1, 0}, {540, 1, 0, 60, 0, 0, 0, 0}},
            // TIER_HIGH: Adreno 730..750, Mali-G715, Immortalis
            {{1080, 0, 2, 45, 0, 1, 2, 1}, {900, 0, 2, 60, 0, 1, 1, 0}, {720, 0, 1, 60, 0, 0, 1, 0}},
            // TIER_ULTRA: Adreno 8xx. Balance / Speed keep 1080p and save on textures, shadows, geometry.
            {{1080, 0, 2, 60, 0, 2, 2, 1}, {1080, 1, 2, 60, 0, 1, 1, 0}, {1080, 1, 1, 60, 0, 0, 1, 0}},
    };

    final SharedPreferences prefs;
    final Context ctx;

    GameConfig(Context c) {
        ctx = c.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int[] preset(int tier, int mode) {
        return PRESETS[Math.max(0, Math.min(3, tier))][Math.max(0, Math.min(2, mode))].clone();
    }

    /**
     * The preset for this phone: the tier table, then the weakest GPUs drop to 480p in Speed,
     * and the RAM class caps texture quality (4 GB: Medium at best, Low in Speed; 6 GB: Medium).
     */
    static int[] preset(DeviceProfile dp, int mode) {
        int[] p = preset(dp.tier, mode);
        if (mode == MODE_SPEED && dp.entryGpu()) p[0] = 480;
        int ram = dp.ramClass();
        if (ram == 4) p[1] = Math.max(p[1], mode == MODE_SPEED ? 2 : 1);
        else if (ram == 6) p[1] = Math.max(p[1], 1);
        return p;
    }

    /** "720p · 30 FPS" summary for the mode buttons. */
    static String presetSummary(DeviceProfile dp, int mode) {
        int[] p = preset(dp, mode);
        return p[0] + "p · " + (p[3] == 0 ? "∞" : String.valueOf(p[3])) + " FPS";
    }

    /** Engine keys derived from the phone and the mode; remembered for in-game ini rewrites. */
    void computeAutoKeys(DeviceProfile dp) {
        int ram = dp.ramClass();
        int aniso = dp.tier >= DeviceProfile.TIER_ULTRA ? 16 : dp.tier == DeviceProfile.TIER_HIGH ? 8 : dp.tier == DeviceProfile.TIER_MID ? 4 : 2;
        if (dp.tier >= DeviceProfile.TIER_ULTRA && mode() != MODE_QUALITY) aniso = 8;
        if (ram == 4) aniso = Math.min(aniso, 2);
        else if (ram == 6) aniso = Math.min(aniso, 4);
        int cloth = get(CLOTH);
        // Cloth is pure CPU: off on entry chips except in Quality, on mid chips in Speed, and with 4 GB.
        boolean noCloth = cloth == 2 || (cloth == 0 && (ram == 4
                || (dp.tier == DeviceProfile.TIER_LOW && mode() != MODE_QUALITY)
                || (dp.tier == DeviceProfile.TIER_MID && mode() == MODE_SPEED)));
        prefs.edit()
                .putInt("x.max_aniso", aniso)
                .putInt("x.texture_skip_min_size", ram == 4 ? 128 : 256)
                // GPUs without BC (Mali, Xclipse, PowerVR) get DXT decoded on the CPU: 16-bit halves it.
                .putInt("x.decode_16bit", !dp.isAdreno() && (ram <= 6 || dp.tier <= DeviceProfile.TIER_MID) ? 1 : 0)
                .putInt("x.no_apex_clothing", noCloth ? 1 : 0)
                .apply();
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
            case "ui_smart_hide": return 1;
            case "ui_fps": return 1;
            case "game_voice": return Ui.english() ? 1 : 0;
            case "game_lang": return Ui.english() ? 1 : 0;
            default: return o.values[0];
        }
    }

    /**
     * Display refresh rate to ask for. V-Sync off or no FPS cap: the screen's highest rate (late
     * frames wait 8 ms at 120 Hz instead of 17 ms); with a cap, a rate that is a multiple of it so
     * frames pace evenly. Battery saver and entry phones take the lowest such rate.
     */
    static float pickRefresh(float[] rates, int fpsLimit, boolean vsync, boolean frugal) {
        float max = -1, maxMul = -1, minMul = -1;
        for (float r : rates) {
            max = Math.max(max, r);
            if (fpsLimit > 0 && r + 0.5f >= fpsLimit && Math.abs(r / fpsLimit - Math.round(r / fpsLimit)) < 0.02f) {
                maxMul = Math.max(maxMul, r);
                minMul = minMul < 0 ? r : Math.min(minMul, r);
            }
        }
        if (fpsLimit <= 0 || maxMul < 0) return max;
        if (frugal) return minMul;
        return vsync ? maxMul : max;
    }

    int mode() {
        return prefs.getInt("graphics_mode", -1);
    }

    /** First run: pick the mode for this phone so the game starts playable. */
    void ensureDefaults(DeviceProfile dp) {
        if (prefs.getInt("graphics_mode", -1) < 0) {
            applyMode(dp, dp.tier == DeviceProfile.TIER_LOW ? MODE_SPEED : MODE_BALANCE);
            set(DRIVER, 0);
        }
        // 1.1.0 forced the system driver on Adreno 8xx; the port's own choice ("Auto": it
        // probes Turnip and the system driver itself) is what actually starts there. Undo it.
        if (prefs.getInt("defaults_v", 1) == 2 && dp.adrenoModel() >= 800 && get(DRIVER) == 1) set(DRIVER, 0);
        // 1.2.x logs: DXVK cannot allocate memory on Qualcomm's own Vulkan driver (abort at start).
        if (prefs.getInt("defaults_v", 1) < 4 && dp.isAdreno() && get(DRIVER) == 1) applyAdrenoVariant(0);
        if (prefs.getInt("defaults_v", 1) < 4) prefs.edit().putInt("defaults_v", 4).apply();
        if (prefs.getInt("defaults_v", 1) < 5) {
            if (dp.adrenoModel() >= 800) applyAdrenoVariant(0);
            prefs.edit().putInt("defaults_v", 5).apply();
        }
        // 1.3.4: presets carry shadows / geometry / AO (Balance on Adreno 8xx was all-High 1080p).
        if (prefs.getInt("defaults_v", 1) < 6) {
            int m = mode();
            if (m >= MODE_QUALITY && m <= MODE_SPEED) applyMode(dp, m);
            prefs.edit().putInt("defaults_v", 6).commit();
        }
        // 1.3.5: Balance / Speed on Adreno 8xx went back to 1080p with lighter textures.
        if (prefs.getInt("defaults_v", 1) < 7) {
            int m = mode();
            if (m >= MODE_QUALITY && m <= MODE_SPEED) applyMode(dp, m);
            prefs.edit().putInt("defaults_v", 7).commit();
        }
    }

    /** Value for TU_DEBUG, "" to block the port's own default, or null to leave it unset. */
    String turnipDebug(DeviceProfile dp) {
        if (get(DRIVER) == 1) return null;    // TU_DEBUG only matters to Turnip
        switch (get(TURNIP)) {
            case 1: return "sysmem";
            case 2: return "noubwc";
            case 3: return "sysmem,noubwc";
            case 4: return "";
            default:
                // DD-Turnip knows the real buffer layout: no workaround flags needed.
                if (turnipBuild(dp) == BUILD_DD) return null;
                return dp.adrenoModel() >= 800 ? "sysmem,noubwc" : null;
        }
    }

    /** Which Turnip: 0 = none (system driver), BUILD_PORT, BUILD_NEXT or BUILD_DD. */
    int turnipBuild(DeviceProfile dp) {
        if (get(DRIVER) == 1 || !dp.isAdreno()) return 0;
        int v = get(TURNIP_BUILD);
        if (v == BUILD_DD && !ddBundled) v = 0;
        if (v != 0) return v;
        if (dp.adrenoModel() >= 800) return ddBundled ? BUILD_DD : BUILD_NEXT;
        return BUILD_PORT;
    }
    /** Quick graphics variants for Adreno, switchable from the in-game port menu. */
    static final String[] ADRENO_VARIANTS = {
            "DD-Turnip (Mesa 27) — рекомендуется",
            "Новый Turnip + совместимость",
            "Новый Turnip без флагов",
            "Стандартный Turnip + noubwc",
            "Стандартный Turnip без флагов",
    };

    /** {vulkan_driver, turnip_build, tu_debug_mode} for each variant. */
    private static final int[][] ADRENO_VARIANT_VALUES = {{0, 3, 0}, {0, 2, 3}, {0, 2, 4}, {0, 1, 2}, {0, 1, 4}};

    void applyAdrenoVariant(int i) {
        int[] v = ADRENO_VARIANT_VALUES[i];
        prefs.edit().putInt(DRIVER.key, v[0]).putInt(TURNIP_BUILD.key, v[1]).putInt(TURNIP.key, v[2]).commit();
    }

    /** Index of the variant matching the current settings, or -1. */
    int adrenoVariant() {
        for (int i = 0; i < ADRENO_VARIANT_VALUES.length; i++) {
            int[] v = ADRENO_VARIANT_VALUES[i];
            if (get(DRIVER) == v[0] && get(TURNIP_BUILD) == v[1] && get(TURNIP) == v[2]) return i;
        }
        return -1;
    }

    /** True to point the port at lib/…/libvulkan_freedreno_next.so (MAFIA2RE_CUSTOM_DRIVER). */
    boolean useNextTurnip(DeviceProfile dp) {
        return turnipBuild(dp) == BUILD_NEXT;
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

    void applyMode(DeviceProfile dp, int mode) {
        int[] p = preset(dp, mode);
        prefs.edit()
                .putInt("graphics_mode", mode)
                .putInt(RES.key, p[0])
                .putInt(TEX.key, p[1])
                .putInt(DETAIL.key, p[2])
                .putInt(FPS.key, p[3])
                .putInt(BATTERY.key, p[4])
                .putInt(SHADOWS.key, p[5])
                .putInt(GEOMETRY.key, p[6])
                .putInt(AO.key, p[7])
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
                    for (String a : AUTO_KEYS) ours |= a.equals(k);
                    ours |= k.equals("dxvk_config");
                    if (!ours && !line.trim().isEmpty()) sb.append(line).append('\n');
                }
            }
            for (Option o : GRAPHICS) {
                if (o == TURNIP || o == TURNIP_BUILD || o == CLOTH) continue;   // env / auto keys
                if (o == VSYNC) {
                    // The port copies dxvk_config into DXVK_CONFIG (commas become ';').
                    sb.append("dxvk_config = d3d9.presentInterval = ").append(get(VSYNC) != 0 ? 1 : 0).append('\n');
                    continue;
                }
                if (o == DRIVER) {
                    int d = get(DRIVER);
                    if (d == 1) sb.append("vulkan_driver = system\n");
                    else if (d == 2) sb.append("vulkan_driver = turnip\n");
                    continue;
                }
                if ((o == SHADOWS || o == GEOMETRY || o == AO) && get(o) < 0) continue;  // Auto: from game_detail
                sb.append(o.key).append(" = ").append(get(o)).append('\n');
            }
            for (Option o : AUDIO_LANG) sb.append(o.key).append(" = ").append(get(o)).append('\n');
            for (String a : AUTO_KEYS) {
                if (prefs.contains("x." + a)) sb.append(a).append(" = ").append(prefs.getInt("x." + a, 0)).append('\n');
            }
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
