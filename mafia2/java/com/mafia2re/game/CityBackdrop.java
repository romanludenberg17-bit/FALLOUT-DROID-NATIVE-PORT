package com.mafia2re.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Launcher backdrop, drawn from code (no game art): a rainy 1950s skyline in warm monochrome,
 * a suspension bridge, a street lamp and a man in a fedora. The still picture is rendered
 * once into a bitmap; only the rain is animated, at about 30 FPS.
 */
final class CityBackdrop extends View {
    private Bitmap still;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stillPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private float[] drops;
    private long lastFrame;
    private boolean animate = true;

    CityBackdrop(Context c) {
        super(c);
        rain.setStrokeCap(Paint.Cap.ROUND);
    }

    void setAnimated(boolean a) {
        animate = a;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        if (still != null) still.recycle();
        // Half resolution is plenty for a soft, foggy picture and halves the memory.
        int bw = Math.max(1, w / 2), bh = Math.max(1, h / 2);
        still = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        paintCity(new Canvas(still), bw, bh);
        Random r = new Random(7);
        drops = new float[160 * 3];
        for (int i = 0; i < drops.length; i += 3) {
            drops[i] = r.nextFloat() * w * 1.2f;
            drops[i + 1] = r.nextFloat() * h;
            drops[i + 2] = 0.5f + r.nextFloat();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (still != null) {
            c.save();
            c.scale(w / (float) still.getWidth(), h / (float) still.getHeight());
            c.drawBitmap(still, 0, 0, stillPaint);
            c.restore();
        }
        if (drops == null) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < drops.length; i += 3) {
            float sp = drops[i + 2];
            drops[i + 1] += dt * h * 1.35f * sp;
            drops[i] -= dt * h * 0.22f * sp;
            if (drops[i + 1] > h) {
                drops[i + 1] -= h + 20 * d;
                drops[i] = (drops[i] + w * 0.37f) % (w * 1.2f);
            }
            float len = 14 * d * sp;
            rain.setStrokeWidth(d * (0.6f + 0.5f * sp));
            rain.setColor(Ui.alpha(0xFFD8CDBA, 0.10f + 0.10f * sp));
            c.drawLine(drops[i], drops[i + 1], drops[i] + len * 0.16f, drops[i + 1] - len, rain);
        }
        if (animate) postInvalidateDelayed(33);
    }

    // ------------------------------------------------------------------ the still picture

