package com.mafia2re.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A logo picture from assets/img, fitted and centred: the red "II" or the full ReMaf2 mark. */
final class Emblem extends View {
    static final String II = "img/logo_ii.png";
    static final String FULL = "img/logo_full.png";

    private final Bitmap bmp;
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    Emblem(Context c) {
        this(c, II);
    }

    Emblem(Context c, String asset) {
        super(c);
        bmp = ArtBackdrop.load(c, asset);
    }

    /** Width that keeps the picture's aspect ratio at the given height (for wrap layouts). */
    int widthFor(int height) {
        return bmp == null ? height : Math.round(height * bmp.getWidth() / (float) bmp.getHeight());
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        float w = getWidth(), h = getHeight();
        float k = Math.min(w / bmp.getWidth(), h / bmp.getHeight());
        float dw = bmp.getWidth() * k, dh = bmp.getHeight() * k;
        dst.set((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
        c.drawBitmap(bmp, null, dst, p);
    }
}
