package com.wastelanddroid.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public final class ScanlineView extends View {
    private final Paint line;
    private final Paint vignette;

    public ScanlineView(Context context) {
        super(context);
        Paint paint = new Paint();
        this.line = paint;
        this.vignette = new Paint();
        paint.setColor(Color.argb(18, 0, 0, 0));
        setClickable(false);
        setFocusable(false);
    }

    @Override // android.view.View
    protected void onSizeChanged(int i, int i2, int i3, int i4) {
        this.vignette.setShader(new RadialGradient(i / 2.0f, i2 / 2.0f, Math.max(i, i2) * 0.75f, new int[]{0, 0, Color.argb(120, 0, 0, 0)}, new float[]{0.0f, 0.6f, 1.0f}, Shader.TileMode.CLAMP));
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        int iMax = Math.max(3, Math.round(getResources().getDisplayMetrics().density * 2.0f));
        for (int i = 0; i < getHeight(); i += iMax) {
            float f = i;
            canvas.drawRect(0.0f, f, getWidth(), f + (iMax / 2.0f), this.line);
        }
        canvas.drawRect(0.0f, 0.0f, getWidth(), getHeight(), this.vignette);
    }
}
