package com.wastelanddroid.input;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import com.wastelanddroid.engine.FalloutInput;
import com.wastelanddroid.engine.NativeInput;
import com.wastelanddroid.util.Prefs;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-game touch controls over the engine surface. Keys come from {@link ScreenKeyStore}
 * (Fallout Droid layout format). Touches that hit no key control the mouse:
 * <ul>
 *   <li>"trackpad": finger motion = relative mouse motion (look), a short tap = left click;</li>
 *   <li>"direct": the cursor follows the finger, a tap = left click at that point (menus);</li>
 *   <li>"auto" (default): like trackpad, plus the cursor jumps to taps so menus work too.</li>
 * </ul>
 */
public class ScreenKeysOverlay extends View {
    private static final long TAP_MS = 220;
    private static final float TAP_SLOP_DP = 10f;

    private final List<ScreenKey> keys = new ArrayList<>();
    private final Map<String, Bitmap> icons = new HashMap<>();
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    // pointer id -> key index it pressed (buttons), stick/dpad codes it holds
    private final Map<Integer, Integer> pressed = new HashMap<>();
    private final Map<Integer, List<Integer>> held = new HashMap<>();
    private final Map<Integer, Integer> armed = new HashMap<>(); // key index -> sector/arm
    private final Set<Integer> latched = new HashSet<>();
    // look pointer
    private int lookId = -1;
    private float lastX, lastY, downX, downY;
    private long downAt;
    private boolean lookMoved;

    private int tint;
    private float sens;
    private boolean invertY, haptics, tapFires;
    private String mode;
    /** All keys' opacity, from the in-game panel (keys_opacity, 10..100 %). */
    private float opacityScale = 1f;

    // ---- smart hiding and menu gestures, by what the engine shows (NativeInput.uiState)
    public static final int UI_PLAY = 1, UI_MENU = 2, UI_FILM = 3, UI_PIPBOY = 4, UI_TALK = 5,
            UI_MAIN_MENU = 6, UI_CHARGEN = 7, UI_PAUSE = 8, UI_MESSAGE = 9, UI_PIPBOY_MAP = 10;
    private boolean smartHide = true;
    /** The engine's screen (0 not known yet). */
    private int uiState;
    /** A menu, the Pip-Boy, a conversation...: the screen is a pointer, drags scroll. */
    private boolean menuMode;
    /** Container, barter, repair, lockpick, V.A.T.S. targets: Esc, E, R, Enter, V, Tab. */
    private static final int[] OTHER_MENU_CODES = {1, 18, 19, 28, 47, 15};

    private static boolean has(ScreenKey k, int code) {
        for (int c : k.codes) if (c == code) return true;
        return false;
    }

    /** Whether a key is shown on this screen (smart hiding). */
    private boolean keyShown(ScreenKey k) {
        if (editing || !smartHide) return true;
        switch (uiState) {
            case UI_PIPBOY:
            case UI_PIPBOY_MAP:
                return has(k, 15); // the Pip-Boy key, to close it
            case UI_TALK:
            case UI_MAIN_MENU:
            case UI_CHARGEN:
            case UI_MESSAGE:
                return false; // chosen with the finger
            case UI_PAUSE:
                return has(k, 1);
            case UI_FILM:
                return false; // a scripted scene or a film: nothing to press
            case UI_MENU:
                for (int c : OTHER_MENU_CODES) if (has(k, c)) return true;
                return false;
            default:
                return playKeyShown(k);
        }
    }

    /** What the player may do (NativeInput.playFlags): bit 0 weapon drawn, 1 fighting
     * locked, 2 sneaking locked, 3 movement locked, 4 Pip-Boy locked. */
    private int playFlags;

    public void setPlayFlags(int flags) {
        if (flags == playFlags) return;
        playFlags = flags;
        invalidate();
    }

