package com.wastelanddroid.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class CoverView extends View {
    private int accent;
    private Bitmap bitmap;
    private final Path clip;
    private int dark;
    private final Paint paint;
    private final RectF rect;
    private boolean selected;
    private String tagline;
    private final Paint text;
    private String title;

    public CoverView(Context context) {
        super(context);
        this.paint = new Paint(1);
        Paint paint = new Paint(1);
        this.text = paint;
        this.clip = new Path();
        this.rect = new RectF();
        this.title = "";
        this.tagline = "";
        this.accent = Ui.FG;
        this.dark = Ui.BG;
        paint.setTypeface(Ui.mono(true));
        paint.setColor(-1);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    public void set(String str, int i, int i2, File file) throws IOException {
        this.title = str;
        this.accent = i;
        this.dark = i2;
        this.bitmap = null;
        if (file != null) {
            if (!file.exists()) {
                InputStream inputStreamOpen = getContext().getAssets().open(file.getName());
                this.bitmap = BitmapFactory.decodeStream(inputStreamOpen);
                inputStreamOpen.close();
            } else {
                this.bitmap = BitmapFactory.decodeFile(file.getPath(), null);
            }
        }
        invalidate();
    }

    public void setTagline(String str) {
        if (str == null) {
            str = "";
        }
        this.tagline = str;
        invalidate();
    }

    public void setSelectedCard(boolean z) {
        this.selected = z;
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        int i;
        float f;
        float f2;
        float f3;
        float f4;
        Shader shader;
        int width = getWidth();
        int height = getHeight();
        float fDp = Ui.dp(getContext(), 14.0f);
        float f5 = width;
        float f6 = height;
        this.rect.set(0.0f, 0.0f, f5, f6);
        this.clip.reset();
        this.clip.addRoundRect(this.rect, fDp, fDp, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(this.clip);
        this.paint.setShader(null);
        if (this.bitmap != null) {
            float fMax = Math.max(f5 / this.bitmap.getWidth(), f6 / this.bitmap.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(fMax, fMax);
            matrix.postTranslate((f5 - (this.bitmap.getWidth() * fMax)) / 2.0f, (f6 - (this.bitmap.getHeight() * fMax)) / 4.0f);
            BitmapShader bitmapShader = new BitmapShader(this.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            bitmapShader.setLocalMatrix(matrix);
            this.paint.setShader(bitmapShader);
            canvas.drawRect(this.rect, this.paint);
            f = f6;
            f2 = f5;
            f3 = fDp;
            i = 255;
            shader = null;
            f4 = 0.0f;
        } else {
            Shader shader2 = null;
            this.paint.setShader(new LinearGradient(0.0f, 0.0f, f5, f6, this.accent, this.dark, Shader.TileMode.CLAMP));
            canvas.drawRect(this.rect, this.paint);
            this.paint.setShader(null);
            Paint paint = this.paint;
            i = 255;
            paint.setColor(Color.argb(28, 255, 255, 255));
            float fDp2 = Ui.dp(getContext(), 14.0f);
            this.paint.setStrokeWidth(Ui.dp(getContext(), 3.0f));
            float f7 = -height;
            while (f7 < f5) {
                canvas.drawLine(f7, f6, f7 + f6, 0.0f, this.paint);
                f7 += fDp2;
                shader2 = shader2;
                fDp = fDp;
                f6 = f6;
                f5 = f5;
            }
            f = f6;
            f2 = f5;
            f3 = fDp;
            f4 = 0.0f;
            shader = shader2;
        }
        int i2 = i;
        float f8 = f4;
        this.paint.setShader(new LinearGradient(0.0f, f * 0.45f, 0.0f, f, 0, Color.argb(230, 0, 0, 0), Shader.TileMode.CLAMP));
        canvas.drawRect(this.rect, this.paint);
        this.paint.setShader(shader);
        float fDp3 = Ui.dp(getContext(), 16.0f);
        this.text.setShadowLayer(10.0f, f8, 2.0f, -16777216);
        if (this.bitmap == null) {
            String strTrim = this.title.contains("New Vegas") ? "NEW VEGAS" : this.title.replace("Fallout", "").trim();
            this.text.setColor(Color.argb(40, i2, i2, i2));
            this.text.setTextSize((strTrim.length() <= 2 ? 0.9f : 0.22f) * f);
            this.text.setLetterSpacing(0.04f);
            canvas.drawText(strTrim, 0.6f * fDp3, (strTrim.length() <= 2 ? 0.78f : 0.42f) * f, this.text);
            this.text.setLetterSpacing(f8);
        }
        this.text.setColor(-1);
        this.text.setTextSize(Math.min(0.075f * f2, 0.11f * f));
        float textSize = f - fDp3;
        if (!this.tagline.isEmpty()) {
            textSize -= this.text.getTextSize() * 0.55f;
        }
        canvas.drawText(this.title.toUpperCase(), fDp3, textSize, this.text);
        if (!this.tagline.isEmpty()) {
            this.text.setColor(Color.argb(200, 230, 230, 220));
            this.text.setTextSize(Math.min(0.034f * f2, f * 0.05f));
            this.text.setLetterSpacing(0.2f);
            canvas.drawText(this.tagline.toUpperCase(), fDp3, f - (0.7f * fDp3), this.text);
            this.text.setLetterSpacing(f8);
        }
        canvas.restore();
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(Ui.dp(getContext(), this.selected ? 2.5f : 1.0f));
        this.paint.setColor(this.selected ? Ui.AMBER : Ui.LINE);
        this.rect.inset(this.paint.getStrokeWidth() / 2.0f, this.paint.getStrokeWidth() / 2.0f);
        float f9 = f3;
        canvas.drawRoundRect(this.rect, f9, f9, this.paint);
        this.paint.setStyle(Paint.Style.FILL);
    }
}
