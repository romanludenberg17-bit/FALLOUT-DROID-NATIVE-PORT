package com.mafia2re.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import org.libsdl.app.SDLActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Touch controls over the game surface, and the same view in "edit" mode for the layout editor.
 *
 * Game protocol (same as the port's original overlay): Android key codes go to
 * {@link SDLActivity#onNativeKeyDown}; mouse goes to {@link SDLActivity#onNativeMouse}
 * (action 0 = down, 1 = up, 2 = move; relative while playing, absolute in menus);
 * {@code TouchControls.nativeGameState()} bits: 8 = state known, 1 = player in control,
 * 2 = in a car, 4 = cutscene.
 */
final class ControlsOverlay extends View implements SensorEventListener, InputManager.InputDeviceListener {
    interface Listener {
        void onGear();

        void onSelection(ControlLayout.Element e);
    }

    private static final int ROLE_BUTTON = 1, ROLE_STICK = 2, ROLE_LOOK = 3, ROLE_MENU = 4;

    private static final class Touch {
        int role;
        ControlLayout.Element el;
        float lastX, lastY;
        long lastT;
    }

    final ControlLayout layout;
    private final GameConfig cfg;
    private final float density;
    private Listener listener;

    // Settings snapshot.
    private float opacity, uiScale, camGain;
    private boolean invertY, aimToggle, fixedStick, autoRun, fireLook, vibrate, showFps, padHide, labels;
    private int gyroMode;

    // Game state.
    private boolean live;
    private int ctx = ControlLayout.FOOT;
    private int manualCtx = 0;          // context chosen by hand when the game does not report it
    private boolean stateKnown, lastPlayer = true, forcedMenu, movie;
    private long showInMovieUntil;
    private boolean padConnected;

    // Input.
    private final Map<Integer, Touch> touches = new HashMap<>();
    private final int[] keyRefs = new int[512];
    private int mouseState;
    private float menuX, menuY;
    private float restX, restY;
    private boolean sprintLatched;
    private boolean edgeRun;

    // Stick.
    private float stickX, stickY, knobX, knobY;
    private final boolean[] dirDown = new boolean[4];
    private static final int[] DIR_KEYS = {ControlLayout.K_W, ControlLayout.K_A, ControlLayout.K_S, ControlLayout.K_D};

    // Gyro.
    private boolean gyroOn;
    private long gyroLast;

    // Edit mode.
    private boolean editing;
    private int editCtx = ControlLayout.FOOT;
    private ControlLayout.Element selected;
    private int dragPointer = -1;
    private float dragDx, dragDy;
    private float pinchStartDist, pinchStartScale;
    private boolean snap = true;
    private float[] undoCandidate;
    private final List<float[]> undo = new ArrayList<>();

    // Drawing.
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Map<String, Bitmap> cache = new HashMap<>();
    private DashPathEffect dash;
    private String hud = "";
    private int hudColor = Ui.IVORY;

    ControlsOverlay(Context c, GameConfig cfg, boolean live) {
        super(c);
        this.cfg = cfg;
        this.live = live;
        this.layout = ControlLayout.load(c);
        this.density = c.getResources().getDisplayMetrics().density;
        text.setTypeface(Ui.head(c));
        dash = new DashPathEffect(new float[]{8 * density, 6 * density}, 0);
        text.setTextAlign(Paint.Align.CENTER);
        setHapticFeedbackEnabled(true);
        reloadSettings();
        if (live) {
            InputManager im = (InputManager) c.getSystemService(Context.INPUT_SERVICE);
            if (im != null) {
                im.registerInputDeviceListener(this, null);
                padConnected = findGamepad(im);
            }
            post(poll);
        }
    }

    void setListener(Listener l) {
        listener = l;
    }

    void reloadSettings() {
        opacity = cfg.get(GameConfig.OPACITY) / 100f;
        uiScale = cfg.get(GameConfig.SCALE) / 100f;
        camGain = cfg.get(GameConfig.CAM) / 100f;
        invertY = cfg.get(GameConfig.INVERT) != 0;
        aimToggle = cfg.get(GameConfig.AIM) != 0;
        fixedStick = cfg.get(GameConfig.STICK) != 0;
        autoRun = cfg.get(GameConfig.AUTORUN) != 0;
        fireLook = cfg.get(GameConfig.FIRE_LOOK) != 0;
        vibrate = cfg.get(GameConfig.VIBRATE) != 0;
        showFps = cfg.get(GameConfig.SHOW_FPS) != 0;
        padHide = cfg.get(GameConfig.PAD_HIDE) != 0;
        labels = cfg.get(GameConfig.LABELS) != 0;
        gyroMode = cfg.get(GameConfig.GYRO);
        cache.clear();
        relayout();
        updateGyro();
        invalidate();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        relayout();
        menuX = w / 2f;
        menuY = h / 2f;
    }

    private void relayout() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        for (ControlLayout.Element e : layout.all) e.layout(w, h, density, uiScale);
        ControlLayout.Element s = layout.stick;
        if (touchOf(ROLE_STICK) == null) {
            stickX = knobX = s.cx;
            stickY = knobY = s.cy;
        }
    }

    private int currentCtx() {
        return editing ? editCtx : ctx;
    }

    private boolean visible(ControlLayout.Element e) {
        if ((e.ctx & currentCtx()) == 0) return false;
        if (editing) return true;
        if (e.hidden) return false;
        if (padHide && padConnected) return false;
        return !movieHidden();
    }

    private boolean movieHidden() {
        return movie && !editing && SystemClock.uptimeMillis() > showInMovieUntil;
    }

    // ------------------------------------------------------------------ game state polling

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (live) {
                boolean changed = pollState();
                if (showFps) updateHud();
                if (changed || showFps) invalidate();
                postDelayed(this, 200);
            }
        }
    };

    private boolean pollState() {
        int s;
        try {
            s = TouchControls.nativeGameState();
        } catch (Throwable t) {
            s = 0;
        }
        int before = ctx;
        boolean movieBefore = movieHidden();
        stateKnown = (s & 8) != 0;
        if (stateKnown) {
            boolean player = (s & 1) != 0;
            if (player != lastPlayer) {
                lastPlayer = player;
                forcedMenu = false;
                manualCtx = 0;
            }
            movie = (s & 4) != 0;
            if (manualCtx != 0) ctx = manualCtx;
            else if (!player || forcedMenu) ctx = ControlLayout.MENU;
            else ctx = (s & 2) != 0 ? ControlLayout.CAR : ControlLayout.FOOT;
        } else {
            movie = false;
            // No state from the game: menus until the player picks a mode on the chip.
            ctx = manualCtx != 0 ? manualCtx : ControlLayout.MENU;
        }
        if (ctx != before) onContextChanged();
        return ctx != before || movieBefore != movieHidden();
    }

    private void onContextChanged() {
        // Let go of anything that is no longer on screen.
        for (Touch t : new ArrayList<>(touches.values())) {
            if (t.role == ROLE_BUTTON && !visible(t.el)) releaseButton(t.el);
        }
        if (ctx == ControlLayout.MENU) {
            releaseStick();
            touches.values().removeIf(t -> t.role == ROLE_STICK || t.role == ROLE_LOOK);
            releaseLatches();
        }
        updateGyro();
    }

    private void updateHud() {
        try {
            float fps = TouchControls.nativeFps();
            hud = String.format(Locale.ROOT, "%.0f FPS  ·  %.1f ms  ·  %d MB", fps, TouchControls.nativeFrameMs(), TouchControls.nativeMemoryMiB());
            hudColor = fps >= 50 ? Ui.GREEN : fps >= 28 ? Ui.AMBER : Ui.RED;
        } catch (Throwable t) {
            hud = "";
        }
    }

    // ------------------------------------------------------------------ output to the game

    private void key(int code, boolean down) {
        if (!live || code <= 0 || code >= keyRefs.length) return;
        if (down) {
            if (keyRefs[code]++ == 0) SDLActivity.onNativeKeyDown(code);
        } else if (keyRefs[code] > 0) {
            if (--keyRefs[code] == 0) SDLActivity.onNativeKeyUp(code);
        }
    }

    private void mouseButton(int button, boolean down) {
        if (!live) return;
        mouseState = down ? mouseState | button : mouseState & ~button;
        if (ctx == ControlLayout.MENU) SDLActivity.onNativeMouse(mouseState, down ? 0 : 1, menuX, menuY, false);
        else SDLActivity.onNativeMouse(mouseState, down ? 0 : 1, 0, 0, true);
    }

    private void mouseBy(float dx, float dy) {
        if (!live) return;
        float x = dx + restX, y = dy + restY;
        int ix = (int) x, iy = (int) y;
        restX = x - ix;
        restY = y - iy;
        if (ix != 0 || iy != 0) SDLActivity.onNativeMouse(mouseState, 2, ix, iy, true);
    }

    private void mouseTo(float x, float y) {
        menuX = x;
        menuY = y;
        if (live) SDLActivity.onNativeMouse(mouseState, 2, x, y, false);
    }

    private void buzz() {
        if (vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    // ------------------------------------------------------------------ buttons

    private void pressButton(ControlLayout.Element e, int pid) {
        e.pointer = pid;
        buzz();
        switch (e.kind) {
            case MOUSE_LEFT:
                mouseButton(1, true);
                break;
            case MOUSE_RIGHT:
                if (aimToggle) {
                    e.latched = !e.latched;
                    mouseButton(2, e.latched);
                } else mouseButton(2, true);
                updateGyro();
                break;
            case RUN:
                sprintLatched = !sprintLatched;
                e.latched = sprintLatched;
                updateRun();
                break;
            case PAUSE:
            case KEY:
                key(e.key, true);
                if (!stateKnown && e.key == ControlLayout.K_F) {
                    // No state from the game: guess the context from the car buttons.
                    manualCtx = ctx == ControlLayout.CAR ? ControlLayout.FOOT : ControlLayout.CAR;
                }
                break;
            case GEAR:
                break;
            default:
                break;
        }
        invalidate();
    }

    private void releaseButton(ControlLayout.Element e) {
        int pid = e.pointer;
        e.pointer = -1;
        touches.remove(pid);
        switch (e.kind) {
            case MOUSE_LEFT:
                mouseButton(1, false);
                break;
            case MOUSE_RIGHT:
                if (!aimToggle) mouseButton(2, false);
                updateGyro();
                break;
            case PAUSE:
                key(e.key, false);
                releaseAll();
                forcedMenu = true;
                manualCtx = 0;
                ctx = ControlLayout.MENU;
                onContextChanged();
                break;
            case KEY:
                key(e.key, false);
                if (e.key == ControlLayout.K_ESC && forcedMenu) {
                    // Back out of the pause menu: return to the game's own idea of the context.
                    forcedMenu = false;
                    pollState();
                }
                if (!stateKnown && e.key == ControlLayout.K_F) onContextChanged();
                break;
            case GEAR:
                if (listener != null) listener.onGear();
                break;
            default:
                break;
        }
        invalidate();
    }

    private void releaseLatches() {
        for (ControlLayout.Element e : layout.all) {
            if (e.latched && e.kind == ControlLayout.Kind.MOUSE_RIGHT) mouseButton(2, false);
            e.latched = false;
        }
        sprintLatched = false;
        updateRun();
    }

    void releaseAll() {
        for (ControlLayout.Element e : layout.all) {
            if (e.pointer >= 0) {
                e.pointer = -1;
                if (e.kind == ControlLayout.Kind.MOUSE_LEFT) mouseButton(1, false);
                else if (e.kind == ControlLayout.Kind.MOUSE_RIGHT && !aimToggle) mouseButton(2, false);
                else if (e.kind == ControlLayout.Kind.KEY || e.kind == ControlLayout.Kind.PAUSE) key(e.key, false);
            }
        }
        if (mouseState != 0 && ctx == ControlLayout.MENU) {
            mouseState = 0;
            if (live) SDLActivity.onNativeMouse(0, 1, menuX, menuY, false);
        }
        touches.clear();
        releaseStick();
        releaseLatches();
        invalidate();
    }

    // ------------------------------------------------------------------ stick

    private void startStick(float x, float y) {
        ControlLayout.Element s = layout.stick;
        if (fixedStick) {
            stickX = s.cx;
            stickY = s.cy;
        } else {
            float m = s.r * 0.9f;
            stickX = Math.max(m, Math.min(getWidth() * 0.5f, x));
            stickY = Math.max(m, Math.min(getHeight() - m, y));
        }
        updateStick(x, y);
    }

    private void updateStick(float x, float y) {
        float r = layout.stick.r;
        float dx = x - stickX, dy = y - stickY;
        float d = (float) Math.hypot(dx, dy);
        if (d > r) {
            dx *= r / d;
            dy *= r / d;
        }
        knobX = stickX + dx;
        knobY = stickY + dy;
        float nx = dx / r, ny = dy / r, mag = Math.min(1f, d / r);
        boolean[] want = new boolean[4];
        if (mag > 0.22f) {
            // 8-way with generous diagonals: a key is held when its axis share is above sin(22.5°).
            float ax = nx / mag, ay = ny / mag;
            want[0] = ay < -0.38f;
            want[2] = ay > 0.38f;
            want[1] = ax < -0.38f;
            want[3] = ax > 0.38f;
        }
        for (int i = 0; i < 4; i++) {
            if (want[i] != dirDown[i]) {
                dirDown[i] = want[i];
                key(DIR_KEYS[i], want[i]);
            }
        }
        boolean er = autoRun && ctx == ControlLayout.FOOT && d > r * 0.94f;
        if (er != edgeRun) {
            edgeRun = er;
            if (er) buzz();
            updateRun();
        }
    }

    private void releaseStick() {
        for (int i = 0; i < 4; i++) {
            if (dirDown[i]) {
                dirDown[i] = false;
                key(DIR_KEYS[i], false);
            }
        }
        edgeRun = false;
        if (sprintLatched) {
            // Sprint lock ends when the thumb leaves the stick, like a real sprint toggle.
            sprintLatched = false;
            for (ControlLayout.Element e : layout.all) if (e.kind == ControlLayout.Kind.RUN) e.latched = false;
        }
        updateRun();
        ControlLayout.Element s = layout.stick;
        stickX = knobX = s.cx;
        stickY = knobY = s.cy;
    }

    private boolean shiftHeld;

    private void updateRun() {
        boolean want = live && (sprintLatched || edgeRun) && ctx == ControlLayout.FOOT;
        if (want != shiftHeld) {
            shiftHeld = want;
            key(ControlLayout.K_SHIFT, want);
        }
    }

    // ------------------------------------------------------------------ camera

    private void look(Touch t, float x, float y, long now) {
        float dx = x - t.lastX, dy = y - t.lastY;
        long dt = Math.max(1, now - t.lastT);
        t.lastX = x;
        t.lastY = y;
        t.lastT = now;
        // dp-based so the feel is the same on every screen; mild acceleration for fast flicks.
        float ddx = dx / density, ddy = dy / density;
        float speed = (float) Math.hypot(ddx, ddy) / dt;              // dp per ms
        float accel = 1f + Math.min(1.6f, Math.max(0f, speed - 0.35f) * 0.9f);
        float g = 2.3f * camGain * accel;
        if (ctx == ControlLayout.CAR) g *= 0.8f;
        mouseBy(ddx * g, ddy * g * (invertY ? -1 : 1));
    }

    // ------------------------------------------------------------------ gyro

    private boolean aiming() {
        for (ControlLayout.Element e : layout.all) if (e.kind == ControlLayout.Kind.MOUSE_RIGHT && e.pressed()) return true;
        return false;
    }

    void updateGyro() {
        SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor g = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        boolean want = live && g != null && gyroMode != 0 && ctx != ControlLayout.MENU
                && getWindowVisibility() == VISIBLE && (gyroMode == 2 || aiming());
        if (want == gyroOn) return;
        gyroOn = want;
        gyroLast = 0;
        if (want) sm.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME);
        else sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent ev) {
        long prev = gyroLast;
        gyroLast = ev.timestamp;
        if (prev == 0 || ctx == ControlLayout.MENU) return;
        float dt = Math.min((ev.timestamp - prev) * 1e-9f, 0.1f);
        boolean flipped = getDisplay() != null && getDisplay().getRotation() == Surface.ROTATION_270;
        float yaw = flipped ? -ev.values[0] : ev.values[0];
        float pitch = flipped ? -ev.values[1] : ev.values[1];
        float k = camGain * 820f * dt;
        mouseBy(-yaw * k, -pitch * k * (invertY ? -1 : 1));
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        updateGyro();
        if (v != VISIBLE && live) releaseAll();
    }

    // ------------------------------------------------------------------ gamepads

    private static boolean findGamepad(InputManager im) {
        for (int id : im.getInputDeviceIds()) {
            InputDevice d = im.getInputDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            boolean pad = (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (pad && (Build.VERSION.SDK_INT < 29 || d.isExternal())) return true;
        }
        return false;
    }

    private void updatePads() {
        InputManager im = (InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
        if (im != null) padConnected = findGamepad(im);
        invalidate();
    }

    @Override
    public void onInputDeviceAdded(int id) {
        updatePads();
    }

    @Override
    public void onInputDeviceRemoved(int id) {
        updatePads();
    }

    @Override
    public void onInputDeviceChanged(int id) {
        updatePads();
    }

    // ------------------------------------------------------------------ touch

    private Touch touchOf(int role) {
        for (Touch t : touches.values()) if (t.role == role) return t;
        return null;
    }

    private ControlLayout.Element hitButton(float x, float y) {
        ControlLayout.Element best = null;
        float bestD = Float.MAX_VALUE;
        float slop = 8 * density;
        for (ControlLayout.Element e : layout.all) {
            if (e.kind == ControlLayout.Kind.STICK || !visible(e) || !e.hit(x, y, slop)) continue;
            float d = (float) Math.hypot(x - e.cx, y - e.cy) / Math.max(1, e.r);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Top-left mode chip: lets the player switch foot / car / menu when the game cannot tell. */
    private final RectF modeChip = new RectF();

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (editing) return editTouch(ev);
        int a = ev.getActionMasked();
        int idx = ev.getActionIndex();
        long now = SystemClock.uptimeMillis();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pid = ev.getPointerId(idx);
                float x = ev.getX(idx), y = ev.getY(idx);
                if (movieHidden()) {
                    showInMovieUntil = now + 4000;
                    invalidate();
                    return true;
                }
                if (!stateKnown && modeChip.contains(x, y)) {
                    manualCtx = ctx == ControlLayout.FOOT ? ControlLayout.CAR : ctx == ControlLayout.CAR ? ControlLayout.MENU : ControlLayout.FOOT;
                    ctx = manualCtx;
                    onContextChanged();
                    buzz();
                    invalidate();
                    return true;
                }
                if (padHide && padConnected) return true;
                Touch t = new Touch();
                t.lastX = x;
                t.lastY = y;
                t.lastT = now;
                ControlLayout.Element e = hitButton(x, y);
                if (e != null && e.pointer < 0) {
                    t.role = ROLE_BUTTON;
                    t.el = e;
                    touches.put(pid, t);
                    pressButton(e, pid);
                } else if (ctx == ControlLayout.MENU) {
                    if (touchOf(ROLE_MENU) == null) {
                        t.role = ROLE_MENU;
                        touches.put(pid, t);
                        mouseTo(x, y);
                        mouseButton(1, true);
                    }
                } else if (x < getWidth() * 0.42f && touchOf(ROLE_STICK) == null
                        && !layout.stick.hidden) {
                    t.role = ROLE_STICK;
                    touches.put(pid, t);
                    startStick(x, y);
                } else if (touchOf(ROLE_LOOK) == null) {
                    t.role = ROLE_LOOK;
                    touches.put(pid, t);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    Touch t = touches.get(ev.getPointerId(i));
                    if (t == null) continue;
                    float x = ev.getX(i), y = ev.getY(i);
                    switch (t.role) {
                        case ROLE_STICK:
                            updateStick(x, y);
                            break;
                        case ROLE_LOOK:
                            look(t, x, y, now);
                            break;
                        case ROLE_MENU:
                            mouseTo(x, y);
                            break;
                        case ROLE_BUTTON:
                            if (fireLook && t.el.kind == ControlLayout.Kind.MOUSE_LEFT && ctx != ControlLayout.MENU) look(t, x, y, now);
                            else if (t.el.kind == ControlLayout.Kind.MOUSE_RIGHT && ctx != ControlLayout.MENU) look(t, x, y, now);
                            break;
                        default:
                            break;
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (a == MotionEvent.ACTION_CANCEL) {
                    releaseAll();
                    return true;
                }
                int pid = ev.getPointerId(idx);
                Touch t = touches.remove(pid);
                if (t == null) return true;
                switch (t.role) {
                    case ROLE_BUTTON:
                        if (t.el.pointer == pid) {
                            touches.put(pid, t);
                            releaseButton(t.el);
                        }
                        break;
                    case ROLE_STICK:
                        releaseStick();
                        break;
                    case ROLE_MENU:
                        mouseTo(ev.getX(idx), ev.getY(idx));
                        mouseButton(1, false);
                        break;
                    default:
                        break;
                }
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }

    // ------------------------------------------------------------------ edit mode

    void setEditing(boolean on, int context) {
        if (on) releaseAll();
        editing = on;
        editCtx = context;
        selected = null;
        if (listener != null) listener.onSelection(null);
        invalidate();
    }

    boolean isEditing() {
        return editing;
    }

    void setEditContext(int c) {
        editCtx = c;
        if (selected != null && (selected.ctx & c) == 0) {
            selected = null;
            if (listener != null) listener.onSelection(null);
        }
        invalidate();
    }

    void setSnap(boolean s) {
        snap = s;
        invalidate();
    }

    boolean snap() {
        return snap;
    }

    ControlLayout.Element selected() {
        return selected;
    }

    void pushUndo() {
        undo.add(layout.snapshot());
        if (undo.size() > 40) undo.remove(0);
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    void undo() {
        if (undo.isEmpty()) return;
        layout.restore(undo.remove(undo.size() - 1));
        relayout();
        cache.clear();
        if (listener != null) listener.onSelection(selected);
        invalidate();
    }

    void resetLayout() {
        pushUndo();
        for (ControlLayout.Element e : layout.all) {
            e.fx = e.fy = -1;
            e.scale = 1;
            e.alpha = 1;
            e.hidden = false;
        }
        relayout();
        cache.clear();
        if (listener != null) listener.onSelection(selected);
        invalidate();
    }

    /** Applies a change to the selected element (size / opacity / hidden) from the editor bar. */
    void changeSelected(float dScale, float dAlpha, boolean toggleHidden) {
        if (selected == null) return;
        pushUndo();
        selected.scale = Math.max(0.5f, Math.min(2.2f, selected.scale + dScale));
        selected.alpha = Math.max(0.2f, Math.min(1f, selected.alpha + dAlpha));
        if (toggleHidden) selected.hidden = !selected.hidden;
        relayout();
        cache.clear();
        if (listener != null) listener.onSelection(selected);
        invalidate();
    }

    void save() {
        layout.save(getContext());
    }

    private float snapV(float v) {
        if (!snap) return v;
        float g = 8 * density;
        return Math.round(v / g) * g;
    }

    private boolean editTouch(MotionEvent ev) {
        int a = ev.getActionMasked();
        int idx = ev.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN: {
                float x = ev.getX(), y = ev.getY();
                ControlLayout.Element hit = null;
                float best = Float.MAX_VALUE;
                for (ControlLayout.Element e : layout.all) {
                    if ((e.ctx & editCtx) == 0) continue;
                    float r = e.kind == ControlLayout.Kind.STICK ? e.r : e.r + 10 * density;
                    float d = (float) Math.hypot(x - e.cx, y - e.cy);
                    if (d <= r && d / r < best) {
                        best = d / r;
                        hit = e;
                    }
                }
                selected = hit;
                if (listener != null) listener.onSelection(hit);
                if (hit != null) {
                    undoCandidate = layout.snapshot();
                    dragPointer = ev.getPointerId(0);
                    dragDx = hit.cx - x;
                    dragDy = hit.cy - y;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                if (selected != null && ev.getPointerCount() == 2) {
                    pinchStartDist = Math.max(1, dist(ev));
                    pinchStartScale = selected.scale;
                    dragPointer = -1;
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (selected == null) return true;
                if (ev.getPointerCount() >= 2 && pinchStartDist > 0) {
                    selected.scale = Math.max(0.5f, Math.min(2.2f, pinchStartScale * dist(ev) / pinchStartDist));
                    relayout();
                    cache.clear();
                    if (listener != null) listener.onSelection(selected);
                } else if (dragPointer >= 0) {
                    int i = ev.findPointerIndex(dragPointer);
                    if (i < 0) return true;
                    float w = getWidth(), h = getHeight();
                    float nx = Math.max(selected.r * 0.5f, Math.min(w - selected.r * 0.5f, snapV(ev.getX(i) + dragDx)));
                    float ny = Math.max(selected.r * 0.5f, Math.min(h - selected.r * 0.5f, snapV(ev.getY(i) + dragDy)));
                    selected.fx = nx / w;
                    selected.fy = ny / h;
                    relayout();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                pinchStartDist = 0;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragPointer = -1;
                pinchStartDist = 0;
                if (undoCandidate != null && !java.util.Arrays.equals(undoCandidate, layout.snapshot())) {
                    undo.add(undoCandidate);
                    if (undo.size() > 40) undo.remove(0);
                    if (listener != null) listener.onSelection(selected);
                }
                undoCandidate = null;
                return true;
            default:
                return true;
        }
    }

    private static float dist(MotionEvent ev) {
        return (float) Math.hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (editing && snap) drawGrid(c, w, h);
        boolean hiddenAll = !editing && ((padHide && padConnected) || movieHidden());

        // Mode chip (only when the game does not report its state) and the FPS line.
        if (!editing && live) {
            if (!stateKnown && !hiddenAll) drawModeChip(c);
            if (showFps && !hud.isEmpty()) {
                text.setTextSize(12 * density);
                text.setColor(Ui.alpha(0xFF000000, 0.6f));
                c.drawText(hud, w / 2f + density, 22 * density + density, text);
                text.setColor(hudColor);
                c.drawText(hud, w / 2f, 22 * density, text);
            }
        }
        if (hiddenAll) {
            if (movie && !editing) drawMovieHint(c, w, h);
            return;
        }

        for (ControlLayout.Element e : layout.all) {
            if (!visible(e)) continue;
            float a = opacity * e.alpha;
            if (editing) a = e.hidden ? 0.28f : Math.max(0.45f, a);
            if (e.kind == ControlLayout.Kind.STICK) {
                drawStick(c, e, a);
            } else {
                boolean pressed = !editing && e.pressed();
                Bitmap b = button(e, pressed);
                blit.setAlpha(Math.round(Math.min(1f, pressed ? Math.max(a, 0.9f) : a) * 255));
                c.drawBitmap(b, e.cx - b.getWidth() / 2f, e.cy - b.getHeight() / 2f, blit);
            }
            if (labels || editing) drawLabel(c, e, editing ? 0.9f : Math.min(0.85f, a));
            if (editing && e.hidden) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * density);
                paint.setColor(Ui.alpha(Ui.RED, 0.8f));
                c.drawLine(e.cx - e.r * 0.7f, e.cy + e.r * 0.7f, e.cx + e.r * 0.7f, e.cy - e.r * 0.7f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        if (editing && selected != null) drawSelection(c, selected);
    }

    private void drawGrid(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        float g = 32 * density;
        paint.setColor(0x14FFFFFF);
        for (float x = g; x < w; x += g) c.drawLine(x, 0, x, h, paint);
        for (float y = g; y < h; y += g) c.drawLine(0, y, w, y, paint);
        paint.setColor(0x26E0A63E);
        c.drawLine(w / 2f, 0, w / 2f, h, paint);
        c.drawLine(0, h / 2f, w, h / 2f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawModeChip(Canvas c) {
        String s = ctx == ControlLayout.CAR ? Ui.t("АВТО", "CAR") : ctx == ControlLayout.FOOT ? Ui.t("ПЕШКОМ", "ON FOOT") : Ui.t("МЕНЮ", "MENU");
        text.setTextSize(12 * density);
        float tw = text.measureText(s);
        float x = 140 * density * uiScale, y = 26 * density;
        modeChip.set(x, y, x + tw + 28 * density, y + 30 * density);
        paint.setColor(Ui.alpha(0xFF15110E, 0.7f * opacity + 0.2f));
        c.drawRoundRect(modeChip, 15 * density, 15 * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(Ui.alpha(Ui.AMBER, 0.7f));
        c.drawRoundRect(modeChip, 15 * density, 15 * density, paint);
        paint.setStyle(Paint.Style.FILL);
        text.setColor(Ui.IVORY);
        c.drawText(s, modeChip.centerX(), modeChip.centerY() + 4.5f * density, text);
    }

    private void drawMovieHint(Canvas c, int w, int h) {
        text.setTextSize(11 * density);
        text.setColor(0x66FFFFFF);
        c.drawText(Ui.t("коснитесь, чтобы показать кнопки", "tap to show the buttons"), w / 2f, h - 14 * density, text);
    }

    private void drawLabel(Canvas c, ControlLayout.Element e, float a) {
        if (e.kind == ControlLayout.Kind.STICK && !editing) return;
        text.setTextSize(Math.max(9f, Math.min(12f, e.r / density * 0.34f)) * density);
        text.setLetterSpacing(0.08f);
        String s = e.label().toUpperCase(Locale.ROOT);
        float y = e.cy + e.r + 13 * density;
        text.setColor(Ui.alpha(0xFF000000, a * 0.7f));
        c.drawText(s, e.cx + density, y + density, text);
        text.setColor(Ui.alpha(Ui.IVORY, a));
        c.drawText(s, e.cx, y, text);
        text.setLetterSpacing(0);
    }

    private void drawSelection(Canvas c, ControlLayout.Element e) {
        float r = e.r + 7 * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(Ui.AMBER);
        paint.setPathEffect(dash);
        c.drawCircle(e.cx, e.cy, r, paint);
        paint.setPathEffect(null);
        // Corner handles hint that a pinch resizes.
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            double ang = Math.PI / 4 + i * Math.PI / 2;
            c.drawCircle(e.cx + (float) Math.cos(ang) * r, e.cy + (float) Math.sin(ang) * r, 4 * density, paint);
        }
    }

    private void drawStick(Canvas c, ControlLayout.Element e, float a) {
        boolean active = !editing && touchOf(ROLE_STICK) != null;
        float bx = editing || !active ? e.cx : stickX;
        float by = editing || !active ? e.cy : stickY;
        float kx = editing || !active ? bx : knobX, ky = editing || !active ? by : knobY;
        float idle = active || editing ? 1f : (fixedStick ? 0.85f : 0.45f);
        boolean running = edgeRun || sprintLatched;
        Bitmap base = stickBase(e.r, running);
        blit.setAlpha(Math.round(Math.min(1f, a * idle) * 255));
        c.drawBitmap(base, bx - base.getWidth() / 2f, by - base.getHeight() / 2f, blit);
        Bitmap knob = stickKnob(e.r * 0.4f, active);
        blit.setAlpha(Math.round(Math.min(1f, a * idle * 0.95f + 0.1f) * 255));
        c.drawBitmap(knob, kx - knob.getWidth() / 2f, ky - knob.getHeight() / 2f, blit);
        if (running && active) {
            text.setTextSize(10 * density);
            text.setColor(Ui.AMBER);
            c.drawText(Ui.t("БЕГ", "SPRINT"), bx, by - e.r - 8 * density, text);
        }
    }

    /** Stick base: smoked glass with a compass ring (amber while sprinting). Cached per size. */
    private Bitmap stickBase(float r, boolean running) {
        String k = "stick" + (running ? "+" : "-") + Math.round(r);
        Bitmap b = cache.get(k);
        if (b != null) return b;
        int size = (int) Math.ceil(r * 2 + 4 * density);
        b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, r, 0x8C1C1712, 0x590C0A08, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f * density);
        p.setColor(running ? Ui.AMBER : Ui.alpha(Ui.IVORY, 0.5f));
        c.drawCircle(cx, cy, r - density, p);
        p.setStrokeWidth(1.2f * density);
        p.setColor(Ui.alpha(Ui.IVORY, 0.35f));
        c.drawCircle(cx, cy, r * 0.62f, p);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 - Math.PI / 2;
            boolean main = i % 2 == 0;
            float r1 = r * (main ? 0.78f : 0.84f), r2 = r * 0.93f;
            p.setColor(main ? Ui.alpha(Ui.AMBER, 0.8f) : Ui.alpha(Ui.IVORY, 0.4f));
            p.setStrokeWidth((main ? 2.2f : 1.2f) * density);
            c.drawLine(cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1, cx + (float) Math.cos(ang) * r2, cy + (float) Math.sin(ang) * r2, p);
        }
        cache.put(k, b);
        return b;
    }

    private Bitmap stickKnob(float kr, boolean active) {
        String k = "knob" + (active ? "+" : "-") + Math.round(kr);
        Bitmap b = cache.get(k);
        if (b != null) return b;
        int size = (int) Math.ceil(kr * 2 + 4 * density);
        b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx - kr * 0.3f, cy - kr * 0.3f, kr * 1.3f,
                active ? 0xFFF3C46A : 0xFF4A3D2E, active ? 0xFFB97F25 : 0xFF1B1611, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, kr, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * density);
        p.setColor(active ? 0xB3FFE2A6 : Ui.alpha(Ui.IVORY, 0.7f));
        c.drawCircle(cx, cy, kr - 0.7f * density, p);
        cache.put(k, b);
        return b;
    }

    /** Pre-rendered button picture (normal or pressed), cached per size. */
    private Bitmap button(ControlLayout.Element e, boolean pressed) {
        String k = e.id + (pressed ? "+" : "-") + Math.round(e.r);
        Bitmap b = cache.get(k);
        if (b != null) return b;
        float pad = 6 * density;
        int size = (int) Math.ceil(e.r * 2 + pad * 2);
        b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f, r = e.r;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Soft drop shadow.
        p.setShader(new RadialGradient(cx, cy + r * 0.06f, r + pad, new int[]{0x66000000, 0x33000000, 0x00000000}, new float[]{0.7f, 0.88f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r + pad, p);
        if (pressed) {
            // Lit: amber lamp glass.
            p.setShader(new RadialGradient(cx, cy - r * 0.35f, r * 1.4f, new int[]{0xFFFFD98C, 0xFFE0A63E, 0xFF9A6418}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
        } else {
            // Smoked glass.
            p.setShader(new RadialGradient(cx, cy - r * 0.4f, r * 1.5f, new int[]{0xD82E261E, 0xC8181410, 0xB00B0907}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
            // Glass highlight on the upper half.
            p.setShader(new LinearGradient(0, cy - r, 0, cy, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawOval(new RectF(cx - r * 0.78f, cy - r * 0.92f, cx + r * 0.78f, cy - r * 0.05f), p);
            p.setShader(null);
        }
        // Rings: a fine ivory edge and an art-deco amber arc across the top.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * density);
        p.setColor(pressed ? 0xFFFFE7B3 : 0x8CF0E6D2);
        c.drawCircle(cx, cy, r - 0.75f * density, p);
        p.setStrokeWidth(2.2f * density);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(pressed ? 0xFF6B4510 : 0xCCE0A63E);
        float ar = r - 4.5f * density;
        c.drawArc(new RectF(cx - ar, cy - ar, cx + ar, cy + ar), 235, 70, false, p);
        p.setStyle(Paint.Style.FILL);
        Glyphs.draw(c, e.glyph, cx, cy + (e.glyph.equals("fire") ? r * 0.04f : 0), r * 0.46f, pressed ? Ui.INK : Ui.IVORY, p);
        cache.put(k, b);
        return b;
    }

    // ------------------------------------------------------------------ lifecycle

    void detach() {
        live = false;
        removeCallbacks(poll);
        SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) sm.unregisterListener(this);
        InputManager im = (InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
        if (im != null) im.unregisterInputDeviceListener(this);
    }
}
