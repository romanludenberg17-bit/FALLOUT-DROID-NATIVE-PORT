package com.mafia2re.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsdl.app.SDLActivity;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Renders every screen to build/shots/*.png (real Skia via Robolectric native graphics) and
 * checks the input protocol the overlay speaks to the game.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w892dp-h412dp-land-xxhdpi")
public class M2Test {
    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        DeviceProfile.testOverride = DeviceProfile.forTest("Helio G85 (MT6769Z)", "Mali-G52 MC2", 4 * 1024 - 300, 2000, true);
        SDLActivity.events.clear();
        TouchControls.state = 8 | 1;
        TouchControls.fpsLimit = -1;
        ctx.getSharedPreferences(GameConfig.PREFS, 0).edit().clear().commit();
        ctx.getSharedPreferences(ControlLayout.PREFS, 0).edit().clear().commit();
    }

    // ------------------------------------------------------------------ helpers

    static void shot(View root, String name) throws Exception {
        ShadowLooper.idleMainLooper();
        root.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getWidth(), root.getHeight());
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File dir = new File(System.getProperty("m2.shots", "build/shots"));
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private File fakeGame(boolean complete) throws Exception {
        File root = new File(ctx.getCacheDir(), "Mafia II");
        for (String r : GameFiles.REQUIRED) {
            if (!complete && r.startsWith("physx")) continue;
            File f = new File(root, r);
            if (r.endsWith("sds")) f.mkdirs();
            else {
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), new byte[]{1});
            }
        }
        return root;
    }

    private TextView findText(View v, String s) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(s)) return (TextView) v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                TextView t = findText(((ViewGroup) v).getChildAt(i), s);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static void touch(View v, int action, int pid, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, action, x, y, 0);
        v.dispatchTouchEvent(e);
        e.recycle();
        SystemClock.sleep(16);
    }

    private static <T extends View> T find(View v, Class<T> cls) {
        if (cls.isInstance(v)) return cls.cast(v);
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                T t = find(((ViewGroup) v).getChildAt(i), cls);
                if (t != null) return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ launcher

    @Test
    public void launcherScreens() throws Exception {
        ActivityController<LauncherActivity> c = Robolectric.buildActivity(LauncherActivity.class).setup();
        LauncherActivity a = c.get();
        View root = a.getWindow().getDecorView();
        shot(root, "01_launcher_first_run");

        GameConfig cfg = new GameConfig(a);
        cfg.setGamePath(fakeGame(true).getAbsolutePath());
        cfg.addPlaySeconds(19 * 60);
        c.pause().resume();
        shot(root, "02_launcher_ready");

        String[] tabs = {Ui.t("НАСТРОЙКИ", "SETTINGS"), Ui.t("УПРАВЛЕНИЕ", "CONTROLS"), Ui.t("ФАЙЛЫ ИГРЫ", "GAME FILES"), Ui.t("О ПОРТЕ", "ABOUT")};
        String[] names = {"03_settings", "04_controls", "05_files", "06_about"};
        for (int i = 0; i < tabs.length; i++) {
            TextView t = findText(root, tabs[i]);
            assertNotNull(tabs[i], t);
            t.performClick();
            shot(root, names[i]);
        }
        // The credit to the port's author must stay visible.
        assertNotNull(findText(root, "VinTeRuS"));
    }

    @Test
    @Config(qualifiers = "ru-rRU-w892dp-h412dp-land-xxhdpi")
    public void launcherScreensRussian() throws Exception {
        java.util.Locale.setDefault(new java.util.Locale("ru", "RU"));
        try {
            GameConfig cfg = new GameConfig(ctx);
            cfg.setGamePath(fakeGame(true).getAbsolutePath());
            cfg.addPlaySeconds(19 * 60);
            File saves = GameFiles.savesDir(ctx);
            saves.mkdirs();
            Files.write(new File(saves, "slot0.sav").toPath(), new byte[]{1});
            Files.write(new File(saves, "slot1.sav").toPath(), new byte[]{1});
            ActivityController<LauncherActivity> c = Robolectric.buildActivity(LauncherActivity.class).setup();
            View root = c.get().getWindow().getDecorView();
            shot(root, "20_ru_play");
            assertNotNull(findText(root, "ИГРАТЬ"));
            findText(root, "НАСТРОЙКИ").performClick();
            shot(root, "21_ru_settings");
            findText(root, "УПРАВЛЕНИЕ").performClick();
            shot(root, "22_ru_controls");
        } finally {
            java.util.Locale.setDefault(java.util.Locale.US);
        }
    }

    @Test
    public void entryPhoneGetsSpeedPreset() {
        GameConfig cfg = new GameConfig(ctx);
        DeviceProfile g85 = DeviceProfile.testOverride;
        assertEquals(DeviceProfile.TIER_LOW, g85.tier);
        cfg.ensureDefaults(g85);
        assertEquals(GameConfig.MODE_SPEED, cfg.mode());
        assertEquals(480, cfg.get(GameConfig.RES));         // Mali-G52: 480p in Speed
        assertEquals(2, cfg.get(GameConfig.TEX));
        assertEquals(30, cfg.get(GameConfig.FPS));
    }

    @Test
    public void driverStaysAutoAndUndoes110Migration() {
        DeviceProfile a829 = DeviceProfile.forTest("SM8845", "Adreno (TM) 829", 12000, 4000, true);
        assertEquals(829, a829.adrenoModel());
        GameConfig cfg = new GameConfig(ctx);
        cfg.ensureDefaults(a829);
        assertEquals(0, cfg.get(GameConfig.DRIVER));
        // An install migrated by 1.1.0 (defaults_v = 2, forced "system") goes back to Auto.
        ctx.getSharedPreferences(GameConfig.PREFS, 0).edit().clear().putInt("graphics_mode", 1)
                .putInt("vulkan_driver", 1).putInt("defaults_v", 2).commit();
        new GameConfig(ctx).ensureDefaults(a829);
        assertEquals(0, new GameConfig(ctx).get(GameConfig.DRIVER));
        // A choice made after that is kept.
        GameConfig c3 = new GameConfig(ctx);
        c3.set(GameConfig.DRIVER, 1);
        c3.ensureDefaults(a829);
        assertEquals(1, c3.get(GameConfig.DRIVER));
    }

    @Test
    public void turnipSettingsForAdreno8xx() throws Exception {
        GameConfig.ddBundled = true;
        GameConfig cfg = new GameConfig(ctx);
        DeviceProfile a829 = DeviceProfile.forTest("SM8845", "Adreno (TM) 829", 12000, 4000, true);
        DeviceProfile a740 = DeviceProfile.forTest("SM8550", "Adreno (TM) 740", 12000, 3200, true);
        DeviceProfile mali = DeviceProfile.forTest("MT6769Z", "Mali-G52 MC2", 4000, 2000, true);
        // Adreno 8xx: DD-Turnip, no workaround flags.
        assertEquals(GameConfig.BUILD_DD, cfg.turnipBuild(a829));
        assertNull(cfg.turnipDebug(a829));
        // Without DD-Turnip in the APK: the port's newer Turnip with the workaround flags.
        GameConfig.ddBundled = false;
        assertEquals(GameConfig.BUILD_NEXT, cfg.turnipBuild(a829));
        assertEquals("sysmem,noubwc", cfg.turnipDebug(a829));
        GameConfig.ddBundled = true;
        // Adreno 7xx keeps the port's own Turnip; Mali has none.
        assertEquals(GameConfig.BUILD_PORT, cfg.turnipBuild(a740));
        assertNull(cfg.turnipDebug(a740));
        assertEquals(0, cfg.turnipBuild(mali));
        cfg.set(GameConfig.TURNIP, 2);
        assertEquals("noubwc", cfg.turnipDebug(a740));
        cfg.set(GameConfig.TURNIP, 4);
        assertEquals("", cfg.turnipDebug(a829));
        cfg.set(GameConfig.TURNIP_BUILD, 1);
        assertEquals(GameConfig.BUILD_PORT, cfg.turnipBuild(a829));
        cfg.writeIni();
        String ini = new String(Files.readAllBytes(new File(ctx.getExternalFilesDir(null), "mafia2re.ini").toPath()), StandardCharsets.UTF_8);
        assertFalse(ini, ini.contains("tu_debug_mode") || ini.contains("turnip_build"));
    }

    @Test
    public void adrenoVariantsMapToDriverSettings() {
        GameConfig.ddBundled = true;
        GameConfig cfg = new GameConfig(ctx);
        DeviceProfile a829 = DeviceProfile.forTest("SM8845", "Adreno (TM) 829", 12000, 4000, true);
        cfg.applyAdrenoVariant(0);                       // DD-Turnip
        assertEquals(0, cfg.adrenoVariant());
        assertEquals(GameConfig.BUILD_DD, cfg.turnipBuild(a829));
        assertNull(cfg.turnipDebug(a829));
        cfg.applyAdrenoVariant(1);                       // newer port Turnip + both flags
        assertTrue(cfg.useNextTurnip(a829));
        assertEquals("sysmem,noubwc", cfg.turnipDebug(a829));
        cfg.applyAdrenoVariant(2);
        assertEquals("", cfg.turnipDebug(a829));
        cfg.applyAdrenoVariant(3);
        assertFalse(cfg.useNextTurnip(a829));
        assertEquals("noubwc", cfg.turnipDebug(a829));
        cfg.applyAdrenoVariant(4);
        assertEquals(GameConfig.BUILD_PORT, cfg.turnipBuild(a829));
        assertEquals("", cfg.turnipDebug(a829));
        assertEquals(4, cfg.adrenoVariant());
    }

    @Test
    public void adreno8xxInstallsMoveToDdTurnip() {
        DeviceProfile a829 = DeviceProfile.forTest("SM8845", "Adreno (TM) 829", 12000, 4000, true);
        ctx.getSharedPreferences(GameConfig.PREFS, 0).edit().clear().putInt("graphics_mode", 1)
                .putInt("vulkan_driver", 0).putInt("turnip_build", 2).putInt("tu_debug_mode", 3).putInt("defaults_v", 4).commit();
        GameConfig cfg = new GameConfig(ctx);
        cfg.ensureDefaults(a829);
        assertEquals(0, cfg.adrenoVariant());
    }

    @Test
    public void fullscreenLiftsThe169Cap() throws Exception {
        GameConfig cfg = new GameConfig(ctx);
        cfg.writeIni();
        assertTrue(readIni().contains("render_max_aspect = 0\n"));     // default: full width
        cfg.set(GameConfig.FULLSCREEN, 1);
        cfg.writeIni();
        String ini = readIni();
        assertTrue(ini, ini.contains("render_max_aspect = 1\n"));
        assertEquals(ini.indexOf("render_max_aspect"), ini.lastIndexOf("render_max_aspect"));
    }

    @Test
    public void aimingHidesNonCombatButtonsAndFpsShows() throws Exception {
        ActivityController<M2GameActivity> c = game();
        View root = c.get().getWindow().getDecorView();
        ControlsOverlay o = find(root, ControlsOverlay.class);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot(root, "18_game_fps");
        ControlLayout.Element aim = o.layout.byId("aim"), car = o.layout.byId("car_in");
        touch(o, MotionEvent.ACTION_DOWN, 0, aim.cx, aim.cy);
        SDLActivity.events.clear();
        // While the aim finger is down, a tap where "Сесть в машину" is does not press F.
        MotionEvent d = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, new MotionEvent.PointerProperties[]{pp(0), pp(1)}, new MotionEvent.PointerCoords[]{pc(aim.cx, aim.cy), pc(car.cx, car.cy)}, 0, 0, 1, 1, 0, 0, 0, 0);
        o.dispatchTouchEvent(d);
        assertFalse(SDLActivity.events.toString(), SDLActivity.events.contains("down 34"));
        shot(root, "19_game_aiming");
    }

    private static MotionEvent.PointerProperties pp(int id) {
        MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
        p.id = id;
        p.toolType = MotionEvent.TOOL_TYPE_FINGER;
        return p;
    }

    private static MotionEvent.PointerCoords pc(float x, float y) {
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = x;
        c.y = y;
        c.pressure = 1;
        c.size = 1;
        return c;
    }

    @Test
    public void actionTapIsEHoldIsEnterCar() {
        ActivityController<M2GameActivity> c = game();
        ControlsOverlay o = find(c.get().getWindow().getDecorView(), ControlsOverlay.class);
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(o.layout.byId("car_in").hidden);              // its own button is hidden by default
        assertTrue(o.layout.byId("grenade").hidden);
        ControlLayout.Element act = o.layout.byId("action");
        // Tap: E down, then up a moment later; no F.
        SDLActivity.events.clear();
        touch(o, MotionEvent.ACTION_DOWN, 0, act.cx, act.cy);
        touch(o, MotionEvent.ACTION_UP, 0, act.cx, act.cy);
        ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("[down 33, up 33]", SDLActivity.events.toString());
        // Hold: F while held, no E.
        SDLActivity.events.clear();
        touch(o, MotionEvent.ACTION_DOWN, 0, act.cx, act.cy);
        ShadowLooper.idleMainLooper(ControlsOverlay.HOLD_MS + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("[down 34]", SDLActivity.events.toString());
        touch(o, MotionEvent.ACTION_UP, 0, act.cx, act.cy);
        assertEquals("[down 34, up 34]", SDLActivity.events.toString());
        // Weapon hold = grenade (key 1).
        ControlLayout.Element wpn = o.layout.byId("weapon");
        SDLActivity.events.clear();
        touch(o, MotionEvent.ACTION_DOWN, 0, wpn.cx, wpn.cy);
        ShadowLooper.idleMainLooper(ControlsOverlay.HOLD_MS + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(o, MotionEvent.ACTION_UP, 0, wpn.cx, wpn.cy);
        assertEquals("[down 8, up 8]", SDLActivity.events.toString());
    }

    @Test
    public void failedStartIsReportedOnce() {
        GameConfig cfg = new GameConfig(ctx);
        cfg.markGameStart();
        assertTrue(new GameConfig(ctx).takeFailedStart());
        assertFalse(new GameConfig(ctx).takeFailedStart());
        cfg.markGameStart();
        cfg.markGameAlive();
        assertFalse(new GameConfig(ctx).takeFailedStart());
    }

    @Test
    public void snapdragon8xxBalanceIsLighterThanQuality() {
        DeviceProfile sd8g5 = DeviceProfile.forTest("SM8845", "Adreno (TM) 829", 12 * 1024, 3800, true);
        int[] q = GameConfig.preset(sd8g5, GameConfig.MODE_QUALITY), b = GameConfig.preset(sd8g5, GameConfig.MODE_BALANCE);
        assertTrue(b[0] < q[0]);
        assertTrue(b[5] < q[5]);       // shadows
        assertEquals(0, b[7]);         // no AO in Balance
        GameConfig cfg = new GameConfig(ctx);
        cfg.applyMode(sd8g5, GameConfig.MODE_BALANCE);
        cfg.writeIni();
        String ini = readIni();
        assertTrue(ini, ini.contains("game_shadows = 1\n"));
        assertTrue(ini, ini.contains("game_ao = 0\n"));
        assertTrue(ini, ini.contains("render_height = 900\n"));
        cfg.set(GameConfig.SHADOWS, -1);   // Auto: the port derives it from game_detail
        cfg.writeIni();
        assertFalse(readIni().contains("game_shadows"));
    }

    @Test
    public void profilesForHelioAndRam() {
        DeviceProfile g85 = DeviceProfile.testOverride;                                          // Mali-G52, ~4 GB
        DeviceProfile g99 = DeviceProfile.forTest("MT6789", "Mali-G57 MC2", 8 * 1024 - 300, 2200, true);
        DeviceProfile g100_6 = DeviceProfile.forTest("MT6789V", "Mali-G57 MC2", 6 * 1024 - 300, 2200, true);
        DeviceProfile sd8g2 = DeviceProfile.forTest("SM8550", "Adreno (TM) 740", 12 * 1024, 3200, true);
        assertTrue(g85.entryGpu());
        assertFalse(g99.entryGpu());
        assertEquals(4, g85.ramClass());
        assertEquals(6, g100_6.ramClass());
        assertEquals(480, GameConfig.preset(g85, GameConfig.MODE_SPEED)[0]);
        assertEquals(540, GameConfig.preset(g99, GameConfig.MODE_SPEED)[0]);
        assertEquals(2, GameConfig.preset(g85, GameConfig.MODE_SPEED)[1]);        // 4 GB: low textures in Speed
        assertEquals(1, GameConfig.preset(g85, GameConfig.MODE_QUALITY)[1]);      // 4 GB: medium at best
        assertTrue(GameConfig.preset(g100_6, GameConfig.MODE_QUALITY)[1] >= 1);  // 6 GB: medium at best
        assertEquals(0, GameConfig.preset(sd8g2, GameConfig.MODE_QUALITY)[1]);

        GameConfig cfg = new GameConfig(ctx);
        cfg.applyMode(g85, GameConfig.MODE_SPEED);
        cfg.computeAutoKeys(g85);
        cfg.writeIni();
        String ini = readIni();
        assertTrue(ini, ini.contains("max_aniso = 2\n"));
        assertTrue(ini, ini.contains("texture_skip_min_size = 128\n"));
        assertTrue(ini, ini.contains("decode_16bit = 1\n"));
        assertTrue(ini, ini.contains("no_apex_clothing = 1\n"));

        cfg.set(GameConfig.CLOTH, 1);
        cfg.applyMode(sd8g2, GameConfig.MODE_BALANCE);
        cfg.computeAutoKeys(sd8g2);
        cfg.writeIni();
        ini = readIni();
        assertTrue(ini, ini.contains("max_aniso = 8\n"));
        assertTrue(ini, ini.contains("texture_skip_min_size = 256\n"));
        assertTrue(ini, ini.contains("decode_16bit = 0\n"));
        assertTrue(ini, ini.contains("no_apex_clothing = 0\n"));
        // Each key once, even after several rewrites.
        assertEquals(ini.indexOf("max_aniso"), ini.lastIndexOf("max_aniso"));
    }

    private String readIni() throws RuntimeException {
        try {
            return new String(Files.readAllBytes(new File(ctx.getExternalFilesDir(null), "mafia2re.ini").toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void tiersForCommonChips() {
        assertEquals(DeviceProfile.TIER_LOW, DeviceProfile.forTest("SM6225", "Adreno (TM) 610", 6000, 2400, true).tier);
        assertEquals(DeviceProfile.TIER_MID, DeviceProfile.forTest("SM7325", "Adreno (TM) 642L", 8000, 2400, true).tier);
        assertEquals(DeviceProfile.TIER_MID, DeviceProfile.forTest("MT6877", "Mali-G68 MC4", 8000, 2400, true).tier);
        assertEquals(DeviceProfile.TIER_MID, DeviceProfile.forTest("s5e8835", "Samsung Xclipse 540", 8000, 2400, true).tier);
        assertEquals(DeviceProfile.TIER_HIGH, DeviceProfile.forTest("SM8550", "Adreno (TM) 740", 12000, 3200, true).tier);
        assertEquals(DeviceProfile.TIER_ULTRA, DeviceProfile.forTest("SM8750", "Adreno (TM) 830", 12000, 4300, true).tier);
        assertEquals(DeviceProfile.TIER_LOW, DeviceProfile.forTest("MT6789", "Mali-G57 MC2", 8000, 2200, true).tier);
        assertFalse(DeviceProfile.forTest("SM6125", "Adreno (TM) 610", 4000, 2000, false).cpuSupported());
    }

    @Test
    public void iniKeepsForeignLinesAndWritesEngineKeys() throws Exception {
        File ini = new File(ctx.getExternalFilesDir(null), "mafia2re.ini");
        Files.write(ini.toPath(), "custom_key = 5\nrender_height = 1080\n".getBytes(StandardCharsets.UTF_8));
        GameConfig cfg = new GameConfig(ctx);
        cfg.applyMode(DeviceProfile.testOverride, GameConfig.MODE_SPEED);
        cfg.set(GameConfig.DRIVER, 1);
        cfg.writeIni();
        String s = new String(Files.readAllBytes(ini.toPath()), StandardCharsets.UTF_8);
        assertTrue(s, s.contains("custom_key = 5"));
        assertTrue(s, s.contains("render_height = 480\n"));
        assertFalse(s, s.contains("1080"));
        assertTrue(s, s.contains("vulkan_driver = system"));
        assertTrue(s, s.contains("fps_limit = 30"));
    }

    @Test
    public void pickedFolderIsNormalised() throws Exception {
        File game = fakeGame(true);
        assertEquals(game, GameFiles.normalize(new File(game, "pc")));
        assertEquals(game, GameFiles.normalize(game.getParentFile()));
        assertTrue(GameFiles.missing(game).isEmpty());
        File partial = new File(ctx.getCacheDir(), "partial");
        new File(partial, "PC").mkdirs();
        Files.write(new File(partial, "PC/Mafia2.exe").toPath(), new byte[]{1});
        assertNotNull(GameFiles.find(partial, GameFiles.EXE));   // case-insensitive
        assertEquals(5, GameFiles.missing(partial).size());
    }

    @Test
    public void defaultLayoutHasNoOverlapsOnSmallAndTallPhones() {
        int[][] screens = {{800, 360}, {892, 412}, {960, 432}, {1024, 480}};
        int[] ctxs = {ControlLayout.FOOT, ControlLayout.CAR, ControlLayout.MENU};
        for (int[] s : screens) {
            ControlLayout l = new ControlLayout();
            for (ControlLayout.Element e : l.all) e.layout(s[0], s[1], 1f, 1f);
            for (int ctxBit : ctxs) {
                for (ControlLayout.Element a : l.all) {
                    if ((a.ctx & ctxBit) == 0) continue;
                    String where = a.id + " on " + s[0] + "x" + s[1];
                    assertTrue(where, a.cx - a.r >= 0 && a.cx + a.r <= s[0] && a.cy - a.r >= 0 && a.cy + a.r <= s[1]);
                    for (ControlLayout.Element b : l.all) {
                        if (b == a || (b.ctx & ctxBit) == 0 || a.id.compareTo(b.id) > 0) continue;
                        double d = Math.hypot(a.cx - b.cx, a.cy - b.cy);
                        // Circles plus room for the caption under the upper one.
                        assertTrue(where + " vs " + b.id + " d=" + Math.round(d), d >= a.r + b.r + 10);
                    }
                }
            }
            ControlLayout.Element st = l.stick;
            float top = st.cy - st.r * (ControlsOverlay.LOCK_DIST + ControlsOverlay.LOCK_R);
            assertTrue("lock off screen on " + s[1], top > 0);
            for (ControlLayout.Element b : l.all) {
                if (b == st || (b.ctx & ControlLayout.FOOT) == 0) continue;
                boolean inX = Math.abs(b.cx - st.cx) < st.r * 0.5f + b.r;
                boolean inY = b.cy + b.r > top && b.cy - b.r < st.cy - st.r * 0.85f;
                assertFalse("lock track covers " + b.id + " on " + s[1], inX && inY);
            }
        }
    }

    // ------------------------------------------------------------------ editor

    @Test
    public void editorScreens() throws Exception {
        ActivityController<LayoutEditorActivity> c = Robolectric.buildActivity(LayoutEditorActivity.class).setup();
        View root = c.get().getWindow().getDecorView();
        shot(root, "07_editor_foot");
        ControlsOverlay o = find(root, ControlsOverlay.class);
        ControlLayout.Element fire = o.layout.byId("fire");
        float x0 = fire.cx, y0 = fire.cy;
        touch(o, MotionEvent.ACTION_DOWN, 0, x0, y0);
        touch(o, MotionEvent.ACTION_MOVE, 0, x0 - 200, y0 - 120);
        touch(o, MotionEvent.ACTION_UP, 0, x0 - 200, y0 - 120);
        assertTrue(fire.fx > 0);
        assertTrue(o.canUndo());
        shot(root, "08_editor_selected");
        o.undo();
        assertEquals(-1f, fire.fx, 0.0001f);
        TextView car = findText(root, Ui.t("ЗА РУЛЁМ", "DRIVING"));
        car.performClick();
        shot(root, "09_editor_car");
        // Done saves.
        touch(o, MotionEvent.ACTION_DOWN, 0, o.layout.byId("gas").cx, o.layout.byId("gas").cy);
        touch(o, MotionEvent.ACTION_MOVE, 0, 300, 300);
        touch(o, MotionEvent.ACTION_UP, 0, 300, 300);
        findText(root, Ui.t("ГОТОВО", "DONE")).performClick();
        ShadowLooper.idleMainLooper();
        ControlLayout saved = ControlLayout.load(ctx);
        assertTrue(saved.byId("gas").fx > 0);
    }

    // ------------------------------------------------------------------ in game

    private ActivityController<M2GameActivity> game() {
        new GameConfig(ctx).applyMode(DeviceProfile.testOverride, GameConfig.MODE_SPEED);
        return Robolectric.buildActivity(M2GameActivity.class).setup();
    }

    @Test
    public void overlayReplacesOriginalControls() throws Exception {
        ActivityController<M2GameActivity> c = game();
        View root = c.get().getWindow().getDecorView();
        assertNull(find(root, TouchControls.class));
        assertNotNull(find(root, ControlsOverlay.class));
        assertNotNull(findText(root, "VinTeRuS"));                 // the author credit stays
        assertEquals(30, TouchControls.fpsLimit);                  // FPS cap pushed on start
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot(root, "10_game_foot");
        TouchControls.state = 8 | 1 | 2;
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot(root, "11_game_car");
        TouchControls.state = 8;
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot(root, "12_game_menu");
    }

    @Test
    public void stickFireLookAndPause() {
        ActivityController<M2GameActivity> c = game();
        ControlsOverlay o = find(c.get().getWindow().getDecorView(), ControlsOverlay.class);
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        int h = o.getHeight();
        float sx = o.getWidth() * 0.2f, sy = h * 0.7f, r = o.layout.stick.r;

        // Stick forward -> W; full deflection -> sprint (Shift); release -> both up.
        touch(o, MotionEvent.ACTION_DOWN, 0, sx, sy);
        touch(o, MotionEvent.ACTION_MOVE, 0, sx, sy - r * 0.6f);
        assertTrue(SDLActivity.events.toString(), SDLActivity.events.contains("down 51"));
        assertFalse(SDLActivity.events.contains("down 59"));
        touch(o, MotionEvent.ACTION_MOVE, 0, sx, sy - r * 1.2f);
        assertTrue(SDLActivity.events.toString(), SDLActivity.events.contains("down 59"));
        touch(o, MotionEvent.ACTION_UP, 0, sx, sy - r * 1.2f);
        assertTrue(SDLActivity.events.contains("up 51"));
        assertTrue(SDLActivity.events.contains("up 59"));

        // Diagonal -> W + D.
        SDLActivity.events.clear();
        touch(o, MotionEvent.ACTION_DOWN, 0, sx, sy);
        touch(o, MotionEvent.ACTION_MOVE, 0, sx + r * 0.5f, sy - r * 0.5f);
        assertTrue(SDLActivity.events.contains("down 51"));
        assertTrue(SDLActivity.events.contains("down 32"));
        touch(o, MotionEvent.ACTION_UP, 0, sx + r * 0.5f, sy - r * 0.5f);

        // Fire = left mouse; dragging the Fire finger turns the camera.
        SDLActivity.events.clear();
        ControlLayout.Element fire = o.layout.byId("fire");
        touch(o, MotionEvent.ACTION_DOWN, 0, fire.cx, fire.cy);
        touch(o, MotionEvent.ACTION_MOVE, 0, fire.cx - 60, fire.cy);
        touch(o, MotionEvent.ACTION_UP, 0, fire.cx - 60, fire.cy);
        assertEquals("mouse b1 a0 rel 0,0", SDLActivity.events.get(0));
        assertTrue(SDLActivity.events.toString(), SDLActivity.events.get(1).startsWith("mouse b1 a2 rel -"));
        assertEquals("mouse b0 a1 rel 0,0", SDLActivity.events.get(SDLActivity.events.size() - 1));

        // Free right half = camera only.
        SDLActivity.events.clear();
        float lx = o.getWidth() * 0.6f, ly = h * 0.3f;
        touch(o, MotionEvent.ACTION_DOWN, 0, lx, ly);
        touch(o, MotionEvent.ACTION_MOVE, 0, lx + 40, ly + 20);
        touch(o, MotionEvent.ACTION_UP, 0, lx + 40, ly + 20);
        assertEquals(1, SDLActivity.events.size());
        assertTrue(SDLActivity.events.get(0), SDLActivity.events.get(0).matches("mouse b0 a2 rel \\d+,\\d+"));

        // Pause -> Esc, then the overlay is in menu mode: taps become absolute clicks.
        SDLActivity.events.clear();
        ControlLayout.Element pause = o.layout.byId("pause");
        touch(o, MotionEvent.ACTION_DOWN, 0, pause.cx, pause.cy);
        touch(o, MotionEvent.ACTION_UP, 0, pause.cx, pause.cy);
        assertEquals("down 111", SDLActivity.events.get(0));
        assertEquals("up 111", SDLActivity.events.get(1));
        SDLActivity.events.clear();
        touch(o, MotionEvent.ACTION_DOWN, 0, 500, 300);
        touch(o, MotionEvent.ACTION_UP, 0, 500, 300);
        assertTrue(SDLActivity.events.toString(), SDLActivity.events.contains("mouse b1 a0 abs 500,300"));
        assertTrue(SDLActivity.events.toString(), SDLActivity.events.contains("mouse b0 a1 abs 500,300"));
    }

    @Test
    public void sprintLockKeepsRunningUntilStickTouched() throws Exception {
        ActivityController<M2GameActivity> c = game();
        View root = c.get().getWindow().getDecorView();
        ControlsOverlay o = find(root, ControlsOverlay.class);
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        float sx = o.getWidth() * 0.15f, sy = o.getHeight() * 0.75f, r = o.layout.stick.r;
        touch(o, MotionEvent.ACTION_DOWN, 0, sx, sy);
        touch(o, MotionEvent.ACTION_MOVE, 0, sx, sy - r * 1.1f);
        shot(root, "14_stick_sprint");
        touch(o, MotionEvent.ACTION_MOVE, 0, sx + r * 0.1f, sy - r * ControlsOverlay.LOCK_DIST);
        shot(root, "15_stick_lock_armed");
        touch(o, MotionEvent.ACTION_UP, 0, sx + r * 0.1f, sy - r * ControlsOverlay.LOCK_DIST);
        assertTrue(o.runLocked());
        assertTrue(SDLActivity.events.toString(), SDLActivity.events.contains("down 51"));
        assertTrue(SDLActivity.events.contains("down 59"));
        assertFalse(SDLActivity.events.toString(), SDLActivity.events.contains("up 51"));
        assertFalse(SDLActivity.events.contains("up 59"));
        shot(root, "16_stick_locked");
        // Touching the stick again ends the auto-run.
        touch(o, MotionEvent.ACTION_DOWN, 0, sx, sy);
        assertFalse(o.runLocked());
        assertTrue(SDLActivity.events.contains("up 51"));
        assertTrue(SDLActivity.events.contains("up 59"));
        touch(o, MotionEvent.ACTION_UP, 0, sx, sy);
        // Sideways at the edge does not sprint.
        SDLActivity.events.clear();
        touch(o, MotionEvent.ACTION_DOWN, 0, sx, sy);
        touch(o, MotionEvent.ACTION_MOVE, 0, sx + r * 1.3f, sy);
        assertFalse(SDLActivity.events.toString(), SDLActivity.events.contains("down 59"));
        touch(o, MotionEvent.ACTION_UP, 0, sx + r * 1.3f, sy);
    }

    @Test
    public void carPedalsAndSharedKeys() {
        TouchControls.state = 8 | 1 | 2;
        ActivityController<M2GameActivity> c = game();
        ControlsOverlay o = find(c.get().getWindow().getDecorView(), ControlsOverlay.class);
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        ControlLayout.Element gas = o.layout.byId("gas");
        touch(o, MotionEvent.ACTION_DOWN, 0, gas.cx, gas.cy);
        assertEquals("down 51", SDLActivity.events.get(SDLActivity.events.size() - 1));
        touch(o, MotionEvent.ACTION_UP, 0, gas.cx, gas.cy);
        assertEquals("up 51", SDLActivity.events.get(SDLActivity.events.size() - 1));
        // Fire is not on screen while driving.
        ControlLayout.Element fire = o.layout.byId("fire");
        int before = SDLActivity.events.size();
        touch(o, MotionEvent.ACTION_DOWN, 0, fire.cx, fire.cy);
        touch(o, MotionEvent.ACTION_UP, 0, fire.cx, fire.cy);
        for (String e : SDLActivity.events.subList(before, SDLActivity.events.size())) assertFalse(e, e.startsWith("mouse b1 a0"));
    }

    @Test
    public void portMenuScreen() throws Exception {
        ActivityController<M2GameActivity> c = game();
        View root = c.get().getWindow().getDecorView();
        ControlsOverlay o = find(root, ControlsOverlay.class);
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        ControlLayout.Element gear = o.layout.byId("gear");
        touch(o, MotionEvent.ACTION_DOWN, 0, gear.cx, gear.cy);
        touch(o, MotionEvent.ACTION_UP, 0, gear.cx, gear.cy);
        shot(root, "13_port_menu");
        TextView fps60 = findText(root, "60");
        assertNotNull(fps60);
        fps60.performClick();
        assertEquals(60, TouchControls.fpsLimit);
    }
}
