package com.wastelanddroid.controls;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.Set;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: FalloutIcons.kt */
public final class FalloutIcons {
    private static final int BTN_LEFT = 272;
    private static final int BTN_RIGHT = 273;
    private static final int CAPS = 58;
    private static final int E = 18;
    private static final int ESC = 1;
    private static final int F = 33;
    private static final int F9 = 67;
    private static final int LCTRL = 29;
    private static final int R = 19;
    private static final int SPACE = 57;
    private static final int T = 20;
    private static final int TAB = 15;
    private static final int V = 47;
    public static final FalloutIcons INSTANCE = new FalloutIcons();
    private static final int F5 = 63;
    private static final Set<Integer> SET = new java.util.HashSet<>(java.util.Arrays.asList(new Integer[]{272, 273, 18, 19, 47, 29, 58, 57, 15, 33, 1, Integer.valueOf(F5), 67, 20}));
    private static final Path path = new Path();
    private static final RectF rect = new RectF();
    public static final int $stable = 8;

    private FalloutIcons() {
    }

    public final boolean has(int code) {
        return SET.contains(Integer.valueOf(code));
    }

    public final boolean draw(Canvas canvas, int code, float cx, float cy, float box, Paint paint) {
        Paint paint2;
        Intrinsics.checkNotNullParameter(canvas, "canvas");
        Intrinsics.checkNotNullParameter(paint, "paint");
        if (!SET.contains(Integer.valueOf(code))) {
            return false;
        }
        float f = box / 2.0f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.09f * box);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path path2 = path;
        path2.reset();
        if (code == 1) {
            paint2 = paint;
            float f2 = 0.72f * f;
            float f3 = cx - f2;
            float f4 = f * 0.5f;
            float f5 = cy - f4;
            path2.moveTo(f3, f5);
            float f6 = f2 + cx;
            path2.lineTo(f6, f5);
            path2.moveTo(f3, cy);
            path2.lineTo(f6, cy);
            float f7 = cy + f4;
            path2.moveTo(f3, f7);
            path2.lineTo(f6, f7);
            canvas.drawPath(path2, paint2);
        } else if (code == 15) {
            paint2 = paint;
            RectF rectF = rect;
            float f8 = 0.68f * f;
            float f9 = 0.34f * f;
            float f10 = cy + f9;
            rectF.set(cx - f8, cy - (0.74f * f), f8 + cx, f10);
            float f11 = 0.16f * f;
            canvas.drawRoundRect(rectF, f11, f11, paint2);
            float f12 = cx - f9;
            float f13 = cy - f9;
            path2.moveTo(f12, f13);
            path2.lineTo(f9 + cx, f13);
            float f14 = cy - (0.04f * f);
            path2.moveTo(f12, f14);
            path2.lineTo(cx + (0.12f * f), f14);
            float f15 = 0.44f * f;
            float f16 = cx - f15;
            path2.moveTo(f16, f10);
            float f17 = cy + (f * 0.84f);
            path2.lineTo(f16, f17);
            float f18 = cx + f15;
            path2.moveTo(f18, f10);
            path2.lineTo(f18, f17);
            canvas.drawPath(path2, paint2);
        } else if (code == 29) {
            paint2 = paint;
            paint2.setStyle(Paint.Style.FILL);
            float f19 = cx - (0.1f * f);
            canvas.drawCircle(f19, cy - (0.64f * f), 0.2f * f, paint2);
            paint2.setStyle(Paint.Style.STROKE);
            path2.moveTo(f19, cy - (0.38f * f));
            float f20 = (0.16f * f) + cx;
            float f21 = cy + (0.06f * f);
            path2.lineTo(f20, f21);
            float f22 = cx - (0.22f * f);
            float f23 = 0.34f * f;
            float f24 = cy + f23;
            path2.lineTo(f22, f24);
            path2.lineTo(f22, (0.84f * f) + cy);
            canvas.drawPath(path2, paint2);
            path2.reset();
            path2.moveTo(f20, f21);
            path2.lineTo((0.56f * f) + cx, f24);
            path2.moveTo(f19, cy - (0.24f * f));
            path2.lineTo(cx + (f * 0.46f), cy - f23);
            canvas.drawPath(path2, paint2);
        } else if (code != 33) {
            if (code == 47) {
                canvas.drawCircle(cx, cy, 0.34f * f, paint);
                float f25 = 0.92f * f;
                float f26 = f * 0.46f;
                float f27 = cx - f25;
                float f28 = cy - f26;
                path2.moveTo(f27, f28);
                float f29 = cy - f25;
                path2.lineTo(f27, f29);
                float f30 = cx - f26;
                path2.lineTo(f30, f29);
                float f31 = cx + f26;
                path2.moveTo(f31, f29);
                float f32 = cx + f25;
                path2.lineTo(f32, f29);
                path2.lineTo(f32, f28);
                float f33 = cy + f26;
                path2.moveTo(f32, f33);
                float f34 = f25 + cy;
                path2.lineTo(f32, f34);
                path2.lineTo(f31, f34);
                path2.moveTo(f30, f34);
                path2.lineTo(f27, f34);
                path2.lineTo(f27, f33);
                canvas.drawPath(path2, paint);
            } else if (code == F5) {
                RectF rectF2 = rect;
                float f35 = 0.72f * f;
                float f36 = cx - f35;
                float f37 = cy - f35;
                float f38 = cx + f35;
                float f39 = f35 + cy;
                rectF2.set(f36, f37, f38, f39);
                float f40 = f * 0.14f;
                canvas.drawRoundRect(rectF2, f40, f40, paint);
                float f41 = 0.36f * f;
                rectF2.set(cx - f41, f37, f41 + cx, cy - (0.16f * f));
                canvas.drawRect(rectF2, paint);
                float f42 = f * 0.46f;
                rectF2.set(cx - f42, f40 + cy, cx + f42, f39);
                canvas.drawRect(rectF2, paint);
            } else if (code == 67) {
                float f43 = 0.8f * f;
                float f44 = cx - f43;
                float f45 = (0.66f * f) + cy;
                path2.moveTo(f44, f45);
                float f46 = cy - (0.44f * f);
                path2.lineTo(f44, f46);
                path2.lineTo(cx - (0.14f * f), f46);
                float f47 = cy - (0.18f * f);
                path2.lineTo((0.02f * f) + cx, f47);
                float f48 = f43 + cx;
                path2.lineTo(f48, f47);
                path2.lineTo(f48, f45);
                path2.close();
                canvas.drawPath(path2, paint);
                path2.reset();
                path2.moveTo(cx, cy + (0.52f * f));
                float f49 = cy + (0.04f * f);
                path2.lineTo(cx, f49);
                float f50 = 0.26f * f;
                float f51 = cy + (f * 0.28f);
                path2.moveTo(cx - f50, f51);
                path2.lineTo(cx, f49);
                path2.lineTo(cx + f50, f51);
                canvas.drawPath(path2, paint);
            } else if (code == 57) {
                float f52 = cy - (0.86f * f);
                path2.moveTo(cx, f52);
                path2.lineTo(cx, (0.2f * f) + cy);
                float f53 = 0.42f * f;
                float f54 = cy - f53;
                path2.moveTo(cx - f53, f54);
                path2.lineTo(cx, f52);
                path2.lineTo(cx + f53, f54);
                float f55 = 0.7f * f;
                float f56 = cy + (f * 0.66f);
                path2.moveTo(cx - f55, f56);
                path2.lineTo(cx + f55, f56);
                canvas.drawPath(path2, paint);
            } else if (code == 58) {
                float f57 = 0.7f * f;
                float f58 = cx - f57;
                float f59 = 0.52f * f;
                float f60 = cy - f59;
                path2.moveTo(f58, f60);
                float f61 = f * 0.12f;
                path2.lineTo(cx - f61, cy);
                float f62 = cy + f59;
                path2.lineTo(f58, f62);
                float f63 = cx + f61;
                path2.moveTo(f63, f60);
                path2.lineTo(cx + f57, cy);
                path2.lineTo(f63, f62);
                canvas.drawPath(path2, paint);
            } else if (code == 272) {
                float f64 = cx - (0.1f * f);
                float f65 = 0.46f * f;
                float f66 = cy - f65;
                path2.moveTo(f64, f66);
                float f67 = cx + (0.34f * f);
                float f68 = 0.3f * f;
                float f69 = cy - f68;
                float f70 = cy + f68;
                float f71 = cy + f65;
                path2.cubicTo(f67, f69, f67, f70, f64, f71);
                float f72 = cx - (0.42f * f);
                path2.lineTo(f72, f71);
                path2.lineTo(f72, f66);
                path2.close();
                canvas.drawPath(path2, paint);
                path2.reset();
                float f73 = cx - (0.92f * f);
                path2.moveTo(f73, f69);
                float f74 = cx - (0.62f * f);
                path2.lineTo(f74, f69);
                path2.moveTo(cx - (f * 0.98f), cy);
                path2.lineTo(f74, cy);
                path2.moveTo(f73, f70);
                path2.lineTo(f74, f70);
                canvas.drawPath(path2, paint);
            } else if (code == 273) {
                canvas.drawCircle(cx, cy, f * 0.62f, paint);
                float f75 = 0.98f * f;
                path2.moveTo(cx, cy - f75);
                float f76 = 0.7f * f;
                path2.lineTo(cx, cy - f76);
                path2.moveTo(cx, cy + f76);
                path2.lineTo(cx, cy + f75);
                path2.moveTo(cx - f75, cy);
                path2.lineTo(cx - f76, cy);
                path2.moveTo(cx + f76, cy);
                path2.lineTo(f75 + cx, cy);
                canvas.drawPath(path2, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, f * 0.14f, paint);
            } else {
                switch (code) {
                    case 18:
                        float f77 = 0.46f * f;
                        float f78 = cx - f77;
                        path2.moveTo(f78, cy + (0.18f * f));
                        path2.lineTo(f78, cy - (0.26f * f));
                        float f79 = cx - (0.16f * f);
                        float f80 = cy - (0.06f * f);
                        path2.moveTo(f79, f80);
                        path2.lineTo(f79, cy - (0.6f * f));
                        float f81 = cx + (0.14f * f);
                        path2.moveTo(f81, f80);
                        path2.lineTo(f81, cy - (0.52f * f));
                        canvas.drawPath(path2, paint);
                        path2.reset();
                        float f82 = cx - (0.62f * f);
                        path2.moveTo(f82, cy + (0.1f * f));
                        float f83 = cx + (0.44f * f);
                        path2.cubicTo(f82, cy + (0.86f * f), cx + f77, cy + (0.94f * f), f83, cy + (0.24f * f));
                        path2.lineTo(f83, cy - (f * 0.22f));
                        canvas.drawPath(path2, paint);
                        break;
                    case 19:
                        RectF rectF3 = rect;
                        float f84 = 0.62f * f;
                        rectF3.set(cx - f84, cy - f84, cx + f84, cy + f84);
                        canvas.drawArc(rectF3, -40.0f, 290.0f, false, paint);
                        float f85 = cx + (0.3f * f);
                        float f86 = 0.76f * f;
                        path2.moveTo(f85, cy - f86);
                        path2.lineTo(f86 + cx, cy - (0.44f * f));
                        path2.lineTo(f85, cy - (f * 0.12f));
                        canvas.drawPath(path2, paint);
                        break;
                    case 20:
                        canvas.drawCircle(cx, cy, 0.78f * f, paint);
                        path2.moveTo(cx, cy - (0.44f * f));
                        path2.lineTo(cx, cy + (0.06f * f));
                        path2.lineTo((0.36f * f) + cx, cy + (f * 0.3f));
                        canvas.drawPath(path2, paint);
                        break;
                }
            }
            paint2 = paint;
        } else {
            float f87 = 0.94f * f;
            float f88 = cx - f87;
            path2.moveTo(f88, cy);
            float f89 = 0.4f * f;
            float f90 = cx - f89;
            float f91 = f * 0.76f;
            float f92 = cy - f91;
            float f93 = f89 + cx;
            path2.cubicTo(f90, f92, f93, f92, cx + f87, cy);
            float f94 = cy + f91;
            path2.cubicTo(f93, f94, f90, f94, f88, cy);
            canvas.drawPath(path2, paint);
            canvas.drawCircle(cx, cy, f * 0.26f, paint);
            paint2 = paint;
        }
        paint2.setStyle(Paint.Style.FILL);
        return true;
    }
}
