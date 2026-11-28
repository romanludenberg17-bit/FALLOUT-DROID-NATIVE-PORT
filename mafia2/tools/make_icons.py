#!/usr/bin/env python3
"""Launcher icon (adaptive + legacy PNGs) from the ReMaf2 logo.

    make_icons.py <res_dir> <logo_full.png>

Background: black with a soft red glow. Foreground: the logo (red "II" over the
"ReMaf2" wordmark), kept inside the adaptive-icon safe zone (central 66%).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

S = 1024  # one adaptive layer (108dp)


def background():
    img = Image.new("RGBA", (S, S), (8, 6, 6, 255))
    glow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(glow).ellipse([S * 0.12, S * 0.18, S * 0.88, S * 0.94], fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.12))
    img.paste(Image.new("RGBA", (S, S), (120, 14, 18, 255)), (0, 0), glow)
    return img


def foreground(logo_path):
    fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    logo = Image.open(logo_path).convert("RGBA")
    box = int(S * 0.6)                                  # safe zone is 0.66
    k = min(box / logo.width, box / logo.height)
    lg = logo.resize((int(logo.width * k), int(logo.height * k)), Image.LANCZOS)
    x, y = (S - lg.width) // 2, (S - lg.height) // 2 + int(S * 0.01)
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shadow.paste(Image.new("RGBA", lg.size, (0, 0, 0, 190)), (x + 6, y + 10), lg)
    fg = Image.alpha_composite(fg, shadow.filter(ImageFilter.GaussianBlur(12)))
    fg.paste(lg, (x, y), lg)
    return fg


def main():
    res, logo = sys.argv[1], sys.argv[2]
    bg, fg = background(), foreground(logo)
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    full = Image.alpha_composite(bg, fg)
    crop = full.crop((S * 18 // 108, S * 18 // 108, S * 90 // 108, S * 90 // 108))
    for name, k in dens.items():
        d = os.path.join(res, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        layer = int(108 * k)
        bg.resize((layer, layer), Image.LANCZOS).save(os.path.join(d, "ic_launcher_background.png"))
        fg.resize((layer, layer), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"))
        size = int(48 * k)
        icon = crop.resize((size, size), Image.LANCZOS)
        for shape, fname in (("rr", "ic_launcher.png"), ("o", "ic_launcher_round.png")):
            mask = Image.new("L", (size * 4, size * 4), 0)
            md = ImageDraw.Draw(mask)
            if shape == "rr":
                md.rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 * 0.22, fill=255)
            else:
                md.ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
            out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            out.paste(icon, (0, 0), mask.resize((size, size), Image.LANCZOS))
            out.save(os.path.join(d, fname))
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
    preview = os.path.join(res, "..", "docs", "icon.png")
    os.makedirs(os.path.dirname(preview), exist_ok=True)
    crop.resize((512, 512), Image.LANCZOS).save(preview)


if __name__ == "__main__":
    main()