    /** While playing: keys for what the player cannot do now go (a holstered weapon's fire
     * and aim; what the story's scripts locked: fighting, sneaking, moving, the Pip-Boy). */
    private boolean playKeyShown(ScreenKey k) {
        boolean weapon = (playFlags & 1) != 0, fight = (playFlags & 2) != 0, sneak = (playFlags & 4) != 0,
                move = (playFlags & 8) != 0, pip = (playFlags & 16) != 0;
        if (fight && (has(k, 272) || has(k, 273) || has(k, 19) || has(k, 47))) return false;
        if (!weapon && (has(k, 272) || has(k, 273))) return false;
        if (sneak && has(k, 29)) return false;
        // A scene the story holds the player in (movement taken: the opening at Doc
        // Mitchell's, the vault's birthday): only the activate / talk key stays.
        if (move) return has(k, 18);
        if (pip && has(k, 15)) return false;
        return true;
    }

    /** Hidden now (smart hiding, or a hidden key). */
    private boolean hiddenNow(ScreenKey k) {
        return k.shape == 10 || !keyShown(k);
    }

    /** What the engine shows now (polled by NativeGameActivity). */
    public void setUiState(int state) {
        if (state == uiState) return;
        boolean menu = state >= UI_MENU && state != UI_FILM;
        if (menu != menuMode || state != uiState) {
            // Held keys (a stick, a pressed key) let go; latched toggles (sneak) stay on.
            for (List<Integer> c : held.values()) press(c, false);
            for (Integer k : pressed.values()) if (k != null && k < keys.size() && !latched.contains(k)) press(keys.get(k).codes, false);
            held.clear();
            pressed.clear();
            armed.clear();
            endGesture();
            lookId = -1;
        }
        uiState = state;
        menuMode = menu;
        invalidate();
    }

    /** Kept for callers of the first smart-hiding build. */
    public void setMenuMode(boolean on) {
        setUiState(on ? UI_MENU : UI_PLAY);
    }

    // Menu gestures: a tap clicks; a drag scrolls (wheel steps, the cursor stays where the
    // finger came down, over the list); on the Pip-Boy's map a drag pans it (left button
    // held); a press held still then dragged drags with the left button (sliders, scroll
    // bars); two fingers pinch to zoom (wheel steps).
    private static final int G_NONE = 0, G_PENDING = 1, G_SCROLL = 2, G_DRAG = 3, G_PINCH = 4;
    private static final long HOLD_TO_DRAG_MS = 350;
    private static final float SCROLL_STEP_DP = 34f;
    private static final float PINCH_STEP = 0.12f;
    private int gesture = G_NONE;
    private int pinchId = -1;
    private float pinchX, pinchY, pinchBase, scrollAcc;
    private boolean lmbHeld;

    private void endGesture() {
        if (lmbHeld) NativeInput.key(272, false);
        lmbHeld = false;
        gesture = G_NONE;
        pinchId = -1;
        scrollAcc = 0;
    }

    private float pinchDistance() {
        return (float) Math.hypot(lastX - pinchX, lastY - pinchY);
    }

    private void menuDown(int id, float x, float y, long t) {
        if (lookId == -1) {
            lookId = id;
            lastX = downX = x;
            lastY = downY = y;
            downAt = t;
            lookMoved = false;
            gesture = G_PENDING;
            scrollAcc = 0;
            NativeInput.pointTo(x, y);
        } else if (pinchId == -1) {
            // A second finger: pinch (zoom) from here on.
            if (lmbHeld) {
                NativeInput.key(272, false);
                lmbHeld = false;
            }
            pinchId = id;
            pinchX = x;
            pinchY = y;
            pinchBase = Math.max(1f, pinchDistance());
            gesture = G_PINCH;
            lookMoved = true;
        }
    }

