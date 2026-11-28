package com.mafia2re.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * Button glyphs: game-icons.net shapes (CC BY 3.0, see assets/ICONS-CREDITS.txt) compiled
 * into VectorDrawables res/drawable/m2_ic_<name>.xml, tinted at draw time. Only used while
 * building cached button bitmaps, never per frame.
 */
final class Glyphs {
    private static final Map<String, Integer> IDS = new HashMap<>();

    private Glyphs() {
    }

    private static int id(Context c, String name) {
        Integer id = IDS.get(name);
        if (id == null) {
            id = c.getResources().getIdentifier("m2_ic_" + name, "drawable", c.getPackageName());
            IDS.put(name, id);
        }
        return id;
    }

    /** Draws glyph {@code name} centred at (cx, cy) inside a square of half-size {@code half}. */
    static void draw(Context ctx, Canvas c, String name, float cx, float cy, float half, int color) {
        int id = id(ctx, name);
        if (id == 0) return;
        Drawable d = ctx.getDrawable(id);
        if (d == null) return;
        d = d.mutate();
        d.setTint(color);
        d.setBounds(Math.round(cx - half), Math.round(cy - half), Math.round(cx + half), Math.round(cy + half));
        d.draw(c);
    }
}
