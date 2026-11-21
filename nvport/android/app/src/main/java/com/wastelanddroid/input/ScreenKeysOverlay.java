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
            if (k.shape == 10) continue;
            float cx = k.x * w, cy = k.y * h, hh = dp(k.size) / 2f, hw = half(k);
            boolean down = pressed.containsValue(i) || latched.contains(i) || armed.containsKey(i);
            int a = Math.round(255 * Math.max(0.1f, Math.min(1f, k.opacity)) * (down ? 1f : 0.75f));
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
                for (int i = 0; i < e.getPointerCount(); i++) pointerMove(e.getPointerId(i), e.getX(i), e.getY(i));
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
        if (k < 0) {
            if (lookId == -1) {
                lookId = id;
                lastX = downX = x;
                lastY = downY = y;
                downAt = t;
                lookMoved = false;
                if ("direct".equals(mode)) NativeInput.cursorTo(x, y);
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

    private void pointerMove(int id, float x, float y) {
        if (id == lookId) {
            float dx = x - lastX, dy = y - lastY;
            lastX = x;
            lastY = y;
            if (Math.hypot(x - downX, y - downY) > dp(TAP_SLOP_DP)) lookMoved = true;
            if ("direct".equals(mode)) {
                NativeInput.cursorTo(x, y);
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

    private void pointerUp(int id, float x, float y, long t) {
        if (id == lookId) {
            lookId = -1;
            boolean tap = !lookMoved && t >= 0 && t - downAt <= TAP_MS;
            if (tap && (tapFires || !"trackpad".equals(mode))) {
                if (!"trackpad".equals(mode)) NativeInput.cursorTo(x, y);
                NativeInput.key(272, true);
                NativeInput.key(272, false);
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