    private void paintCity(Canvas c, int w, int h) {
        Random r = new Random(1945);
        float horizon = h * 0.72f;

        // Overcast dusk sky, warm grey.
        paint.setShader(new LinearGradient(0, 0, 0, horizon, new int[]{0xFF1E1B18, 0xFF4A443C, 0xFF877D70}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        // Clouds: soft light blobs.
        for (int i = 0; i < 14; i++) {
            float cx = r.nextFloat() * w, cy = h * (0.05f + r.nextFloat() * 0.35f), rad = w * (0.08f + r.nextFloat() * 0.16f);
            paint.setShader(new RadialGradient(cx, cy, rad, Ui.alpha(0xFFB9AE9C, 0.18f), 0, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, paint);
        }
        paint.setShader(null);

        // Far skyline in the fog.
        skyline(c, r, w, horizon, h * 0.18f, h * 0.38f, 0xFF7B7266, 0.0f, w * 0.018f);
        // The landmark: a stepped art-deco tower with a spire.
        landmark(c, w * 0.64f, horizon, h * 0.62f, w * 0.07f, 0xFF4E473F);
        // Mid skyline with lit windows.
        skyline(c, r, w, horizon + h * 0.02f, h * 0.10f, h * 0.30f, 0xFF35302A, 0.35f, w * 0.025f);
        // Bridge on the left.
        bridge(c, w, h, horizon);
        // Street level haze.
        paint.setShader(new LinearGradient(0, horizon - h * 0.08f, 0, h, new int[]{0x00000000, 0xCC17130F, 0xFF0D0B09}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon - h * 0.08f, w, h, paint);
        paint.setShader(null);
        // Wet street reflections of the windows.
        for (int i = 0; i < 70; i++) {
            float x = r.nextFloat() * w, y = horizon + h * 0.05f + r.nextFloat() * h * 0.2f;
            paint.setColor(Ui.alpha(0xFFE0A63E, 0.05f + r.nextFloat() * 0.08f));
            c.drawRect(x, y, x + w * 0.002f, y + h * (0.02f + r.nextFloat() * 0.05f), paint);
        }
        // Street lamp and the man under it, right side.
        lampAndMan(c, w, h);

        // Readability: darken the left (cards) and the bottom (tab bar), vignette.
        paint.setShader(new LinearGradient(0, 0, w * 0.62f, 0, new int[]{0xE60D0B09, 0x990D0B09, 0x000D0B09}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * 0.55f, h * 0.45f, Math.max(w, h) * 0.75f, 0x00000000, 0xAA000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        // Film grain.
        for (int i = 0; i < w * h / 90; i++) {
            paint.setColor(r.nextBoolean() ? 0x0CFFFFFF : 0x10000000);
            c.drawPoint(r.nextFloat() * w, r.nextFloat() * h, paint);
        }
    }

    private void skyline(Canvas c, Random r, int w, float base, float minH, float maxH, int color, float windows, float unit) {
        float x = -unit * 2;
        while (x < w + unit) {
            float bw = unit * (1.5f + r.nextFloat() * 3.5f);
            float bh = minH + r.nextFloat() * (maxH - minH);
            paint.setColor(color);
            float top = base - bh;
            c.drawRect(x, top, x + bw, base, paint);
            // Art-deco setbacks.
            int steps = r.nextInt(3);
            float sx = x, sw = bw, sy = top;
            for (int s = 0; s < steps; s++) {
                sx += sw * 0.15f;
                sw *= 0.7f;
                float sh = bh * (0.06f + r.nextFloat() * 0.08f);
                c.drawRect(sx, sy - sh, sx + sw, sy, paint);
                sy -= sh;
            }
            if (r.nextFloat() < 0.25f) {
                c.drawRect(sx + sw * 0.45f, sy - bh * 0.12f, sx + sw * 0.55f, sy, paint);
            }
            if (windows > 0) {
                float ww = unit * 0.16f, wh = unit * 0.22f;
                for (float wy = top + wh * 2; wy < base - wh; wy += wh * 2.2f) {
                    for (float wx = x + ww * 1.5f; wx < x + bw - ww * 1.5f; wx += ww * 2.4f) {
                        if (r.nextFloat() < windows * 0.45f) {
                            paint.setColor(Ui.alpha(r.nextFloat() < 0.8f ? 0xFFE7B45A : 0xFFF2E6C8, 0.35f + r.nextFloat() * 0.5f));
                            c.drawRect(wx, wy, wx + ww, wy + wh, paint);
                        }
                    }
                }
            }
            x += bw + unit * r.nextFloat() * 0.6f;
        }
    }

    private void landmark(Canvas c, float cx, float base, float height, float width, int color) {
        paint.setColor(color);
        float y = base, wdt = width;
        float[] tiers = {0.42f, 0.18f, 0.12f, 0.08f, 0.06f};
        for (float t : tiers) {
            float th = height * t;
            c.drawRect(cx - wdt / 2, y - th, cx + wdt / 2, y, paint);
            y -= th;
            wdt *= 0.72f;
        }
        Path spire = new Path();
        spire.moveTo(cx - wdt * 0.25f, y);
        spire.lineTo(cx, y - height * 0.16f);
        spire.lineTo(cx + wdt * 0.25f, y);
        spire.close();
        c.drawPath(spire, paint);
        // A few lit windows on the tower's face.
        Random r = new Random(11);
        for (int i = 0; i < 40; i++) {
            float wx = cx - width * 0.4f + r.nextFloat() * width * 0.8f;
            float wy = base - height * (0.05f + r.nextFloat() * 0.35f);
            paint.setColor(Ui.alpha(0xFFE7B45A, 0.3f + r.nextFloat() * 0.4f));
            c.drawRect(wx, wy, wx + width * 0.025f, wy + width * 0.04f, paint);
        }
    }

    private void bridge(Canvas c, int w, int h, float horizon) {
        int col = 0xFF24201C;
        paint.setColor(col);
        float deck = horizon + h * 0.04f;
        float t1 = w * 0.08f, t2 = w * 0.34f, top = horizon - h * 0.30f;
        // Deck.
        c.drawRect(-w * 0.05f, deck, w * 0.48f, deck + h * 0.018f, paint);
        // Two gothic towers.
        for (float tx : new float[]{t1, t2}) {
            float tw = w * 0.032f;
            c.drawRect(tx - tw / 2, top, tx - tw * 0.18f, deck + h * 0.12f, paint);
            c.drawRect(tx + tw * 0.18f, top, tx + tw / 2, deck + h * 0.12f, paint);
            c.drawRect(tx - tw / 2, top, tx + tw / 2, top + h * 0.03f, paint);
            c.drawRect(tx - tw / 2, top + h * 0.14f, tx + tw / 2, top + h * 0.16f, paint);
        }
        // Main cables and suspenders.
        Paint cable = new Paint(Paint.ANTI_ALIAS_FLAG);
        cable.setStyle(Paint.Style.STROKE);
        cable.setColor(col);
        cable.setStrokeWidth(Math.max(1, w * 0.0018f));
        Path p = new Path();
        p.moveTo(-w * 0.05f, deck - h * 0.02f);
        p.quadTo((-w * 0.05f + t1) / 2, deck - h * 0.02f, t1, top + h * 0.01f);
        p.quadTo((t1 + t2) / 2, deck + h * 0.02f, t2, top + h * 0.01f);
        p.quadTo(t2 + w * 0.08f, deck - h * 0.04f, w * 0.48f, deck - h * 0.01f);
        c.drawPath(p, cable);
        cable.setStrokeWidth(Math.max(1, w * 0.0007f));
        for (float x = t1 + w * 0.012f; x < t2 - w * 0.01f; x += w * 0.012f) {
            float u = (x - t1) / (t2 - t1);
            float y = (top + h * 0.01f) * (1 - u) * (1 - u) + 2 * u * (1 - u) * (deck + h * 0.02f) + u * u * (top + h * 0.01f);
            c.drawLine(x, y, x, deck, cable);
        }
    }

    private void lampAndMan(Canvas c, int w, int h) {
        float lx = w * 0.79f, ground = h * 0.97f, lampTop = h * 0.30f;
        // Glow.
        paint.setShader(new RadialGradient(lx, lampTop + h * 0.03f, h * 0.45f, new int[]{0x66F2C46B, 0x22E0A63E, 0x00000000}, new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(lx, lampTop + h * 0.03f, h * 0.45f, paint);
        // Light cone on the wet street.
        Path cone = new Path();
        cone.moveTo(lx - w * 0.012f, lampTop + h * 0.04f);
        cone.lineTo(lx - w * 0.16f, ground);
        cone.lineTo(lx + w * 0.16f, ground);
        cone.lineTo(lx + w * 0.012f, lampTop + h * 0.04f);
        cone.close();
        paint.setShader(new LinearGradient(0, lampTop, 0, ground, 0x33F2C46B, 0x08F2C46B, Shader.TileMode.CLAMP));
        c.drawPath(cone, paint);
        paint.setShader(null);
        // Post and lantern.
        paint.setColor(0xFF0B0A09);
        float pw = w * 0.006f;
        c.drawRect(lx - pw / 2, lampTop, lx + pw / 2, ground, paint);
        c.drawRect(lx - pw * 1.4f, ground - h * 0.06f, lx + pw * 1.4f, ground, paint);
        Path lantern = new Path();
        lantern.moveTo(lx - w * 0.014f, lampTop);
        lantern.lineTo(lx + w * 0.014f, lampTop);
        lantern.lineTo(lx + w * 0.009f, lampTop + h * 0.045f);
        lantern.lineTo(lx - w * 0.009f, lampTop + h * 0.045f);
        lantern.close();
        c.drawPath(lantern, paint);
        paint.setColor(0xFFF6D58E);
        c.drawRect(lx - w * 0.007f, lampTop + h * 0.008f, lx + w * 0.007f, lampTop + h * 0.04f, paint);
        paint.setColor(0xFF0B0A09);
        c.drawRect(lx - w * 0.017f, lampTop - h * 0.008f, lx + w * 0.017f, lampTop, paint);

        // The man: long coat, fedora, standing a little left of the lamp, rim-lit by it.
        float mx = w * 0.885f, mh = h * 0.56f, top = ground - mh;
        Path man = new Path();
        float s = mh;
        // Hat brim and crown.
        man.moveTo(mx - s * 0.13f, top + s * 0.085f);
        man.cubicTo(mx - s * 0.06f, top + s * 0.065f, mx + s * 0.06f, top + s * 0.065f, mx + s * 0.13f, top + s * 0.08f);
        man.lineTo(mx + s * 0.075f, top + s * 0.06f);
        man.cubicTo(mx + s * 0.075f, top + s * 0.01f, mx + s * 0.04f, top, mx, top + s * 0.012f);
        man.cubicTo(mx - s * 0.04f, top, mx - s * 0.075f, top + s * 0.01f, mx - s * 0.075f, top + s * 0.06f);
        man.close();
        // Head and neck.
        man.addOval(new RectF(mx - s * 0.052f, top + s * 0.06f, mx + s * 0.052f, top + s * 0.17f), Path.Direction.CW);
        // Coat with raised collar, shoulders, flaring to the knees.
        Path coat = new Path();
        coat.moveTo(mx - s * 0.04f, top + s * 0.15f);
        coat.lineTo(mx - s * 0.075f, top + s * 0.19f);
        coat.cubicTo(mx - s * 0.15f, top + s * 0.2f, mx - s * 0.17f, top + s * 0.24f, mx - s * 0.175f, top + s * 0.32f);
        coat.lineTo(mx - s * 0.2f, top + s * 0.78f);
        coat.lineTo(mx + s * 0.19f, top + s * 0.78f);
        coat.lineTo(mx + s * 0.17f, top + s * 0.32f);
        coat.cubicTo(mx + s * 0.165f, top + s * 0.24f, mx + s * 0.145f, top + s * 0.2f, mx + s * 0.075f, top + s * 0.19f);
        coat.lineTo(mx + s * 0.04f, top + s * 0.15f);
        coat.close();
        // Legs.
        coat.addRect(mx - s * 0.11f, top + s * 0.76f, mx - s * 0.03f, ground, Path.Direction.CW);
        coat.addRect(mx + s * 0.02f, top + s * 0.76f, mx + s * 0.1f, ground, Path.Direction.CW);
        man.addPath(coat);
        // Rim light from the lamp (left side), then the black figure over it.
        paint.setColor(Ui.alpha(0xFFF2C46B, 0.55f));
        c.save();
        c.translate(-s * 0.006f, 0);
        c.drawPath(man, paint);
        c.restore();
        paint.setColor(0xFF080706);
        c.drawPath(man, paint);
        // Shadow on the street.
        paint.setColor(0x66000000);
        c.drawOval(new RectF(mx - s * 0.25f, ground - s * 0.015f, mx + s * 0.35f, ground + s * 0.02f), paint);
        // Cigarette ember.
        paint.setShader(new RadialGradient(mx - s * 0.06f, top + s * 0.14f, s * 0.02f, 0xFFFF9A3C, 0x00FF9A3C, Shader.TileMode.CLAMP));
        c.drawCircle(mx - s * 0.06f, top + s * 0.14f, s * 0.02f, paint);
        paint.setShader(null);
    }
}
