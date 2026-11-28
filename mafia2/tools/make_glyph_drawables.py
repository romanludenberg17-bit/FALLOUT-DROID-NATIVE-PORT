#!/usr/bin/env python3
"""game-icons.net SVGs -> Android VectorDrawables for the touch buttons.

m2_ic_crouch.xml and m2_ic_telegram.xml are drawn by hand (not generated here).

    make_glyph_drawables.py <game-icons repo> <res dir> <credits.txt>

Icons by Lorc, Delapouite, Skoll, Guard13007 (game-icons.net), CC BY 3.0.
Each SVG is 512x512 with a black background square and one white shape; the shape
becomes a white VectorDrawable that the overlay tints at draw time.
"""
import os
import re
import sys

GLYPHS = {
    "fire": "lorc/bullets",
    "aim": "delapouite/crosshair",
    "jump": "delapouite/jump-across",
    "action": "lorc/pointing",
    "reload": "delapouite/reload-gun-barrel",
    "punch": "lorc/fist",
    "cover": "delapouite/brick-wall",
    "weapon": "skoll/thompson-m1928",
    "grenade": "lorc/grenade",
    "map": "delapouite/position-marker",
    "pause": "guard13007/pause-button",
    "run": "lorc/run",
    "car_in": "delapouite/car-door",
    "car_out": "delapouite/exit-door",
    "gas": "delapouite/gas-pump",
    "brake": "delapouite/stop-sign",
    "handbrake": "delapouite/tire-tracks",
    "horn": "delapouite/megaphone",
    "limiter": "delapouite/speedometer",
    "ok": "delapouite/check-mark",
    "back": "delapouite/plain-arrow",
    "gear": "lorc/cog",
    "lock": "delapouite/plain-padlock",
    "unlock": "delapouite/padlock-open",
}
# Extra rotation (degrees) for icons whose natural direction does not fit.
ROTATE = {"back": 180}

AUTHORS = {"lorc": "Lorc", "delapouite": "Delapouite", "skoll": "Skoll", "guard13007": "Guard13007"}


def main():
    repo, res, credits = sys.argv[1:4]
    out = os.path.join(res, "drawable")
    os.makedirs(out, exist_ok=True)
    lines = ["Иконки кнопок: game-icons.net, лицензия CC BY 3.0 (https://creativecommons.org/licenses/by/3.0/)", ""]
    for name, src in sorted(GLYPHS.items()):
        svg = open(os.path.join(repo, src + ".svg")).read()
        paths = [d for d in re.findall(r'<path[^>]*\sd="([^"]+)"', svg) if d.strip() != "M0 0h512v512H0z"]
        if not paths:
            sys.exit("no shape in " + src)
        body = "".join('    <path android:fillColor="#FFFFFFFF" android:pathData="%s"/>\n' % d for d in paths)
        rot = ROTATE.get(name)
        if rot:
            body = ('  <group android:rotation="%d" android:pivotX="256" android:pivotY="256">\n%s  </group>\n' % (rot, body))
        xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
               '<!-- %s.svg from game-icons.net by %s, CC BY 3.0 -->\n'
               '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
               '    android:width="64dp" android:height="64dp" android:viewportWidth="512" android:viewportHeight="512">\n'
               '%s</vector>\n') % (src, AUTHORS[src.split("/")[0]], body)
        open(os.path.join(out, "m2_ic_%s.xml" % name), "w").write(xml)
        lines.append("%-10s %s.svg — %s" % (name, src, AUTHORS[src.split("/")[0]]))
    lines.append("crouch     иконка автора ReMaf2")
    lines.append("telegram   собственный рисунок")
    os.makedirs(os.path.dirname(credits), exist_ok=True)
    open(credits, "w").write("\n".join(lines) + "\n")
    print("%d drawables" % len(GLYPHS))


if __name__ == "__main__":
    main()