    private void menuMove(int id, float x, float y, long t) {
        if (id == pinchId) {
            pinchX = x;
            pinchY = y;
        } else if (id == lookId) {
            float dy = y - lastY;
            lastX = x;
            lastY = y;
            if (gesture == G_PENDING && Math.hypot(x - downX, y - downY) > dp(TAP_SLOP_DP)) {
                lookMoved = true;
                boolean held = t - downAt >= HOLD_TO_DRAG_MS;
                // Sideways first: a slider (the creator's, the settings'), dragged at once;
                // up or down first: a list, scrolled.
                boolean sideways = Math.abs(x - downX) > Math.abs(y - downY) * 1.3f;
                if (uiState == UI_PIPBOY_MAP || held || sideways) {
                    gesture = G_DRAG;
                    // The pointer has stood at the press point since the finger came down.
                    NativeInput.key(272, true);
                    lmbHeld = true;
                } else {
                    gesture = G_SCROLL;
                    dy = y - downY;
                }
            }
            if (gesture == G_DRAG) {
                NativeInput.pointTo(x, y);
            } else if (gesture == G_SCROLL) {
                // Natural scrolling: the finger up shows what is below (wheel down).
                scrollAcc += dy;
                float step = dp(SCROLL_STEP_DP);
                while (Math.abs(scrollAcc) >= step) {
                    float dir = Math.signum(scrollAcc);
                    NativeInput.scroll(dir);
                    scrollAcc -= dir * step;
                }
            }
        } else {
            return;
        }
        if (gesture == G_PINCH && lookId != -1 && pinchId != -1) {
            float d = pinchDistance();
            float ratio = d / pinchBase;
            if (ratio > 1f + PINCH_STEP) {
                NativeInput.scroll(1f); // zoom in
                pinchBase = d;
            } else if (ratio < 1f - PINCH_STEP) {
                NativeInput.scroll(-1f); // zoom out
                pinchBase = d;
            }
        }
    }

    private void menuUp(int id, float x, float y, long t) {
        if (id == pinchId) {
            pinchId = -1;
            return;
        }
        if (id != lookId) return;
        lookId = -1;
        boolean tap = gesture == G_PENDING && !lookMoved && t >= 0;
        if (gesture == G_DRAG) {
            NativeInput.pointTo(x, y);
        }
        endGesture();
        if (tap) clickAt(x, y);
    }

    // The engine finds what is under the pointer from the frames before a click (hover):
    // a click in the frame the pointer moved lands on nothing. So the pointer goes first,
    // the button a few frames later.
    private static final long CLICK_PRESS_DELAY_MS = 50, CLICK_RELEASE_DELAY_MS = 110;
    private final android.os.Handler clicks = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable lmbDown = () -> NativeInput.key(272, true);
    private final Runnable lmbUp = () -> NativeInput.key(272, false);

    private void clickAt(float x, float y) {
        clicks.removeCallbacks(lmbDown);
        clicks.removeCallbacks(lmbUp);
        NativeInput.pointTo(x, y);
        clicks.postDelayed(lmbDown, CLICK_PRESS_DELAY_MS);
        clicks.postDelayed(lmbUp, CLICK_RELEASE_DELAY_MS);
    }

    // ---- editor mode (ControlsEditorActivity)
    private boolean editing;
    private int dragKey = -1;
    private int dragId = -1;
    private float dragDx, dragDy, dragStartX, dragStartY;
    private boolean dragMoved;
    private kotlin.jvm.functions.Function1<Object, Object> onSelect;

    /** Editor constructor (1.x signature: the second argument was a key callback, unused here). */
    public ScreenKeysOverlay(Context c, Object unusedKeyCallback) {
        this(c);
    }

    public ScreenKeysOverlay(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        try {
            label.setTypeface(Typeface.createFromAsset(c.getAssets(), "fonts/FalloutMonofonto.ttf"));
        } catch (RuntimeException ignored) {
        }
        reload();
    }

