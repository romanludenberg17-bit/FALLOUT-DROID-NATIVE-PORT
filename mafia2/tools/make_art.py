#!/usr/bin/env python3
"""Launcher backgrounds from the Mafia II art the ReMaf2 author supplied.

    make_art.py <keyart.jpg> <police.jpg> <snow.jpg> <out_dir>

The key art keeps its full height (the launcher anchors it to the top, so heads are never
cut); its "MAFIA II" wordmark in the top-right corner is dissolved into the
night (blurred and darkened), so it does not fight with the ReMaf2 logo.
"""
import sys

from PIL import Image, ImageFilter


def main():
    key, police, snow, out = sys.argv[1:5]
    k = Image.open(key).convert("RGB")
    w, h = k.size
    # Wordmark box (1408x880 art): x 1072..1400, y 30..~112. No clean stretch of sky of that
    # size exists in the picture (heads, hats, gun barrels), so the corner is dissolved into
    # the night instead: heavy blur, no colour, darkened, with soft edges.
    x0, y1 = int(w * 0.74), int(h * 0.21)
    region = k.crop((x0, 0, w, y1))
    fog = region.filter(ImageFilter.GaussianBlur(w * 0.02)).convert("L").point(lambda v: int(v * 0.45)).convert("RGB")
    mask = Image.new("L", region.size, 0)
    mx, my = int(w * 0.02), int(h * 0.03)
    mask.paste(Image.new("L", (region.size[0] - mx, region.size[1] - my), 255), (mx, 0))
    mask = mask.filter(ImageFilter.GaussianBlur(mx / 2))
    k.paste(fog, (x0, 0), mask)
    k.save(out + "/keyart.jpg", quality=84, optimize=True, progressive=True)
    for s, n in ((police, "street_police.jpg"), (snow, "street_snow.jpg")):
        Image.open(s).convert("RGB").save(out + "/" + n, quality=82, optimize=True, progressive=True)


if __name__ == "__main__":
    main()
