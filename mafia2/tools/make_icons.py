#!/usr/bin/env python3
"""Draws the launcher icon (adaptive + legacy PNGs) for Mafia II Droid.

Original artwork: an art-deco cut-corner gold frame, a fedora over a serif "II",
sunburst rays on a dark wine background. Usage: make_icons.py <res_dir> <playfair.ttf>
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

S = 1024  # work size of one adaptive layer (108dp)
GOLD_HI, GOLD_LO = (246, 214, 140), (170, 112, 34)


def vgrad(size, top, bottom):
    w, h = size
    g = Image.new("RGBA", size)
    d = ImageDraw.Draw(g)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    return g


def gold_fill(mask):
    g = vgrad(mask.size, GOLD_HI, GOLD_LO)
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    out.paste(g, (0, 0), mask)
    return out


def background():
    img = vgrad((S, S), (58, 14, 18), (14, 6, 6))
    rays = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(rays)
    cx, cy = S / 2, S * 0.62
    for i in range(24):
        a0 = math.radians(i * 15)
        a1 = math.radians(i * 15 + 6)
        d.polygon([(cx, cy), (cx + math.cos(a0) * S, cy + math.sin(a0) * S), (cx + math.cos(a1) * S, cy + math.sin(a1) * S)], fill=34)
    glow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(glow).ellipse([S * 0.18, S * 0.3, S * 0.82, S * 0.94], fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.08))
    warm = Image.new("RGBA", (S, S), (224, 166, 62, 255))
    img.paste(warm, (0, 0), rays)
    img.paste(Image.new("RGBA", (S, S), (120, 40, 30, 255)), (0, 0), glow)
    vig = Image.new("L", (S, S), 0)
    ImageDraw.Draw(vig).ellipse([-S * 0.2, -S * 0.2, S * 1.2, S * 1.2], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(S * 0.1))
    dark = Image.new("RGBA", (S, S), (8, 4, 4, 255))
    img = Image.composite(img, dark, vig)
    return img


def cut_rect(d, box, cut, **kw):
    l, t, r, b = box
    d.polygon([(l + cut, t), (r - cut, t), (r, t + cut), (r, b - cut), (r - cut, b), (l + cut, b), (l, b - cut), (l, t + cut)], **kw)


def foreground(font_path):
    fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    # Safe zone is the central 66%: keep everything inside ~[176, 848].
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    box = (232, 250, 792, 810)
    cut_rect(d, box, 70, fill=255)
    cut_rect(d, (box[0] + 22, box[1] + 22, box[2] - 22, box[3] - 22), 58, fill=0)
    cut_rect(d, (box[0] + 40, box[1] + 40, box[2] - 40, box[3] - 40), 48, fill=255)
    cut_rect(d, (box[0] + 50, box[1] + 50, box[2] - 50, box[3] - 50), 42, fill=0)
    # Inner dark plate.
    plate = Image.new("L", (S, S), 0)
    cut_rect(ImageDraw.Draw(plate), (box[0] + 50, box[1] + 50, box[2] - 50, box[3] - 50), 42, fill=255)
    fg.paste(Image.new("RGBA", (S, S), (22, 14, 10, 235)), (0, 0), plate)
    # Numeral.
    font = ImageFont.truetype(font_path, 330)
    try:
        font.set_variation_by_axes([900])
    except Exception:
        pass
    tm = Image.new("L", (S, S), 0)
    td = ImageDraw.Draw(tm)
    td.text((S / 2, 662), "II", font=font, fill=255, anchor="ms")
    # Fedora above the numeral, in gold.
    hat = Image.new("L", (S, S), 0)
    hd = ImageDraw.Draw(hat)
    hd.ellipse([344, 334, 680, 386], fill=255)                      # brim
    hd.polygon([(426, 362), (434, 296), (468, 272), (512, 288), (556, 272), (590, 296), (598, 362)], fill=255)  # crown, pinched top
    hd.rectangle([428, 336, 596, 348], fill=0)                      # hat band
    # Deco rays under the numeral.
    rd = ImageDraw.Draw(m)
    for i in range(-2, 3):
        rd.line([(512 + i * 44, 690), (512 + i * 30, 734)], fill=255, width=12)
    for layer in (m, tm, hat):
        g = gold_fill(layer)
        shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        shadow.paste(Image.new("RGBA", (S, S), (0, 0, 0, 150)), (8, 12), layer)
        fg = Image.alpha_composite(fg, shadow.filter(ImageFilter.GaussianBlur(10)))
        fg = Image.alpha_composite(fg, g)
    return fg


def main():
    res, font = sys.argv[1], sys.argv[2]
    bg, fg = background(), foreground(font)
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in dens.items():
        d = os.path.join(res, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        layer = int(108 * k)
        bg.resize((layer, layer), Image.LANCZOS).save(os.path.join(d, "ic_launcher_background.png"))
        fg.resize((layer, layer), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"))
        # Legacy icon: 48dp, rounded square, the central 72/108 of the composed layers.
        full = Image.alpha_composite(bg, fg)
        crop = full.crop((S * 18 // 108, S * 18 // 108, S * 90 // 108, S * 90 // 108))
        size = int(48 * k)
        icon = crop.resize((size, size), Image.LANCZOS)
        mask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 * 0.22, fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(icon, (0, 0), mask.resize((size, size), Image.LANCZOS))
        out.save(os.path.join(d, "ic_launcher.png"))
        rmask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(rmask).ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
        rout = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rout.paste(icon, (0, 0), rmask.resize((size, size), Image.LANCZOS))
        rout.save(os.path.join(d, "ic_launcher_round.png"))
    d = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(d, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@mipmap/ic_launcher_background"/>\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
           '    <monochrome android:drawable="@mipmap/ic_launcher_foreground"/>\n'
           '</adaptive-icon>\n')
    for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(d, n), "w").write(xml)
    # 512 px store-style preview for the README.
    full = Image.alpha_composite(bg, fg).crop((S * 18 // 108, S * 18 // 108, S * 90 // 108, S * 90 // 108)).resize((512, 512), Image.LANCZOS)
    full.save(os.path.join(res, "..", "icon_preview.png"))


if __name__ == "__main__":
    main()
