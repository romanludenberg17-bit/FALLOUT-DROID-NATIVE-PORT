package com.mafia2re.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Vector glyphs for the touch buttons. Everything is drawn in a [-1, 1] box around the button
 * centre with round-capped strokes, so icons stay crisp at any button size.
 */
final class Glyphs {
    private Glyphs() {
    }

    private static final Path P = new Path();
    private static final RectF R = new RectF();

    static void draw(Canvas c, String g, float cx, float cy, float size, int color, Paint p) {
        c.save();
        c.translate(cx, cy);
        c.scale(size, size);
        p.setShader(null);
        p.setColor(color);
        p.setStrokeWidth(0.13f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        switch (g) {
            case "fire": fire(c, p); break;
            case "aim": aim(c, p); break;
            case "jump": chevron(c, p, -1); break;
            case "crouch": chevron(c, p, 1); break;
            case "action": action(c, p); break;
            case "reload": reload(c, p); break;
            case "punch": punch(c, p); break;
            case "cover": cover(c, p); break;
            case "weapon": weapon(c, p); break;
            case "grenade": grenade(c, p); break;
            case "map": map(c, p); break;
            case "pause": pause(c, p); break;
            case "run": run(c, p); break;
            case "car_in": car(c, p, true); break;
            case "car_out": car(c, p, false); break;
            case "gas": pedal(c, p, true); break;
            case "brake": pedal(c, p, false); break;
            case "handbrake": handbrake(c, p); break;
            case "horn": horn(c, p); break;
            case "limiter": limiter(c, p); break;
            case "ok": ok(c, p); break;
            case "back": back(c, p); break;
            case "gear": gear(c, p); break;
            case "look": look(c, p); break;
            default: break;
        }
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private static void fire(Canvas c, Paint p) {
        // A pistol cartridge: lead bullet (filled), brass case (outline), rim.
        c.rotate(30);
        P.reset();
        P.moveTo(-0.24f, -0.12f);
        P.cubicTo(-0.24f, -0.48f, -0.12f, -0.74f, 0f, -0.82f);
        P.cubicTo(0.12f, -0.74f, 0.24f, -0.48f, 0.24f, -0.12f);
        P.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(P, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.11f);
        R.set(-0.24f, -0.04f, 0.24f, 0.62f);
        c.drawRect(R, p);
        c.drawLine(-0.33f, 0.74f, 0.33f, 0.74f, p);
        p.setStrokeWidth(0.07f);
        c.drawLine(-0.24f, 0.4f, 0.24f, 0.4f, p);
        c.rotate(-30);
    }

    private static void aim(Canvas c, Paint p) {
        c.drawCircle(0, 0, 0.55f, p);
        c.drawLine(0, -0.85f, 0, -0.3f, p);
        c.drawLine(0, 0.3f, 0, 0.85f, p);
        c.drawLine(-0.85f, 0, -0.3f, 0, p);
        c.drawLine(0.3f, 0, 0.85f, 0, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0, 0, 0.09f, p);
        p.setStyle(Paint.Style.STROKE);
    }

    private static void chevron(Canvas c, Paint p, int dir) {
        P.reset();
        P.moveTo(-0.55f, 0.18f * -dir + 0.05f * dir);
        P.lineTo(0, -0.38f * -dir);
        P.lineTo(0.55f, 0.18f * -dir + 0.05f * dir);
        c.drawPath(P, p);
        P.reset();
        P.moveTo(-0.55f, 0.18f * -dir + 0.05f * dir - 0.32f * dir);
        P.lineTo(0, -0.38f * -dir - 0.32f * dir);
        P.lineTo(0.55f, 0.18f * -dir + 0.05f * dir - 0.32f * dir);
        p.setAlpha(p.getAlpha() / 2);
        c.drawPath(P, p);
        p.setAlpha(Math.min(255, p.getAlpha() * 2));
        c.drawLine(-0.6f, 0.62f * dir, 0.6f, 0.62f * dir, p);
    }

    private static void action(Canvas c, Paint p) {
        // Hand pressing a point, with ripples.
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0, 0.05f, 0.16f, p);
        p.setStyle(Paint.Style.STROKE);
        R.set(-0.45f, -0.4f, 0.45f, 0.5f);
        c.drawArc(R, 200, 140, false, p);
        R.set(-0.75f, -0.7f, 0.75f, 0.8f);
        p.setAlpha(p.getAlpha() / 2);
        c.drawArc(R, 205, 130, false, p);
        p.setAlpha(Math.min(255, p.getAlpha() * 2));
        c.drawLine(0, 0.25f, 0, 0.8f, p);
    }

    private static void reload(Canvas c, Paint p) {
        R.set(-0.6f, -0.6f, 0.6f, 0.6f);
        c.drawArc(R, -60, 290, false, p);
        P.reset();
        P.moveTo(0.18f, -0.78f);
        P.lineTo(0.42f, -0.5f);
        P.lineTo(0.08f, -0.34f);
        c.drawPath(P, p);
    }

    private static void punch(Canvas c, Paint p) {
        R.set(-0.55f, -0.42f, 0.5f, 0.42f);
        c.drawRoundRect(R, 0.2f, 0.2f, p);
        for (float x = -0.3f; x <= 0.31f; x += 0.3f) c.drawLine(x, -0.42f, x, -0.12f, p);
        c.drawLine(-0.55f, 0.05f, -0.2f, 0.05f, p);
        c.drawLine(0.5f, -0.1f, 0.78f, -0.1f, p);
        c.drawLine(0.5f, 0.15f, 0.85f, 0.15f, p);
    }

    private static void cover(Canvas c, Paint p) {
        // Low wall and a head peeking over it.
        R.set(-0.75f, 0f, 0.55f, 0.65f);
        c.drawRect(R, p);
        c.drawLine(-0.75f, 0.32f, 0.55f, 0.32f, p);
        c.drawLine(-0.2f, 0f, -0.2f, 0.32f, p);
        c.drawLine(0.15f, 0.32f, 0.15f, 0.65f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0.55f, -0.28f, 0.2f, p);
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(0.55f, -0.05f, 0.75f, 0.2f, p);
    }

    private static void weapon(Canvas c, Paint p) {
        // A 1940s pistol, side view.
        P.reset();
        P.moveTo(-0.8f, -0.35f);
        P.lineTo(0.7f, -0.35f);
        P.lineTo(0.7f, -0.08f);
        P.lineTo(-0.05f, -0.08f);
        P.lineTo(-0.15f, 0.12f);
        P.lineTo(-0.35f, 0.12f);
        P.lineTo(-0.45f, 0.7f);
        P.lineTo(-0.78f, 0.7f);
        P.lineTo(-0.65f, -0.08f);
        P.lineTo(-0.8f, -0.08f);
        P.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(P, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.09f);
        R.set(-0.3f, -0.05f, 0.05f, 0.35f);
        c.drawArc(R, 0, 180, false, p);
    }

    private static void grenade(Canvas c, Paint p) {
        R.set(-0.45f, -0.35f, 0.45f, 0.75f);
        c.drawOval(R, p);
        p.setStrokeWidth(0.08f);
        c.drawLine(-0.43f, 0.2f, 0.43f, 0.2f, p);
        c.drawLine(0, -0.35f, 0, 0.75f, p);
        p.setStrokeWidth(0.13f);
        c.drawRect(-0.18f, -0.55f, 0.18f, -0.35f, p);
        c.drawLine(0.18f, -0.5f, 0.55f, -0.15f, p);
        c.drawCircle(-0.38f, -0.62f, 0.14f, p);
    }

    private static void map(Canvas c, Paint p) {
        P.reset();
        P.moveTo(-0.75f, -0.5f);
        P.lineTo(-0.25f, -0.65f);
        P.lineTo(0.25f, -0.5f);
        P.lineTo(0.75f, -0.65f);
        P.lineTo(0.75f, 0.5f);
        P.lineTo(0.25f, 0.65f);
        P.lineTo(-0.25f, 0.5f);
        P.lineTo(-0.75f, 0.65f);
        P.close();
        c.drawPath(P, p);
        c.drawLine(-0.25f, -0.65f, -0.25f, 0.5f, p);
        c.drawLine(0.25f, -0.5f, 0.25f, 0.65f, p);
    }

    private static void pause(Canvas c, Paint p) {
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(-0.45f, -0.55f, -0.12f, 0.55f, 0.06f, 0.06f, p);
        c.drawRoundRect(0.12f, -0.55f, 0.45f, 0.55f, 0.06f, 0.06f, p);
    }

    private static void run(Canvas c, Paint p) {
        for (float o : new float[]{-0.35f, 0.15f}) {
            P.reset();
            P.moveTo(o, -0.5f);
            P.lineTo(o + 0.4f, 0);
            P.lineTo(o, 0.5f);
            c.drawPath(P, p);
        }
        c.drawLine(-0.8f, 0, -0.5f, 0, p);
    }

    private static void carBody(Canvas c, Paint p) {
        // A rounded 1940s sedan.
        P.reset();
        P.moveTo(-0.85f, 0.25f);
        P.lineTo(-0.85f, 0.02f);
        P.cubicTo(-0.75f, -0.08f, -0.6f, -0.1f, -0.45f, -0.12f);
        P.cubicTo(-0.3f, -0.45f, 0.2f, -0.48f, 0.38f, -0.14f);
        P.cubicTo(0.6f, -0.12f, 0.8f, -0.05f, 0.85f, 0.1f);
        P.lineTo(0.85f, 0.25f);
        P.close();
        c.drawPath(P, p);
        c.drawCircle(-0.45f, 0.3f, 0.16f, p);
        c.drawCircle(0.45f, 0.3f, 0.16f, p);
    }

    private static void car(Canvas c, Paint p, boolean in) {
        c.save();
        c.translate(0, 0.18f);
        c.scale(0.85f, 0.85f);
        carBody(c, p);
        c.restore();
        float y = -0.62f;
        if (in) {
            c.drawLine(-0.45f, y, 0.25f, y, p);
            P.reset();
            P.moveTo(0.05f, y - 0.2f);
            P.lineTo(0.28f, y);
            P.lineTo(0.05f, y + 0.2f);
        } else {
            c.drawLine(-0.25f, y, 0.45f, y, p);
            P.reset();
            P.moveTo(-0.05f, y - 0.2f);
            P.lineTo(-0.28f, y);
            P.lineTo(-0.05f, y + 0.2f);
        }
        c.drawPath(P, p);
    }

    private static void pedal(Canvas c, Paint p, boolean gas) {
        if (gas) {
            R.set(-0.35f, -0.75f, 0.35f, 0.75f);
            c.drawRoundRect(R, 0.18f, 0.18f, p);
            for (float y = -0.45f; y <= 0.46f; y += 0.3f) c.drawLine(-0.15f, y, 0.15f, y, p);
        } else {
            R.set(-0.65f, -0.45f, 0.65f, 0.45f);
            c.drawRoundRect(R, 0.18f, 0.18f, p);
            for (float x = -0.35f; x <= 0.36f; x += 0.35f) c.drawLine(x, -0.2f, x, 0.2f, p);
        }
    }

    private static void handbrake(Canvas c, Paint p) {
        c.drawCircle(0, 0, 0.42f, p);
        R.set(-0.78f, -0.78f, 0.78f, 0.78f);
        c.drawArc(R, 120, 120, false, p);
        c.drawArc(R, -60, 120, false, p);
        p.setStrokeWidth(0.11f);
        c.drawLine(-0.1f, 0.22f, -0.1f, -0.22f, p);
        R.set(-0.1f, -0.22f, 0.18f, 0.04f);
        c.drawArc(R, -90, 180, false, p);
    }

    private static void horn(Canvas c, Paint p) {
        P.reset();
        P.moveTo(-0.7f, -0.15f);
        P.lineTo(-0.35f, -0.15f);
        P.lineTo(0.2f, -0.55f);
        P.lineTo(0.2f, 0.55f);
        P.lineTo(-0.35f, 0.15f);
        P.lineTo(-0.7f, 0.15f);
        P.close();
        c.drawPath(P, p);
        R.set(0.05f, -0.4f, 0.65f, 0.4f);
        c.drawArc(R, -50, 100, false, p);
        R.set(0.05f, -0.7f, 0.95f, 0.7f);
        c.drawArc(R, -45, 90, false, p);
    }

    private static void limiter(Canvas c, Paint p) {
        R.set(-0.7f, -0.6f, 0.7f, 0.8f);
        c.drawArc(R, 160, 220, false, p);
        c.drawLine(0, 0.1f, 0.38f, -0.25f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0, 0.1f, 0.1f, p);
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(-0.55f, 0.65f, 0.55f, 0.65f, p);
    }

    private static void ok(Canvas c, Paint p) {
        P.reset();
        P.moveTo(-0.55f, 0.02f);
        P.lineTo(-0.15f, 0.42f);
        P.lineTo(0.6f, -0.4f);
        c.drawPath(P, p);
    }

    private static void back(Canvas c, Paint p) {
        c.drawLine(-0.55f, 0, 0.6f, 0, p);
        P.reset();
        P.moveTo(-0.15f, -0.42f);
        P.lineTo(-0.58f, 0);
        P.lineTo(-0.15f, 0.42f);
        c.drawPath(P, p);
    }

    private static void gear(Canvas c, Paint p) {
        c.drawCircle(0, 0, 0.42f, p);
        c.drawCircle(0, 0, 0.14f, p);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            c.drawLine((float) Math.cos(a) * 0.48f, (float) Math.sin(a) * 0.48f, (float) Math.cos(a) * 0.72f, (float) Math.sin(a) * 0.72f, p);
        }
    }

    private static void look(Canvas c, Paint p) {
        R.set(-0.8f, -0.45f, 0.8f, 0.45f);
        P.reset();
        P.moveTo(-0.8f, 0);
        P.quadTo(0, -0.75f, 0.8f, 0);
        P.quadTo(0, 0.75f, -0.8f, 0);
        c.drawPath(P, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(0, 0, 0.2f, p);
        p.setStyle(Paint.Style.STROKE);
    }
}
