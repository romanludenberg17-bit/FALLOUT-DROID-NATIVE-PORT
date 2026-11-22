package com.wastelanddroid.engine;

import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.google.androidgamesdk.GameActivity;
import com.wastelanddroid.controls.GamepadInput;
import com.wastelanddroid.controls.GamepadMapping;
import com.wastelanddroid.runtime.InputBridge;
import com.wastelanddroid.input.KeyMap;
import com.wastelanddroid.input.ScreenKeysOverlay;

/**
 * The game: GameActivity running libnvforever.so (android_main), with Fallout Droid's
 * on-screen controls over it. The command line was written by {@link FalloutEngine#launch}.
 */
public class NativeGameActivity extends GameActivity implements QuickPanel.Host {
    private ScreenKeysOverlay overlay;
    private com.wastelanddroid.input.ConsoleBar consoleBar;
    private CrtOverlay crt;
    private QuickPanel panel;
    private LoadingOverlay loading;
    private long engineUpAt;
    private boolean fo3;
    private GamepadInput gamepad;

    @Override
    protected void onCreate(Bundle state) {
        // A Java crash in the game's process: its stack goes to java_crash.txt for the report.
        final Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.File ext = getExternalFilesDir(null);
                if (ext != null) {
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(ext, "java_crash.txt"))) {
                        out.write((new java.util.Date() + " thread " + t.getName() + "\n" + android.util.Log.getStackTraceString(e)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception ignored) {
            }
            if (before != null) before.uncaughtException(t, e);
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // The window's background is the loading screen's first frame (the theme:
        // FdGameTheme / FdGameThemeFo3), shown by the system before this process runs.
        try {
            super.onCreate(state);
        } catch (Throwable t) {
            // libnvforever.so did not load or GameActivity failed: tell the launcher and leave.
            writeExit("java " + android.util.Log.getStackTraceString(t));
            android.util.Log.e("NVForever", "GameActivity.onCreate failed", t);
            finish();
            return;
        }
        hideSystemBars();
        overlay = new ScreenKeysOverlay(this);
        addContentView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        GamepadMapping mapping;
        try {
            mapping = GamepadMapping.load(this);
        } catch (Exception e) {
            mapping = GamepadMapping.defaults();
        }
        gamepad = new GamepadInput(new InputBridge(), mapping);
        gamepad.start();
        fo3 = this instanceof NativeGameActivityFo3 || "fo3".equals(getIntent().getStringExtra("game"));
        crt = new CrtOverlay(this);
        addContentView(crt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
        consoleBar = new com.wastelanddroid.input.ConsoleBar(this);
        addContentView(consoleBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP));
        panel = new QuickPanel(this, this);
        addContentView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Over everything until the engine draws its first frames.
        loading = new LoadingOverlay(this, fo3);
        addContentView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, this::togglePanel);
        }
        applyRenderScale();
        applyRefreshRate();
        ui.postDelayed(pollUi, 500);
    }

    // ---- render scale: the surface is drawn at the launcher's resolution and scaled up by
    // the display hardware (Settings / Pip-Boy "Разрешение"); the cursor follows the scale.
    private void applyRenderScale() {
        if (mSurfaceView == null) return;
        mSurfaceView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitSurface(r - l, b - t));
        // GameActivity has laid its view out already in onCreate's super call, so the listener
        // may never fire: fit now (the view's size, else the screen's) and after the next layout.
        android.graphics.Rect screen = getWindowManager().getCurrentWindowMetrics().getBounds();
        int w = mSurfaceView.getWidth() > 0 ? mSurfaceView.getWidth() : Math.max(screen.width(), screen.height());
        int h = mSurfaceView.getHeight() > 0 ? mSurfaceView.getHeight() : Math.min(screen.width(), screen.height());
        fitSurface(w, h);
        mSurfaceView.post(() -> fitSurface(mSurfaceView.getWidth(), mSurfaceView.getHeight()));
    }

    // ---- display refresh: Android keeps a game at 60 Hz unless it asks for more; a cap
    // above 60 (a high class device) asks for the fastest mode at the screen's size.
    private void applyRefreshRate() {
        try {
            int cap = FalloutEngine.frameCap(this);
            android.view.Display d = getDisplay();
            if (d == null) return;
            // The fastest mode at the screen's size up to 120 Hz, whatever the cap (the engine
            // keeps the cap): at 60 Hz a frame a little over 16.7 ms waits for the next
            // refresh and the game shows 30; at 90/120 Hz it shows 40-45.
            float limit = cap > 120 || cap <= 0 ? Float.MAX_VALUE : 121f;
            android.view.Display.Mode cur = d.getMode(), best = null;
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
                float hz = m.getRefreshRate();
                if (hz <= limit && (best == null || hz > best.getRefreshRate())) best = m;
            }
            if (best == null) best = cur;
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
            getWindow().setAttributes(lp);
            android.util.Log.i("NVForever", "display mode " + best.getRefreshRate() + " Hz (cap " + cap + ")");
            wantedHz = best.getRefreshRate();
            if (mSurfaceView != null && android.os.Build.VERSION.SDK_INT >= 30) {
                if (!rateCallback) {
                    rateCallback = true;
                    mSurfaceView.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
                        @Override public void surfaceCreated(android.view.SurfaceHolder h) { setSurfaceRate(h); }
                        @Override public void surfaceChanged(android.view.SurfaceHolder h, int f, int w, int hgt) { setSurfaceRate(h); }
                        @Override public void surfaceDestroyed(android.view.SurfaceHolder h) {}
                    });
                }
                setSurfaceRate(mSurfaceView.getHolder());
            }
        } catch (RuntimeException e) {
            android.util.Log.w("NVForever", "display mode: " + e);
        }
    }

    private float wantedHz;
    private boolean rateCallback;

    private void setSurfaceRate(android.view.SurfaceHolder h) {
        try {
            android.view.Surface surface = h.getSurface();
            if (surface != null && surface.isValid() && wantedHz > 0) {
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    surface.setFrameRate(wantedHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                            android.view.Surface.CHANGE_FRAME_RATE_ALWAYS);
                } else {
                    surface.setFrameRate(wantedHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    private int fixedW, fixedH;

    private void fitSurface(int vw, int vh) {
        if (vw <= 0 || vh <= 0) return;
        int target = FalloutEngine.renderHeight(this);
        int th = Math.min(vh, target <= 0 ? vh : target);
        int tw = Math.round(th * (float) vw / vh);
        tw &= ~1;
        th &= ~1;
        if (tw != fixedW || th != fixedH) {
            fixedW = tw;
            fixedH = th;
            if (th < vh) mSurfaceView.getHolder().setFixedSize(tw, th);
            else mSurfaceView.getHolder().setSizeFromLayout();
            android.util.Log.i("NVForever", "render surface " + tw + "x" + th + " on a " + vw + "x" + vh + " view");
        }
        NativeInput.setViewToSurface(fixedW / (float) vw, fixedH / (float) vh);
    }

    // ---- smart hiding of the on-screen keys: the engine says when a menu is up
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pollUi = new Runnable() {
        @Override
        public void run() {
            int state = 0;
            boolean restart = false, exited = false;
            try {
                state = NativeInput.uiState();
                restart = NativeInput.restartRequested();
                exited = NativeInput.engineExited();
            } catch (UnsatisfiedLinkError ignored) {
            }
            if (exited && !restart && !restarting) {
                // The graphics driver lost the device (Adreno/Mali reset the GPU): the
                // game starts again by itself from its newest save, behind the loading
                // screen, instead of dropping to the launcher.
                if (recoverFromDeviceLoss()) return;
                // Quit Game: back to the launcher, the process ended at once (see exitGame).
                restarting = true;
                try {
                    startActivity(new android.content.Intent(NativeGameActivity.this, com.wastelanddroid.MainActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
                } catch (Exception ignored) {
                }
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            if (restart && !restarting) {
                restartGame();
                return;
            }
            // The engine draws once it reports a screen: the loading picture goes a moment
            // later, so its first frames are behind it.
            if (loading != null && !loading.finishing() && state != 0) {
                if (engineUpAt == 0) engineUpAt = android.os.SystemClock.uptimeMillis();
                else if (android.os.SystemClock.uptimeMillis() - engineUpAt > 700) loading.finish();
            }
            if (consoleBar != null) consoleBar.setShown(state == ScreenKeysOverlay.UI_CONSOLE);
            if (overlay != null) {
                overlay.setUiState(state);
                try {
                    overlay.setPlayFlags(NativeInput.playFlags());
                } catch (UnsatisfiedLinkError ignored) {
                }
            }
            // Fallout 3's main menu is on a CRT, as in the original.
            if (crt != null) crt.setOn(fo3 && state == ScreenKeysOverlay.UI_MAIN_MENU);
            ui.postDelayed(this, 120);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (overlay != null) overlay.reload();
    }

    @Override
    protected void onPause() {
        if (overlay != null) overlay.releaseAll();
        if (gamepad != null) gamepad.releaseAll();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(pollUi);
        if (gamepad != null) gamepad.stop();
        boolean ending = isFinishing();
        super.onDestroy();
        // The engine keeps process-wide state (Bevy, OnceLocks): a later start needs a fresh
        // process. This one is the game's own (manifest: android:process=":game").
        if (ending) {
            android.util.Log.i("NVForever", "game activity finished: ending the game process");
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    // ---- loading a save / back to the main menu: a fresh game process. The engine wrote
    // the next command line; the launcher (its own process) starts this activity again
    // once this process has ended.
    private boolean restarting;

    private void restartGame() {
        restarting = true;
        android.util.Log.i("NVForever", "engine asked for a fresh run: relaunching");
        String game = getIntent().getStringExtra("game");
        if (game == null) game = this instanceof NativeGameActivityFo3 ? "fo3" : "fnv";
        android.content.Intent i = new android.content.Intent(this, GameRelayActivity.class)
                .putExtra(GameRelayActivity.EXTRA_GAME, game)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
        overridePendingTransition(0, 0);
        // The launcher's "game running" flag for its crash check.
        com.wastelanddroid.util.Prefs.get(this).edit().putBoolean(com.wastelanddroid.util.Prefs.LAUNCH_PENDING, true).commit();
        // The relay ends this process once it is on screen (SIGKILL: no destructors under the
        // engine's live threads; its memory free before the next run). Killing it here, before
        // the relay showed, let the launcher under it come up meanwhile.
    }

    private static final String RECOVERIES = "gpu_recoveries";
    private final long startedAt = System.currentTimeMillis();
    private int safeLevel;

    /** Starts the game again after a lost graphics device; false when it was not one. */
    private boolean recoverFromDeviceLoss() {
        String game = getIntent().getStringExtra("game");
        if (game == null) game = this instanceof NativeGameActivityFo3 ? "fo3" : "fnv";
        java.io.File home = FalloutEngine.homeDir(this, game);
        java.io.File crash = newestFile(new java.io.File(home, "logs"), "crash-", ".txt");
        long now = System.currentTimeMillis();
        if (crash == null || now - crash.lastModified() > 30_000) return false;
        String text;
        try {
            byte[] b = java.nio.file.Files.readAllBytes(crash.toPath());
            text = new String(b, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return false;
        }
        if (!text.contains("device lost") && !text.contains("devicelost")) return false;
        // At most 3 times in 10 minutes: a loss on every start would loop for ever.
        android.content.SharedPreferences prefs = com.wastelanddroid.util.Prefs.get(this);
        String[] past = prefs.getString(RECOVERIES, "").split(",");
        StringBuilder keep = new StringBuilder();
        int recent = 0;
        for (String t : past) {
            try {
                long v = Long.parseLong(t.trim());
                if (now - v < 600_000) {
                    recent++;
                    keep.append(v).append(',');
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (recent >= 3) return false;
        // Lost again within a minute of the last start-over: that save's place loses it, so
        // the main menu this time.
        long last = 0;
        for (String t : past) {
            try {
                last = Math.max(last, Long.parseLong(t.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        boolean again = now - last < 90_000;
        keep.append(now);
        prefs.edit().putString(RECOVERIES, keep.toString()).commit();
        // The command line again, ending in the newest save (or the main menu).
        // A save written during this run (autosave, quicksave), else the one it started from.
        java.io.File save = newestFile(new java.io.File(home, "saves"), "", ".nvfsave");
        if (save != null && save.lastModified() < startedAt) save = null;
        boolean toMenu = again;
        try {
            java.io.File args = new java.io.File(getFilesDir(), FalloutEngine.ARGS_FILE);
            java.util.List<String> lines = new java.util.ArrayList<>(java.nio.file.Files.readAllLines(args.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            if (toMenu) save = null;
            else if (save == null) {
                int at = lines.indexOf("--load");
                if (at >= 0 && at + 1 < lines.size() && new java.io.File(lines.get(at + 1)).isFile()) save = new java.io.File(lines.get(at + 1));
            }
            // A lighter picture from now on (FalloutEngine.GPU_SAFE: 1 no sun shadows, MSAA,
            // grass; 2 also a lower resolution and smaller textures), and the command line
            // made again from the profile, ending in the save (or the main menu).
            int level = Math.min(2, prefs.getInt(FalloutEngine.GPU_SAFE, 0) + 1);
            prefs.edit().putInt(FalloutEngine.GPU_SAFE, level).commit();
            safeLevel = level;
            if (save != null) prefs.edit().putString(FalloutEngine.PENDING_LOAD + game, save.getAbsolutePath()).commit();
            com.wastelanddroid.game.GameDef def = com.wastelanddroid.game.GameDef.byId(game);
            FalloutEngine.Prepared p = FalloutEngine.prepare(this, def, new java.io.File(def.gameDir(this)));
            if (!(p instanceof FalloutEngine.Prepared.Ok)) return false;
            StringBuilder sb = new StringBuilder();
            for (String l : ((FalloutEngine.Prepared.Ok) p).getArgs()) sb.append(l).append('\n');
            FalloutEngine.writeArgs(this, sb.toString());
        } catch (Exception e) {
            android.util.Log.w("NVForever", "device loss: could not write the command line", e);
            return false;
        }
        android.util.Log.i("NVForever", "graphics device lost: starting again from " + (save == null ? "the main menu" : save));
        android.widget.Toast.makeText(this, (save == null ? "Сбой видеодрайвера — перезапуск игры" : "Сбой видеодрайвера — перезапуск с последнего сохранения") + (safeLevel >= 2 ? "; графика снижена (разрешение, текстуры, тени)" : "; тени, MSAA и трава выключены"), android.widget.Toast.LENGTH_LONG).show();
        restartGame();
        return true;
    }

    private static java.io.File newestFile(java.io.File dir, String prefix, String suffix) {
        java.io.File[] kids = dir.listFiles();
        java.io.File best = null;
        if (kids == null) return null;
        for (java.io.File f : kids) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (f.isFile() && n.startsWith(prefix) && n.endsWith(suffix) && (best == null || f.lastModified() > best.lastModified())) best = f;
        }
        return best;
    }

    // ---- the quick panel (QuickPanel.Host)
    private void togglePanel() {
        if (panel == null) return;
        if (!panel.shown() && overlay != null) overlay.releaseAll();
        panel.toggle();
    }

    @Override
    public void onBackPressed() {
        togglePanel();
    }

    @Override
    public void keysChanged() {
        if (overlay != null) overlay.reload();
    }

    @Override
    public void frameCapChanged(int fps) {
        try {
            NativeInput.setFrameCap(fps);
        } catch (UnsatisfiedLinkError ignored) {
        }
        applyRefreshRate();
    }

    @Override
    public void exitGame() {
        // A quick save (F5), then the game's process ends at once (its sound with it) and
        // the launcher comes back. Waiting on the engine's thread to wind down (the
        // GameActivity glue's destroy) left the game playing behind the launcher.
        if (panel != null) panel.hide();
        try {
            NativeInput.key(63, true);
            NativeInput.key(63, false);
        } catch (UnsatisfiedLinkError ignored) {
        }
        android.widget.Toast.makeText(this, "Сохранение и выход…", android.widget.Toast.LENGTH_SHORT).show();
        restarting = true;
        ui.postDelayed(() -> {
            com.wastelanddroid.util.Prefs.get(this).edit().putBoolean(com.wastelanddroid.util.Prefs.LAUNCH_PENDING, false).commit();
            writeExit("ok");
            try {
                startActivity(new android.content.Intent(this, com.wastelanddroid.MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (Exception ignored) {
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 2500);
    }

    private void writeExit(String text) {
        try {
            java.io.File ext = getExternalFilesDir(null);
            if (ext == null) return;
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(ext, FalloutEngine.EXIT_FILE))) {
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private void hideSystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    // ---- physical keyboard and gamepad -> engine (evdev codes)
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // The system Back (gesture or button): the quick panel, never the engine.
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) togglePanel();
            return true;
        }
        int src = e.getSource();
        if (gamepad != null && ((src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
            if (gamepad.onKey(e)) return true;
        }
        int code = KeyMap.toEvdev(e.getKeyCode());
        if (code > 0 && (e.getAction() == KeyEvent.ACTION_DOWN || e.getAction() == KeyEvent.ACTION_UP)) {
            if (e.getRepeatCount() == 0) NativeInput.key(code, e.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- physical mouse: captured -> relative look; otherwise absolute cursor
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (gamepad != null && (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            if (gamepad.onMotion(e)) return true;
        }
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            handleMouse(e, false);
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            handleMouse(e, false);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && overlay != null) overlay.requestPointerCapture();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    private int lastButtons;

    private void handleMouse(MotionEvent e, boolean captured) {
        if (captured) {
            NativeInput.mouseMove(e.getX(), e.getY());
        } else {
            NativeInput.pointTo(e.getX(), e.getY());
        }
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            NativeInput.scroll(e.getAxisValue(MotionEvent.AXIS_VSCROLL));
        }
        int b = e.getButtonState();
        int changed = b ^ lastButtons;
        if ((changed & MotionEvent.BUTTON_PRIMARY) != 0) NativeInput.key(272, (b & MotionEvent.BUTTON_PRIMARY) != 0);
        if ((changed & MotionEvent.BUTTON_SECONDARY) != 0) NativeInput.key(273, (b & MotionEvent.BUTTON_SECONDARY) != 0);
        if ((changed & MotionEvent.BUTTON_TERTIARY) != 0) NativeInput.key(274, (b & MotionEvent.BUTTON_TERTIARY) != 0);
        lastButtons = b;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
        if (hasCapture && overlay != null) {
            overlay.setOnCapturedPointerListener((View v, MotionEvent e) -> {
                handleMouse(e, true);
                return true;
            });
        }
    }
}