    /** Re-reads layout and settings (after the editor changed them). */
    public void reload() {
        Context c = getContext();
        keys.clear();
        if (ScreenKeyStore.shown(c)) keys.addAll(ScreenKeyStore.load(c));
        tint = ScreenKeyStore.tint(c, 0xFFFFB840);
        sens = FalloutInput.lookSensitivity(c);
        invertY = FalloutInput.invertY(c);
        haptics = FalloutInput.haptics(c);
        tapFires = FalloutInput.tapFires(c);
        mode = Prefs.get(c).getString("touch_mode", "auto");
        smartHide = Prefs.get(c).getBoolean("smart_hide", true);
        opacityScale = Math.max(0.1f, Math.min(1f, Prefs.get(c).getInt("keys_opacity", 100) / 100f));
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float half(ScreenKey k) {
        float h = dp(k.size) / 2f;
        return k.shape == 4 ? h * 1.7f : h;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        for (int i = 0; i < keys.size(); i++) {
            ScreenKey k = keys.get(i);
            if (hiddenNow(k)) continue;
            float cx = k.x * w, cy = k.y * h, hh = dp(k.size) / 2f, hw = half(k);
            boolean down = pressed.containsValue(i) || latched.contains(i) || armed.containsKey(i);
            int a = Math.round(255 * Math.max(0.1f, Math.min(1f, k.opacity)) * (down ? 1f : 0.75f) * (editing ? 1f : opacityScale));
            body.setColor(Color.argb(down ? a * 2 / 3 : a / 3, Color.red(tint), Color.green(tint), Color.blue(tint)));
            ring.setColor(Color.argb(a, Color.red(tint), Color.green(tint), Color.blue(tint)));
            ring.setStrokeWidth(dp(down ? 2.5f : 1.5f));
            rect.set(cx - hw, cy - hh, cx + hw, cy + hh);
            drawBody(canvas, k, cx, cy, hw, hh);
            if (k.shape == 6 || k.shape == 5) drawStick(canvas, k, i, cx, cy, hh, a);
            Bitmap icon = icon(k.image);
            if (icon != null) {
                float s = hh * 1.2f;
                rect.set(cx - s, cy - s, cx + s, cy + s);
                bmp.setAlpha(a);
                canvas.drawBitmap(icon, null, rect, bmp);
            }
            String text = !k.text.isEmpty() ? k.text : k.glyph;
            if (k.labels && !text.isEmpty() && (icon == null || !k.text.isEmpty())) {
                label.setColor(Color.argb(a, 255, 255, 255));
                label.setTextSize(icon == null ? Math.min(hh * 0.8f, dp(16)) : dp(9));
                float ty = icon == null ? cy + label.getTextSize() / 3f : cy + hh + dp(11);
                canvas.drawText(text, cx, ty, label);
            }
        }
    }

    private void drawBody(Canvas canvas, ScreenKey k, float cx, float cy, float hw, float hh) {
        switch (k.shape) {
            case 2: case 5: case 6:
                canvas.drawCircle(cx, cy, hh, body);
                canvas.drawCircle(cx, cy, hh, ring);
                break;
            case 7:
                canvas.drawOval(rect, body);
                canvas.drawOval(rect, ring);
                break;
            case 8: poly(cx, cy, hh, 4, -90f); canvas.drawPath(path, body); canvas.drawPath(path, ring); break;
            case 9: poly(cx, cy, hh, 6, 0f); canvas.drawPath(path, body); canvas.drawPath(path, ring); break;
            default:
                float r = k.shape == 1 ? hh * 0.3f : k.shape == 3 ? hh * 0.12f : k.shape == 4 ? hh * 0.45f : hh;
                canvas.drawRoundRect(rect, r, r, body);
                canvas.drawRoundRect(rect, r, r, ring);
        }
    }

    private void poly(float cx, float cy, float r, int n, float startDeg) {
        path.reset();
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(startDeg + 360.0 * i / n);
            float x = cx + (float) (r * Math.cos(a)), y = cy + (float) (r * Math.sin(a));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    private void drawStick(Canvas canvas, ScreenKey k, int idx, float cx, float cy, float hh, int a) {
        Integer s = armed.get(idx);
        float kx = cx, ky = cy;
        if (s != null && s >= 0) {
            int sectors = k.shape == 6 ? 8 : 4;
            double ang = Math.toRadians(-90 + 360.0 * s / sectors);
            kx += (float) (Math.cos(ang) * hh * 0.45f);
            ky += (float) (Math.sin(ang) * hh * 0.45f);
        }
        body.setColor(Color.argb(a / 2, Color.red(tint), Color.green(tint), Color.blue(tint)));
        canvas.drawCircle(kx, ky, hh * 0.38f, body);
    }

    private Bitmap icon(String name) {
        if (name == null || name.isEmpty()) return null;
        if (icons.containsKey(name)) return icons.get(name);
        Bitmap b = null;
        File f = new File(new File(getContext().getFilesDir(), "screenkeys"), name);
        if (f.isFile()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b == null) {
            try (InputStream in = getContext().getAssets().open("screenkeys/" + name)) {
                b = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
            }
        }
        icons.put(name, b);
        return b;
    }

    // ------------------------------------------------------------------ touch
    private int keyAt(float x, float y) {
        int w = getWidth(), h = getHeight();
        for (int i = keys.size() - 1; i >= 0; i--) {
            ScreenKey k = keys.get(i);
            if (!keyShown(k)) continue;
            float cx = k.x * w, cy = k.y * h, hh = dp(k.size) / 2f + dp(6), hw = half(k) + dp(6);
            if (Math.abs(x - cx) <= hw && Math.abs(y - cy) <= hh) return i;
        }
        return -1;
    }

    /** Sector 0..7 clockwise from up (8-way), or 0..3 (4-way), -1 in the dead zone. */
    private int sector(ScreenKey k, float x, float y, int n) {
        float cx = k.x * getWidth(), cy = k.y * getHeight();
        float dx = x - cx, dy = y - cy;
        if (Math.hypot(dx, dy) < dp(k.size) / 2f * 0.24f) return -1;
        double deg = Math.toDegrees(Math.atan2(dy, dx)) + 90.0; // 0 = up
        int s = (int) Math.round(deg / (360.0 / n));
        return ((s % n) + n) % n;
    }

    private List<Integer> stickCodes(ScreenKey k, int sector) {
        List<Integer> out = new ArrayList<>();
        if (sector < 0) return out;
        if (k.shape == 5) {
            int c = sector < k.codes.size() ? k.codes.get(sector) : 0;
            if (c != 0) out.add(c);
            return out;
        }
        int c = sector < k.codes.size() ? k.codes.get(sector) : 0;
        if (c != 0) {
            out.add(c);
        } else if (sector % 2 == 1) { // diagonal without its own code: both neighbours
            int a = (sector + 7) % 8, b = (sector + 1) % 8;
            if (a < k.codes.size() && k.codes.get(a) != 0) out.add(k.codes.get(a));
            if (b < k.codes.size() && k.codes.get(b) != 0) out.add(k.codes.get(b));
        }
        return out;
    }

    private void press(List<Integer> codes, boolean down) {
        for (int c : codes) if (c != 0) NativeInput.key(c, down);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editing) return editTouch(e);
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerDown(e.getPointerId(idx), e.getX(idx), e.getY(idx), e.getEventTime());
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) pointerMove(e.getPointerId(i), e.getX(i), e.getY(i), e.getEventTime());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                pointerUp(e.getPointerId(idx), e.getX(idx), e.getY(idx), e.getEventTime());
                break;
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) pointerUp(e.getPointerId(i), e.getX(i), e.getY(i), -1);
                break;
            default:
                return false;
        }
        return true;
    }

    private void pointerDown(int id, float x, float y, long t) {
        int k = keyAt(x, y);
        if (k < 0 && menuMode) {
            menuDown(id, x, y, t);
            return;
        }
        if (k < 0 && lookId != -1 && pinchId == -1) {
            // A second finger on the look area: pinch to zoom the view (the mouse wheel:
            // third-person distance, first <-> third person), as on PC.
            pinchId = id;
            pinchX = x;
            pinchY = y;
            pinchBase = Math.max(1f, pinchDistance());
            lookMoved = true;
            return;
        }
        if (k < 0) {
            if (lookId == -1) {
                lookId = id;
                lastX = downX = x;
                lastY = downY = y;
                downAt = t;
                lookMoved = false;
                if (direct()) NativeInput.pointTo(x, y);
            }
            return;
        }
        ScreenKey key = keys.get(k);
        if (haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (key.shape == 6 || key.shape == 5) {
            int s = sector(key, x, y, key.shape == 6 ? 8 : 4);
            List<Integer> codes = stickCodes(key, s);
            held.put(id, codes);
            pressed.put(id, k);
            if (s >= 0) armed.put(k, s);
            press(codes, true);
        } else if (key.toggle) {
            if (latched.remove(k)) press(key.codes, false);
            else { latched.add(k); press(key.codes, true); }
        } else {
            pressed.put(id, k);
            press(key.codes, true);
        }
        invalidate();
    }

    /** The finger is the pointer: "direct" mode, or any menu. */
    private boolean direct() {
        return menuMode || "direct".equals(mode);
    }

    private void pointerMove(int id, float x, float y, long t) {
        if (menuMode && (id == lookId || id == pinchId)) {
            menuMove(id, x, y, t);
            return;
        }
        if (!menuMode && id == pinchId) {
            pinchX = x;
            pinchY = y;
            gamePinch();
            return;
        }
        if (!menuMode && id == lookId && pinchId != -1) {
            // Both fingers pinch: the view does not turn meanwhile.
            lastX = x;
            lastY = y;
            gamePinch();
            return;
        }
        if (id == lookId) {
            float dx = x - lastX, dy = y - lastY;
            lastX = x;
            lastY = y;
            if (Math.hypot(x - downX, y - downY) > dp(TAP_SLOP_DP)) lookMoved = true;
            if (direct()) {
                NativeInput.pointTo(x, y);
            } else if (dx != 0 || dy != 0) {
                NativeInput.mouseMove(dx * sens, (invertY ? -dy : dy) * sens);
            }
            return;
        }
        Integer k = pressed.get(id);
        if (k == null) return;
        ScreenKey key = keys.get(k);
        if (key.shape != 6 && key.shape != 5) return;
        int s = sector(key, x, y, key.shape == 6 ? 8 : 4);
        List<Integer> now = stickCodes(key, s);
        List<Integer> before = held.get(id);
        if (before == null) before = Collections.emptyList();
        if (!now.equals(before)) {
            for (int c : before) if (!now.contains(c)) NativeInput.key(c, false);
            for (int c : now) if (!before.contains(c)) NativeInput.key(c, true);
            held.put(id, now);
            if (s >= 0) armed.put(k, s); else armed.remove(k);
            invalidate();
        }
    }

    private void gamePinch() {
        float d = pinchDistance();
        float ratio = d / pinchBase;
        if (ratio > 1f + PINCH_STEP) {
            NativeInput.scroll(1f); // fingers apart: closer
            pinchBase = d;
        } else if (ratio < 1f - PINCH_STEP) {
            NativeInput.scroll(-1f); // together: further out
            pinchBase = d;
        }
    }

    private void pointerUp(int id, float x, float y, long t) {
        if (menuMode && (id == lookId || id == pinchId)) {
            menuUp(id, x, y, t);
            return;
        }
        if (!menuMode && id == pinchId) {
            pinchId = -1;
            return;
        }
        if (!menuMode && id == lookId && pinchId != -1) {
            // The look finger lifted first: the other finger looks on, no tap.
            lookId = pinchId;
            pinchId = -1;
            lastX = pinchX;
            lastY = pinchY;
            return;
        }
        if (id == lookId) {
            lookId = -1;
            boolean tap = !lookMoved && t >= 0 && t - downAt <= TAP_MS;
            if (tap && (tapFires || menuMode || !"trackpad".equals(mode))) {
                if (menuMode || !"trackpad".equals(mode)) {
                    clickAt(x, y);
                } else {
                    NativeInput.key(272, true);
                    NativeInput.key(272, false);
                }
            }
            return;
        }
        Integer k = pressed.remove(id);
        List<Integer> codes = held.remove(id);
        if (codes != null) {
            press(codes, false);
            if (k != null) armed.remove(k);
        } else if (k != null) {
            press(keys.get(k).codes, false);
        }
        invalidate();
    }

    /** Releases everything (activity paused). */
    public void releaseAll() {
        for (List<Integer> c : held.values()) press(c, false);
        for (Integer k : pressed.values()) if (k != null && k < keys.size()) press(keys.get(k).codes, false);
        for (Integer k : latched) if (k < keys.size()) press(keys.get(k).codes, false);
        held.clear();
        pressed.clear();
        latched.clear();
        armed.clear();
        endGesture();
        lookId = -1;
        invalidate();
    }

    // =================================================================== editor API
    public void setOnSelect(kotlin.jvm.functions.Function1<Object, Object> cb) { onSelect = cb; }

    /** Loads an encoded layout (Fallout Droid format). */
    public void load(String encoded) {
        keys.clear();
        keys.addAll(ScreenKeyStore.decode(encoded));
        invalidate();
    }

    public void startEditing() { editing = true; releaseAll(); invalidate(); }

    public void stopEditing() { editing = false; invalidate(); }

    public String export() { return ScreenKeyStore.encode(keys); }

    public void refreshAppearance() {
        tint = ScreenKeyStore.tint(getContext(), tint);
        icons.clear();
        invalidate();
    }

    private int indexOf(java.util.List<Integer> codes) {
        if (codes == null) return -1;
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).codes.equals(codes)) return i;
        return -1;
    }

    public ScreenKey stateOf(java.util.List<Integer> codes) {
        int i = indexOf(codes);
        return i < 0 ? null : keys.get(i);
    }

    public void add(java.util.List<Integer> codes) {
        keys.add(new ScreenKey(new ArrayList<>(codes), 0.5f, 0.5f, 56, "", "", "", 0.85f, 2, false, true));
        invalidate();
    }

    public void remove(java.util.List<Integer> codes) {
        int i = indexOf(codes);
        if (i >= 0) keys.remove(i);
        invalidate();
    }

    private void replace(java.util.List<Integer> codes, ScreenKey nk) {
        int i = indexOf(codes);
        if (i >= 0) keys.set(i, nk);
        invalidate();
    }

    public void resize(java.util.List<Integer> codes, int size) {
        ScreenKey k = stateOf(codes);
        if (k != null) replace(codes, k.with(null, size, null, null, null, null, null));
    }

    public void setOpacity(java.util.List<Integer> codes, float opacity) {
        ScreenKey k = stateOf(codes);
        if (k != null) replace(codes, k.with(null, null, null, opacity, null, null, null));
    }

    public void setShape(java.util.List<Integer> codes, int shape) {
        ScreenKey k = stateOf(codes);
        if (k != null) replace(codes, k.with(null, null, null, null, shape, null, null));
    }

    public void setToggle(java.util.List<Integer> codes, boolean toggle) {
        ScreenKey k = stateOf(codes);
        if (k != null) replace(codes, k.with(null, null, null, null, null, toggle, null));
    }

    public void setText(java.util.List<Integer> codes, String text) {
        ScreenKey k = stateOf(codes);
        if (k != null) replace(codes, k.with(null, null, text, null, null, null, null));
    }

    public ScreenKey setCodes(java.util.List<Integer> codes, java.util.List<Integer> newCodes) {
        ScreenKey k = stateOf(codes);
        if (k == null) return null;
        ScreenKey nk = k.with(new ArrayList<>(newCodes), null, null, null, null, null, null);
        replace(codes, nk);
        return nk;
    }

    public void scaleAll(float factor) {
        for (int i = 0; i < keys.size(); i++) {
            ScreenKey k = keys.get(i);
            keys.set(i, k.with(null, Math.max(24, Math.round(k.size * factor)), null, null, null, null, null));
        }
        invalidate();
    }

    public void opacityAll(float opacity) {
        for (int i = 0; i < keys.size(); i++) keys.set(i, keys.get(i).with(null, null, null, opacity, null, null, null));
        invalidate();
    }

    public void labelsAll(boolean labels) {
        for (int i = 0; i < keys.size(); i++) keys.set(i, keys.get(i).with(null, null, null, null, null, null, labels));
        invalidate();
    }

    private boolean editTouch(MotionEvent e) {
        int idx = e.getActionIndex();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX(idx), y = e.getY(idx);
                dragKey = keyAt(x, y);
                dragId = e.getPointerId(idx);
                dragMoved = false;
                dragStartX = x;
                dragStartY = y;
                if (dragKey >= 0) {
                    ScreenKey k = keys.get(dragKey);
                    dragDx = k.x * getWidth() - x;
                    dragDy = k.y * getHeight() - y;
                } else if (onSelect != null) {
                    onSelect.invoke(null);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragKey < 0) return true;
                int pi = e.findPointerIndex(dragId);
                if (pi < 0) return true;
                float x = e.getX(pi), y = e.getY(pi);
                if (Math.hypot(x - dragStartX, y - dragStartY) > dp(TAP_SLOP_DP)) dragMoved = true;
                if (dragMoved) {
                    float nx = Math.max(0f, Math.min(1f, (x + dragDx) / getWidth()));
                    float ny = Math.max(0f, Math.min(1f, (y + dragDy) / getHeight()));
                    keys.set(dragKey, keys.get(dragKey).at(nx, ny));
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragKey >= 0 && onSelect != null) onSelect.invoke(keys.get(dragKey).codes);
                dragKey = -1;
                dragId = -1;
                return true;
            default:
                return true;
        }
    }

}
