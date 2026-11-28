package com.mafia2re.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Launcher backdrop: a picture from assets/img (cover-fit, anchored to the right so the
 * characters stay visible next to the cards), darkened on the left and bottom for
 * readability, a cross-fade when the picture changes, and light rain on top.
 */
final class ArtBackdrop extends View {
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private Bitmap cur, prev;
    private long fadeStart;
    private static final long FADE_MS = 450;
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final Paint rain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private Shader left, bottom, vignette;
    private float[] drops;
    private long lastFrame;
    private boolean animate = true;

    ArtBackdrop(Context c, String asset) {
        super(c);
        rain.setStrokeCap(Paint.Cap.ROUND);
        cur = load(c, asset);
    }

    static Bitmap load(Context c, String asset) {
        Bitmap b = CACHE.get(asset);
        if (b != null) return b;
        try (InputStream in = c.getAssets().open(asset)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;   // photos: half the memory, no visible loss
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            b = null;
        }
        if (b != null) CACHE.put(asset, b);
        return b;
    }

    void setImage(String asset) {
        Bitmap n = load(getContext(), asset);
        if (n == null || n == cur) return;
        prev = cur;
        cur = n;
        fadeStart = SystemClock.uptimeMillis();
        invalidate();
    }

    void setAnimated(boolean a) {
        animate = a;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        left = new LinearGradient(0, 0, w * 0.62f, 0, new int[]{0xF20A0909, 0xB30A0909, 0x000A0909}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
        bottom = new LinearGradient(0, h * 0.55f, 0, h, 0x000A0909, 0xE60A0909, Shader.TileMode.CLAMP);
        vignette = new RadialGradient(w * 0.62f, h * 0.42f, Math.max(w, h) * 0.8f, 0x00000000, 0x99000000, Shader.TileMode.CLAMP);
        Random r = new Random(7);
        drops = new float[110 * 3];
        for (int i = 0; i < drops.length; i += 3) {
            drops[i] = r.nextFloat() * w * 1.2f;
            drops[i + 1] = r.nextFloat() * h;
            drops[i + 2] = 0.5f + r.nextFloat();
        }
    }

    private void drawCover(Canvas c, Bitmap b, int alpha) {
        int w = getWidth(), h = getHeight();
        float k = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        float dw = b.getWidth() * k;
        m.setScale(k, k);
        m.postTranslate(w - dw, (h - b.getHeight() * k) * 0.35f);   // anchored right, slightly to the top
        img.setAlpha(alpha);
        c.drawBitmap(b, m, img);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Ui.BG);
        long now = SystemClock.uptimeMillis();
        float t = prev == null ? 1f : Math.min(1f, (now - fadeStart) / (float) FADE_MS);
        if (prev != null && t < 1f) drawCover(c, prev, 255);
        if (cur != null) drawCover(c, cur, Math.round(255 * t));
        if (t >= 1f) prev = null;
        shade.setShader(left);
        c.drawRect(0, 0, w, h, shade);
        shade.setShader(bottom);
        c.drawRect(0, 0, w, h, shade);
        shade.setShader(vignette);
        c.drawRect(0, 0, w, h, shade);
        shade.setShader(null);

        if (drops != null) {
            float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < drops.length; i += 3) {
                float sp = drops[i + 2];
                drops[i + 1] += dt * h * 1.35f * sp;
                drops[i] -= dt * h * 0.2f * sp;
                if (drops[i + 1] > h) {
                    drops[i + 1] -= h + 20 * d;
                    drops[i] = (drops[i] + w * 0.37f) % (w * 1.2f);
                }
                float len = 13 * d * sp;
                rain.setStrokeWidth(d * (0.6f + 0.5f * sp));
                rain.setColor(Ui.alpha(0xFFE8ECF2, 0.07f + 0.08f * sp));
                c.drawLine(drops[i], drops[i + 1], drops[i] + len * 0.15f, drops[i + 1] - len, rain);
            }
        }
        if (animate || prev != null) postInvalidateDelayed(33);
    }
}
