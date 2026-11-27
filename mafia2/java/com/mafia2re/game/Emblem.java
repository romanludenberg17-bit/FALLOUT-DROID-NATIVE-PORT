package com.mafia2re.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Art-deco monogram: a cut-corner gold frame around a serif "II". */
final class Emblem extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private LinearGradient plate, gold;

    Emblem(Context c) {
        super(c);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float s = Math.min(w, h), t = (h - s) / 2;
        plate = new LinearGradient(0, t, 0, t + s, 0xFF2A2119, 0xFF120E0A, Shader.TileMode.CLAMP);
        gold = new LinearGradient(0, t, 0, t + s, 0xFFF4CF7E, 0xFFA86F1E, Shader.TileMode.CLAMP);
    }

    static Path frame(float l, float t, float r, float b, float cut) {
        Path f = new Path();
        f.moveTo(l + cut, t);
        f.lineTo(r - cut, t);
        f.lineTo(r, t + cut);
        f.lineTo(r, b - cut);
        f.lineTo(r - cut, b);
        f.lineTo(l + cut, b);
        f.lineTo(l, b - cut);
        f.lineTo(l, t + cut);
        f.close();
        return f;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        float l = (w - s) / 2, t = (h - s) / 2;
        float k = s / 100f;
        p.setStyle(Paint.Style.FILL);
        if (plate == null) onSizeChanged(getWidth(), getHeight(), 0, 0);
        p.setShader(plate);
        c.drawPath(frame(l + 2 * k, t + 2 * k, l + s - 2 * k, t + s - 2 * k, 18 * k), p);
        p.setShader(gold);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3.2f * k);
        c.drawPath(frame(l + 3 * k, t + 3 * k, l + s - 3 * k, t + s - 3 * k, 18 * k), p);
        p.setStrokeWidth(1.2f * k);
        c.drawPath(frame(l + 10 * k, t + 10 * k, l + s - 10 * k, t + s - 10 * k, 14 * k), p);
        // Deco rays above and below the numeral.
        p.setStrokeWidth(1.6f * k);
        for (int i = -2; i <= 2; i++) {
            c.drawLine(l + 50 * k + i * 9 * k, t + 17 * k, l + 50 * k + i * 6 * k, t + 25 * k, p);
            c.drawLine(l + 50 * k + i * 9 * k, t + 83 * k, l + 50 * k + i * 6 * k, t + 75 * k, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Ui.serif(getContext()));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(52 * k);
        c.drawText("II", l + 50 * k, t + 68 * k, p);
        p.setShader(null);
    }
}
