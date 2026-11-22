package com.wastelanddroid.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * The CRT look of Fallout 3's main menu, drawn over the game's picture: phosphor
 * scanlines, a soft bright band rolling down the tube (the "plasma" interference of the
 * original menu), a faint flicker, a green phosphor glow and the tube's vignette. It only
 * draws while shown and never takes a touch.
 */
public final class CrtOverlay extends View {
    private final Paint scan = new Paint();
    private final Paint band = new Paint();
    private final Paint glow = new Paint();
    private final Paint vignette = new Paint();
    private int builtW, builtH;
    private boolean on;

    public CrtOverlay(Context c) {
        super(c);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
        setVisibility(GONE);
        glow.setColor(0x0C1AFF80);
    }

    /** Shown or hidden (Fallout 3's main menu). */
    public void setOn(boolean shown) {
        if (shown == on) return;
        on = shown;
        setVisibility(shown ? VISIBLE : GONE);
        if (shown) postInvalidateOnAnimation();
    }

    private void build(int w, int h) {
        builtW = w;
        builtH = h;
        float d = getResources().getDisplayMetrics().density;
        // One scanline every ~3 dp: a dark line over a clear one, tiled down the screen.
        int period = Math.max(3, Math.round(3 * d));
        Bitmap tile = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < period; y++) tile.setPixel(0, y, y < period / 2 ? 0x38000000 : 0x00000000);
        scan.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        float r = (float) Math.hypot(w, h) * 0.62f;
        vignette.setShader(new RadialGradient(w / 2f, h / 2f, r, new int[]{0x00000000, 0x00000000, 0x9A000000},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!on) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (w != builtW || h != builtH) build(w, h);
        long t = SystemClock.uptimeMillis();
        // Phosphor glow, flickering by a few percent.
        float flicker = 0.85f + 0.15f * (float) Math.abs(Math.sin(t / 47.0) * Math.sin(t / 131.0));
        glow.setAlpha(Math.round(14 * flicker));
        canvas.drawRect(0, 0, w, h, glow);
        // The rolling band: 7 s from top to bottom, a tenth of the screen tall.
        float bandH = h * 0.14f;
        float y = ((t % 7000) / 7000f) * (h + bandH) - bandH;
        band.setShader(new LinearGradient(0, y, 0, y + bandH, new int[]{0x00FFFFFF, 0x16C8FFD8, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, y, w, y + bandH, band);
        canvas.drawRect(0, 0, w, h, scan);
        canvas.drawRect(0, 0, w, h, vignette);
        postInvalidateOnAnimation();
    }
}
