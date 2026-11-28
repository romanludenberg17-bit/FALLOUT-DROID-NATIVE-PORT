#!/usr/bin/env python3
"""ReMaf2 logo (white wordmark + red "II" on black) -> transparent PNGs.

    make_logo_assets.py <logo.jpg> <out_dir>

Black becomes transparent (alpha = brightest channel, colours un-premultiplied), so the
man's silhouette between the two "I" stays see-through, as in the logo itself.
"""
import sys

from PIL import Image


def main():
    src, out = sys.argv[1], sys.argv[2]
    lg = Image.open(src).convert("RGB")
    W, H = lg.size
    px = lg.load()
    res = Image.new("RGBA", (W, H))
    op = res.load()
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            a = max(r, g, b)
            if a < 18:
                op[x, y] = (0, 0, 0, 0)
                continue
            op[x, y] = (min(255, r * 255 // a), min(255, g * 255 // a), min(255, b * 255 // a), a)
    bb = res.getbbox()
    full = res.crop(bb)
    full.thumbnail((1100, 1100))
    full.save(out + "/logo_full.png", optimize=True)
    alpha = res.split()[3]
    gap = next(y for y in range(bb[1], bb[3]) if not alpha.crop((bb[0], y, bb[2], y + 1)).getbbox())
    ii = res.crop((bb[0], bb[1], bb[2], gap))
    ii = ii.crop(ii.getbbox())
    ii.thumbnail((600, 600))
    ii.save(out + "/logo_ii.png", optimize=True)


if __name__ == "__main__":
    main()
