package com.wastelanddroid.input;

import java.util.List;

/**
 * One on-screen control. Same fields and string format as Fallout Droid 1.x
 * (DroidDeck screen keys), so saved layouts and the Pip-Boy editor keep working.
 *
 * shape: 0 rounded rect, 1 soft rect, 2 circle, 3 square, 4 wide pill, 5 d-pad (4 arms),
 * 6 analog-style stick (8 sectors, codes: up, up-right, right, down-right, down, down-left,
 * left, up-left; 0 on a diagonal = both neighbours), 7 oval, 8 diamond, 9 hexagon, 10 hidden.
 */
public final class ScreenKey {
    public final List<Integer> codes;
    public final float x, y;     // centre, fraction of the screen
    public final int size;       // dp
    public final String glyph, text, image;
    public final float opacity;
    public final int shape;
    public final boolean toggle;
    public final boolean labels;

    public ScreenKey(List<Integer> codes, float x, float y, int size, String glyph, String text,
                     String image, float opacity, int shape, boolean toggle, boolean labels) {
        this.codes = codes;
        this.x = x;
        this.y = y;
        this.size = size;
        this.glyph = glyph == null ? "" : glyph;
        this.text = text == null ? "" : text;
        this.image = image == null ? "" : image;
        this.opacity = opacity;
        this.shape = shape;
        this.toggle = toggle;
        this.labels = labels;
    }

    // Kotlin-style accessors (the 1.x editor code uses them)
    public List<Integer> getCodes() { return codes; }
    public float getX() { return x; }
    public float getY() { return y; }
    public int getSize() { return size; }
    public String getGlyph() { return glyph; }
    public String getText() { return text; }
    public String getImage() { return image; }
    public float getOpacity() { return opacity; }
    public int getShape() { return shape; }
    public boolean getToggle() { return toggle; }
    public boolean getLabels() { return labels; }

    /** Label shown on the key (text, glyph or the key's name). */
    public String shownLabel(android.content.Context c) {
        if (!text.isEmpty()) return text;
        if (!glyph.isEmpty()) return glyph;
        return KeyNames.name(codes.isEmpty() ? 0 : codes.get(0));
    }

    public ScreenKey with(List<Integer> nCodes, Integer nSize, String nText, Float nOpacity, Integer nShape,
                          Boolean nToggle, Boolean nLabels) {
        return new ScreenKey(nCodes != null ? nCodes : codes, x, y, nSize != null ? nSize : size, glyph,
                nText != null ? nText : text, image, nOpacity != null ? nOpacity : opacity,
                nShape != null ? nShape : shape, nToggle != null ? nToggle : toggle, nLabels != null ? nLabels : labels);
    }

    public ScreenKey at(float nx, float ny) {
        return new ScreenKey(codes, nx, ny, size, glyph, text, image, opacity, shape, toggle, labels);
    }
}
