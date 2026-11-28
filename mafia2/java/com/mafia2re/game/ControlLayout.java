package com.mafia2re.game;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The touch layout: every button with its action, the contexts it shows in, its default place
 * and the user's changes (position, size, opacity, hidden). Key codes are Android key codes,
 * which SDL turns into the PC keys the recompiled game reads.
 */
final class ControlLayout {
    static final int FOOT = 1, CAR = 2, MENU = 4;
    static final String PREFS = "m2layout";

    static final int K_ESC = 111, K_ENTER = 66, K_M = 41, K_WEAPON = 152, K_1 = 8, K_C = 31, K_SHIFT = 59,
            K_E = 33, K_SPACE = 62, K_F = 34, K_CTRL = 113, K_R = 46, K_Q = 45, K_L = 40, K_W = 51, K_A = 29,
            K_S = 47, K_D = 32;

    enum Kind {KEY, MOUSE_LEFT, MOUSE_RIGHT, RUN, PAUSE, GEAR, STICK}

    static final class Element {
        final String id, ru, en, glyph;
        final Kind kind;
        final int key, ctx;
        /** Default centre in dp, measured from the left/right and top/bottom edges. */
        final boolean fromRight, fromBottom;
        final float dx, dy, radiusDp;

        // User state (fractions of the screen; -1 = default place).
        float fx = -1, fy = -1, scale = 1f, alpha = 1f;
        boolean hidden;

        /** Second action on a long press (key code), its badge glyph, and the default visibility. */
        int holdKey;
        String holdGlyph;
        boolean hiddenByDefault;

        // Runtime for long presses.
        long holdStart;
        boolean holdFired;

        // Runtime.
        float cx, cy, r;
        int pointer = -1;
        boolean latched;

        Element(String id, String ru, String en, String glyph, Kind kind, int key, int ctx,
                boolean fromRight, float dx, boolean fromBottom, float dy, float radiusDp) {
            this.id = id;
            this.ru = ru;
            this.en = en;
            this.glyph = glyph;
            this.kind = kind;
            this.key = key;
            this.ctx = ctx;
            this.fromRight = fromRight;
            this.dx = dx;
            this.fromBottom = fromBottom;
            this.dy = dy;
            this.radiusDp = radiusDp;
        }

        String label() {
            return Ui.t(ru, en);
        }

        boolean pressed() {
            return pointer >= 0 || latched;
        }

        boolean hit(float x, float y, float slop) {
            float ddx = x - cx, ddy = y - cy, rr = r + slop;
            return ddx * ddx + ddy * ddy <= rr * rr;
        }

        void layout(int w, int h, float density, float uiScale) {
            r = radiusDp * density * uiScale * scale;
            if (fx >= 0) {
                cx = fx * w;
                cy = fy * h;
            } else {
                cx = fromRight ? w - dx * density * uiScale : dx * density * uiScale;
                cy = fromBottom ? h - dy * density * uiScale : dy * density * uiScale;
            }
        }
    }

    final List<Element> all = new ArrayList<>();
    final Element stick;

    private Element add(String id, String ru, String en, String glyph, Kind kind, int key, int ctx,
                        boolean fromRight, float dx, boolean fromBottom, float dy, float radius) {
        Element e = new Element(id, ru, en, glyph, kind, key, ctx, fromRight, dx, fromBottom, dy, radius);
        all.add(e);
        return e;
    }

