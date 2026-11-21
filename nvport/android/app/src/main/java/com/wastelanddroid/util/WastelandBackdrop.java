package com.wastelanddroid.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public final class WastelandBackdrop extends View {
    private Bitmap image;
    private final Paint p;
    private long seed;
    private float zoom;

    public WastelandBackdrop(Context context) {
        super(context);
        this.p = new Paint(1);
        this.zoom = 1.0f;
        this.seed = 1977L;
        this.image = load(context);
    }

    public static File customFile(Context context) {
        return new File(context.getFilesDir(), "splash_bg");
    }

    /** The user's background (Settings > Look) if set, else the bundled splash.jpg. */
    private static Bitmap load(Context context) {
        File custom = customFile(context);
        if (custom.exists()) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(custom.getPath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(custom.getPath(), opts);
            if (b != null) return b;
        }
        try (InputStream in = context.getAssets().open("splash.jpg")) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    public void setZoom(float f) {
        this.zoom = f;
        invalidate();
    }

    public float getZoom() {
        return this.zoom;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        canvas.save();
        float f = this.zoom;
        float f2 = width;
        float f3 = height;
        canvas.scale(f, f, 0.62f * f2, 0.55f * f3);
        if (this.image != null) {
            float fMax = Math.max(f2 / this.image.getWidth(), f3 / this.image.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(fMax, fMax);
            matrix.postTranslate((f2 - (this.image.getWidth() * fMax)) / 2.0f, (f3 - (this.image.getHeight() * fMax)) / 2.0f);
            this.p.setShader(null);
            canvas.drawBitmap(this.image, matrix, this.p);
        } else {
            drawProcedural(canvas, width, height);
        }
        canvas.restore();
        this.p.setShader(new LinearGradient(0.0f, 0.0f, 0.0f, f3, Color.argb(40, 0, 0, 0), Color.argb(170, 0, 0, 0), Shader.TileMode.CLAMP));
        canvas.drawRect(0.0f, 0.0f, f2, f3, this.p);
        this.p.setShader(new RadialGradient(f2 / 2.0f, f3 / 2.0f, Math.max(width, height) * 0.75f, new int[]{0, Color.argb(150, 0, 0, 0)}, new float[]{0.55f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0.0f, 0.0f, f2, f3, this.p);
        this.p.setShader(null);
    }

    private void drawProcedural(Canvas canvas, int i, int i2) {
        float f = i2;
        float f2 = f * 0.68f;
        this.p.setShader(new LinearGradient(0.0f, 0.0f, 0.0f, f2, new int[]{Color.rgb(28, 32, 44), Color.rgb(92, 70, 72), Color.rgb(214, 132, 70), Color.rgb(250, 196, 120)}, new float[]{0.0f, 0.45f, 0.82f, 1.0f}, Shader.TileMode.CLAMP));
        float f3 = i;
        canvas.drawRect(0.0f, 0.0f, f3, f2, this.p);
        this.p.setShader(new RadialGradient(f3 * 0.82f, f2 - (f * 0.05f), f * 0.55f, new int[]{Color.argb(255, 255, 236, 190), Color.argb(120, 255, 170, 90), 0}, new float[]{0.0f, 0.18f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0.0f, 0.0f, f3, f2, this.p);
        this.p.setShader(null);
        Random random = new Random(this.seed);
        for (int i3 = 0; i3 < 26; i3++) {
            float fNextFloat = random.nextFloat() * f3;
            float fNextFloat2 = random.nextFloat() * f2 * 0.6f;
            float fNextFloat3 = ((random.nextFloat() * 0.18f) + 0.08f) * f3;
            float fNextFloat4 = ((random.nextFloat() * 0.04f) + 0.015f) * f;
            this.p.setColor(Color.argb(random.nextInt(60) + 40, random.nextInt(30) + 30, random.nextInt(20) + 28, random.nextInt(20) + 40));
            canvas.drawOval(fNextFloat - fNextFloat3, fNextFloat2 - fNextFloat4, fNextFloat + fNextFloat3, fNextFloat2 + fNextFloat4, this.p);
        }
        this.p.setColor(Color.argb(150, 70, 60, 70));
        Path path = new Path();
        path.moveTo(0.0f, f2);
        float f4 = f3 * 0.3f;
        path.lineTo(f4, f2);
        float fNextFloat5 = f4;
        while (fNextFloat5 < 0.78f * f3) {
            float fNextFloat6 = ((random.nextFloat() * 0.02f) + 0.008f) * f3;
            float fNextFloat7 = f2 - (((random.nextFloat() * 0.12f) + 0.03f) * f);
            path.lineTo(fNextFloat5, fNextFloat7);
            float f5 = fNextFloat5 + fNextFloat6;
            path.lineTo(f5, fNextFloat7);
            path.lineTo(f5, f2);
            fNextFloat5 = f5 + (random.nextFloat() * f3 * 0.006f);
        }
        path.lineTo(f3, f2);
        path.close();
        canvas.drawPath(path, this.p);
        this.p.setColor(Color.argb(170, 60, 52, 62));
        float f6 = 0.56f * f3;
        float f7 = 0.003f * f3;
        canvas.drawRect(f6 - f7, f2 - (0.26f * f), f6 + f7, f2, this.p);
        float f8 = 0.022f * f3;
        canvas.drawOval(f6 - f8, f2 - (f * 0.27f), f6 + f8, f2 - (0.235f * f), this.p);
        this.p.setShader(new LinearGradient(0.0f, f2, 0.0f, f, new int[]{Color.rgb(120, 92, 70), Color.rgb(54, 42, 34), Color.rgb(22, 18, 16)}, (float[]) null, Shader.TileMode.CLAMP));
        canvas.drawRect(0.0f, f2, f3, f, this.p);
        this.p.setShader(null);
        this.p.setColor(Color.argb(235, 26, 22, 22));
        float f9 = f2 - (0.2f * f);
        float f10 = f2 - (0.17f * f);
        canvas.drawRect(0.0f, f9, f4, f10, this.p);
        float f11 = f2 + (0.04f * f);
        canvas.drawRect(f3 * 0.04f, f10, f3 * 0.05f, f11, this.p);
        canvas.drawRect(f3 * 0.24f, f10, f3 * 0.25f, f11, this.p);
        canvas.drawRect(f3 * 0.12f, f2 - (0.07f * f), f3 * 0.15f, f11, this.p);
        canvas.drawRect(f3 * 0.27f, f2 - (0.42f * f), f3 * 0.285f, f9, this.p);
        this.p.setColor(Color.argb(235, 40, 34, 30));
        canvas.drawRect(f3 * 0.06f, f2 - (0.54f * f), f3 * 0.31f, f2 - (f * 0.4f), this.p);
    }
}
