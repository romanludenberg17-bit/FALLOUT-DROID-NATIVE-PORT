package com.wastelanddroid.input;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persistence of on-screen controls in the Fallout Droid 1.x string format:
 * {@code codes:x,y,size:glyph:text:image:opacity:shape:toggle:labels}, keys joined by ';',
 * codes joined by '+'. Replaces DroidDeck's ControllerPrefs for the native build.
 */
public final class ScreenKeyStore {
    private static final String PREFS = "screen_keys";
    private static final String KEY = "screenKeys";
    private static final String SHOWN = "screenKeysShown";
    private static final String TINT = "tint";

    private ScreenKeyStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<ScreenKey> load(Context c) {
        return decode(prefs(c).getString(KEY, ""));
    }

    public static void save(Context c, List<ScreenKey> keys) {
        prefs(c).edit().putString(KEY, encode(keys)).apply();
    }

    public static boolean shown(Context c) {
        return prefs(c).getBoolean(SHOWN, true);
    }

    public static void setShown(Context c, boolean shown) {
        prefs(c).edit().putBoolean(SHOWN, shown).apply();
    }

    public static int tint(Context c, int def) {
        return prefs(c).getInt(TINT, def);
    }

    public static void setTint(Context c, int tint) {
        prefs(c).edit().putInt(TINT, tint).apply();
    }

    public static String encode(List<ScreenKey> keys) {
        StringBuilder sb = new StringBuilder();
        for (ScreenKey k : keys) {
            if (sb.length() > 0) sb.append(';');
            for (int i = 0; i < k.codes.size(); i++) {
                if (i > 0) sb.append('+');
                sb.append(k.codes.get(i));
            }
            sb.append(':').append(k.x).append(',').append(k.y).append(',').append(k.size)
              .append(':').append(escape(k.glyph))
              .append(':').append(escape(k.text))
              .append(':').append(escape(k.image))
              .append(':').append(k.opacity)
              .append(':').append(k.shape)
              .append(':').append(k.toggle ? 1 : 0)
              .append(':').append(k.labels ? 1 : 0);
        }
        return sb.toString();
    }

    public static List<ScreenKey> decode(String raw) {
        List<ScreenKey> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String item : raw.split(";")) {
            String[] f = item.split(":", -1);
            if (f.length < 2) continue;
            List<Integer> codes = new ArrayList<>();
            for (String c : f[0].split("\\+")) {
                try { codes.add(Integer.parseInt(c.trim())); } catch (NumberFormatException ignored) {}
            }
            if (codes.isEmpty()) continue;
            String[] pos = f[1].split(",");
            if (pos.length != 3) continue;
            float x, y;
            int size;
            try {
                x = Float.parseFloat(pos[0]);
                y = Float.parseFloat(pos[1]);
                size = Math.round(Float.parseFloat(pos[2]));
            } catch (NumberFormatException e) {
                continue;
            }
            String glyph = f.length > 2 ? unescape(f[2]) : "";
            String text = f.length > 3 ? unescape(f[3]) : "";
            String image = f.length > 4 ? unescape(f[4]) : "";
            float opacity = f.length > 5 ? parseF(f[5], 1f) : 1f;
            int shape = f.length > 6 ? parseI(f[6], 0) : 0;
            boolean toggle = f.length > 7 && "1".equals(f[7]);
            boolean labels = f.length <= 8 || "1".equals(f[8]);
            out.add(new ScreenKey(codes, x, y, size, glyph, text, image, opacity, shape, toggle, labels));
        }
        return out;
    }

    private static float parseF(String s, float d) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return d; }
    }

    private static int parseI(String s, int d) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return d; }
    }

    // Same escaping as Fallout Droid 1.x: '%' ';' ':' ',' are percent-encoded.
    static String escape(String v) {
        return v.replace("%", "%25").replace(";", "%3B").replace(":", "%3A").replace(",", "%2C");
    }

    static String unescape(String v) {
        return v.replace("%2C", ",").replace("%3A", ":").replace("%3B", ";").replace("%25", "%");
    }

    /** Debug helper. */
    public static String describe(List<ScreenKey> keys) {
        return String.format(Locale.ROOT, "%d keys", keys.size());
    }
}