    ControlLayout() {
        final boolean R = true, L = false, B = true, T = false;
        // Shared.
        stick = add("stick", "Стик", "Stick", "", Kind.STICK, 0, FOOT | CAR, L, 128, B, 116, 52);
        add("pause", "Пауза", "Pause", "pause", Kind.PAUSE, K_ESC, FOOT | CAR, L, 44, T, 42, 22);
        add("map", "Карта", "Map", "map", Kind.KEY, K_M, FOOT | CAR, L, 102, T, 42, 22);
        add("gear", "Меню порта", "Port menu", "gear", Kind.GEAR, 0, FOOT | CAR | MENU, R, 42, T, 42, 21);
        // On foot: a fan of actions around a big Fire button.
        add("fire", "Огонь", "Fire", "fire", Kind.MOUSE_LEFT, 0, FOOT, R, 96, B, 100, 46);
        add("aim", "Прицел", "Aim", "aim", Kind.MOUSE_RIGHT, 0, FOOT, R, 204, B, 56, 31);
        add("jump", "Прыжок", "Jump", "jump", Kind.KEY, K_SPACE, FOOT, R, 56, B, 206, 28);
        add("crouch", "Присесть", "Crouch", "crouch", Kind.KEY, K_C, FOOT, R, 150, B, 198, 25);
        add("reload", "Перезарядка", "Reload", "reload", Kind.KEY, K_R, FOOT, R, 236, B, 150, 25);
        add("action", "Действие", "Action", "action", Kind.KEY, K_E, FOOT, R, 300, B, 64, 28);
        add("cover", "Укрытие", "Cover", "cover", Kind.KEY, K_CTRL, FOOT, R, 316, B, 170, 24);
        add("punch", "Удар", "Punch", "punch", Kind.KEY, K_Q, FOOT, R, 232, B, 262, 22);
        add("weapon", "Оружие", "Weapon", "weapon", Kind.KEY, K_WEAPON, FOOT, R, 136, B, 286, 24);
        add("grenade", "Граната", "Grenade", "grenade", Kind.KEY, K_1, FOOT, R, 330, B, 250, 22);
        add("car_in", "Сесть в машину", "Enter car", "car_in", Kind.KEY, K_F, FOOT, R, 392, B, 70, 24);
        add("run", "Бег", "Sprint", "run", Kind.RUN, K_SHIFT, FOOT, L, 236, B, 212, 24);
        // In the car: pedals under the right thumb.
        add("gas", "Газ", "Gas", "gas", Kind.KEY, K_W, CAR, R, 78, B, 124, 46);
        add("brake", "Тормоз", "Brake", "brake", Kind.KEY, K_S, CAR, R, 192, B, 82, 38);
        add("handbrake", "Ручник", "Handbrake", "handbrake", Kind.KEY, K_SPACE, CAR, R, 72, B, 252, 30);
        add("horn", "Сигнал", "Horn", "horn", Kind.KEY, K_E, CAR, R, 196, B, 214, 26);
        add("car_out", "Выйти", "Exit car", "car_out", Kind.KEY, K_F, CAR, R, 304, B, 108, 27);
        add("limiter", "Ограничитель", "Speed limiter", "limiter", Kind.KEY, K_L, CAR, R, 306, B, 222, 24);
        // Menus.
        add("ok", "Выбрать", "Select", "ok", Kind.KEY, K_ENTER, MENU, R, 84, B, 92, 34);
        add("back", "Назад", "Back", "back", Kind.KEY, K_ESC, MENU, R, 186, B, 72, 28);
        smartPairs();
    }

    /**
     * Smart pairs: the rarely needed key rides on a frequent button as a long press, and its own
     * button is hidden by default (can be shown again in the editor).
     *   Действие: tap E, hold F (enter / leave a car).   Оружие: tap next weapon, hold grenade.
     */
    private void smartPairs() {
        Element action = byId("action"), weapon = byId("weapon");
        action.holdKey = K_F;
        action.holdGlyph = "car_in";
        weapon.holdKey = K_1;
        weapon.holdGlyph = "grenade";
        byId("car_in").hiddenByDefault = true;
        byId("grenade").hiddenByDefault = true;
    }

    Element byId(String id) {
        for (Element e : all) if (e.id.equals(id)) return e;
        return null;
    }

    static ControlLayout load(Context c) {
        ControlLayout l = new ControlLayout();
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getInt("layout_v", 1) < 2) {
            // 1.3.3: car / grenade buttons became long presses; earlier saved layouts get the new default.
            p.edit().remove("car_in.h").remove("grenade.h").putInt("layout_v", 2).apply();
        }
        for (Element e : l.all) {
            e.fx = p.getFloat(e.id + ".x", -1);
            e.fy = p.getFloat(e.id + ".y", -1);
            e.scale = p.getFloat(e.id + ".s", 1f);
            e.alpha = p.getFloat(e.id + ".a", 1f);
            e.hidden = p.getBoolean(e.id + ".h", e.hiddenByDefault);
        }
        return l;
    }

    void save(Context c) {
        SharedPreferences.Editor ed = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (Element e : all) {
            ed.putFloat(e.id + ".x", e.fx).putFloat(e.id + ".y", e.fy).putFloat(e.id + ".s", e.scale)
                    .putFloat(e.id + ".a", e.alpha).putBoolean(e.id + ".h", e.hidden);
        }
        ed.apply();
    }

    static void reset(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** Snapshot for undo in the editor. */
    float[] snapshot() {
        float[] s = new float[all.size() * 5];
        for (int i = 0; i < all.size(); i++) {
            Element e = all.get(i);
            s[i * 5] = e.fx;
            s[i * 5 + 1] = e.fy;
            s[i * 5 + 2] = e.scale;
            s[i * 5 + 3] = e.alpha;
            s[i * 5 + 4] = e.hidden ? 1 : 0;
        }
        return s;
    }

    void restore(float[] s) {
        for (int i = 0; i < all.size() && i * 5 + 4 < s.length; i++) {
            Element e = all.get(i);
            e.fx = s[i * 5];
            e.fy = s[i * 5 + 1];
            e.scale = s[i * 5 + 2];
            e.alpha = s[i * 5 + 3];
            e.hidden = s[i * 5 + 4] != 0;
        }
    }
}
